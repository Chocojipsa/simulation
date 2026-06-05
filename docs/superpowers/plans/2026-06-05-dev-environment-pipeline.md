# Dev Staging Environment & Pipeline Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a developer staging environment (`dev-api`) on Lightsail B (port 8082) and integrate it into the Jenkinsfile pipeline as a pre-production gating stage.

**Architecture:** A new `dev-api` service is added to the Lightsail B compose file. Jenkins deploys to this staging environment first, performs health check verification at port 8082, and only triggers production deployment (Local A and Remote B) if the staging health check succeeds.

**Tech Stack:** Jenkins, Docker Compose, Groovy (Jenkins Pipeline DSL), Bash, curl.

---

### Task 1: Add `dev-api` to Lightsail B Compose Configuration

**Files:**
- Modify: `infra/prod/lightsail-b.compose.yml`

- [ ] **Step 1: Modify lightsail-b.compose.yml**

Add the `dev-api` service block to `infra/prod/lightsail-b.compose.yml` so that it binds host port `8082` to container port `8080`.

Update `infra/prod/lightsail-b.compose.yml` to match:
```yaml
services:
  api-b:
    image: chocojipsa/timedeal-backend:${BACKEND_VERSION:-latest}
    env_file:
      - .env
    environment:
      APP_INSTANCE_ID: api-b
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"
    restart: unless-stopped

  dev-api:
    image: chocojipsa/timedeal-backend:${BACKEND_VERSION:-latest}
    env_file:
      - .env
    environment:
      APP_INSTANCE_ID: dev-api
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8082:8080"
    restart: unless-stopped

  worker:
    image: chocojipsa/timedeal-backend:${BACKEND_VERSION:-latest}
    env_file:
      - .env
    environment:
      APP_INSTANCE_ID: worker
      SPRING_PROFILES_ACTIVE: prod,worker
    restart: unless-stopped

  traffic-generator:
    image: chocojipsa/timedeal-backend:${BACKEND_VERSION:-latest}
    env_file:
      - .env
    environment:
      APP_INSTANCE_ID: traffic-generator
      SPRING_PROFILES_ACTIVE: prod,generator
    ports:
      - "8081:8080"
    restart: unless-stopped
```

- [ ] **Step 2: Commit changes**

```bash
git add infra/prod/lightsail-b.compose.yml
git commit -m "infra: add dev-api staging container to lightsail-b compose file"
```

---

### Task 2: Integrate Dev Staging Stage into Jenkinsfile

**Files:**
- Modify: `Jenkinsfile`

- [ ] **Step 1: Modify Jenkinsfile**

Update `Jenkinsfile` to add the `Deploy to Dev (Remote)` stage and adjust the production remote deployment stage to exclude restarting `dev-api`.

Replace lines 84 to 134 in `Jenkinsfile` with the following:
```groovy
        stage('Deploy to Dev (Remote)') {
            steps {
                script {
                    echo "Deploying to Developer Staging (Remote B Port 8082)..."
                    sshagent(credentials: [SSH_CREDENTIALS_ID]) {
                        sh """
                            ssh -o StrictHostKeyChecking=no ubuntu@${LIGHTSAIL_B_IP} '
                                cd ~/simulation && \
                                git fetch --all && \
                                git reset --hard origin/\$(git rev-parse --abbrev-ref HEAD) && \
                                cd infra/prod && \
                                BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-b.compose.yml pull dev-api && \
                                BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-b.compose.yml up -d --no-deps dev-api
                            '
                        """
                    }

                    echo "Checking health on Dev api..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://${LIGHTSAIL_B_IP}:8082/health", returnStdout: true).trim()
                                    echo "Health check response: ${response}"
                                    return (response == "200")
                                } catch (Exception e) {
                                    return false
                                }
                            }
                        }
                    }
                    echo "Developer Staging deployment verified."
                }
            }
        }

        stage('Deploy to Lightsail A (Local)') {
            steps {
                script {
                    echo "Deploying to Lightsail A (Local)..."
                    withCredentials([file(credentialsId: 'prod-env-file', variable: 'PROD_ENV')]) {
                        dir('infra/prod') {
                            // Securely stage the template config to avoid truncating nginx-api.conf on failure
                            sh "git show HEAD:infra/prod/nginx-api.conf > nginx-api.conf.tmp"
                            sh "sed 's/LIGHTSAIL_B_PRIVATE_IP/${LIGHTSAIL_B_IP}/g' nginx-api.conf.tmp > nginx-api.conf"
                            sh "rm -f nginx-api.conf.tmp"

                            // Copy the secret env file to the local directory
                            sh "cat \$PROD_ENV > .env"

                            sh "BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-a.compose.yml pull api-a"
                            sh "BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-a.compose.yml up -d --no-deps api-a"
                            // Dynamic Nginx configuration reload to apply active failover settings without downtime
                            sh "docker compose -f lightsail-a.compose.yml exec -T nginx nginx -s reload || docker compose -f lightsail-a.compose.yml restart nginx"
                        }
                    }
                    
                    echo "Checking health on Local api-a..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
                                    // Query api-a directly to ensure the newly deployed instance itself is healthy
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://api-a:8080/health", returnStdout: true).trim()
                                    echo "Health check response: ${response}"
                                    return (response == "200")
                                } catch (Exception e) {
                                    return false
                                }
                            }
                        }
                    }
                    echo "Lightsail A deployment verified."
                }
            }
        }

        stage('Deploy to Lightsail B (Remote)') {
            steps {
                script {
                    echo "Deploying to Lightsail B (Remote)..."
                    sshagent(credentials: [SSH_CREDENTIALS_ID]) {
                        sh """
                            ssh -o StrictHostKeyChecking=no ubuntu@${LIGHTSAIL_B_IP} '
                                cd ~/simulation && \
                                git fetch --all && \
                                git reset --hard origin/\$(git rev-parse --abbrev-ref HEAD) && \
                                cd infra/prod && \
                                BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-b.compose.yml pull api-b worker traffic-generator && \
                                BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-b.compose.yml up -d --no-deps api-b worker traffic-generator
                            '
                        """
                    }

                    echo "Checking health on Remote api-b..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://${LIGHTSAIL_B_IP}:8080/health", returnStdout: true).trim()
                                    echo "Health check response: ${response}"
                                    return (response == "200")
                                } catch (Exception e) {
                                    return false
                                }
                            }
                        }
                    }
                    echo "Lightsail B deployment verified."
                }
            }
        }
```

- [ ] **Step 2: Commit changes**

```bash
git add Jenkinsfile
git commit -m "ci: insert dev staging remote deploy stage before production deployment stages"
```
