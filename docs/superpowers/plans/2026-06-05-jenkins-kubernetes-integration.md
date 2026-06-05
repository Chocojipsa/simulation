# Jenkins CI/CD & Docker Compose Rolling Deployment Implementation Plan

> [!NOTE]
> **Architecture Decision Record (ADR) - 2026-06-05**
> * **Status:** Deferred (Kubernetes Migration Phase)
> * **Decision:** Retain the optimized Docker Compose + Nginx Active Failover architecture and defer the Kubernetes (K3s) migration.
> * **Rationale:** The AWS Lightsail instances A and B are limited to 2GB of physical RAM. Running the K3s control plane (master process, kubelet, flannel, etc.) alongside the Jenkins automation server, Spring Boot API, and Nginx proxy would lead to extreme memory resource contention, causing heavy disk swap usage and potential Out-Of-Memory (OOM) daemon terminations. Retaining Docker Compose ensures stable, low-overhead resource utilization while achieving identical zero-downtime rolling deployment guarantees.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure SWAP, install Docker-based Jenkins on Lightsail A, configure Nginx Upstream failover, and set up a declarative Jenkinsfile pipeline to build and perform zero-downtime rolling deploys to Lightsail A and B.

**Architecture:** Use SWAP space and JVM limitations to optimize memory. Jenkins runs with local Docker socket access (Docker-out-of-Docker). Gradle builds inside Jenkins with cached dependencies, producing a JAR that is packaged into a minimal production Docker image and pushed to Docker Hub. Sequential deployment starts with local (Lightsail A) first, verifies health, then updates remote (Lightsail B) via SSH agent VPC private networking.

**Tech Stack:** Jenkins (LTS JDK17), Docker/Docker Compose, Bash, Nginx, Spring Boot (Java 17/Gradle).

---

## Proposed File Changes
- **Create**: `infra/prod/setup-swap.sh` - Automates 2GB swap partition creation.
- **Create**: `infra/prod/Dockerfile.jenkins` - Custom Jenkins container with Docker CLI.
- **Create**: `backend/Dockerfile.production` - Minimal runtime image copying pre-built JAR.
- **Create**: `Jenkinsfile` - Main declarative CI/CD pipeline definition.
- **Modify**: `infra/prod/lightsail-a.compose.yml` - Integrates Jenkins service with resource bounds.
- **Modify**: `infra/prod/nginx-api.conf` - Upstream active failover config.

---

### Task 1: SWAP Space Setup Script

**Files:**
- Create: `infra/prod/setup-swap.sh`

- [ ] **Step 1: Write swap configuration script**

Create `infra/prod/setup-swap.sh` with the following content:
```bash
#!/bin/bash
set -e

echo "Starting SWAP space configuration..."

if [ -f /swapfile ]; then
    echo "Swapfile already exists. Skipping creation."
else
    echo "Creating 2GB swap file..."
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    
    # Append to /etc/fstab if not present
    if ! grep -q "/swapfile" /etc/fstab; then
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    fi
    echo "Swapfile created and activated successfully."
fi

echo "Current memory status:"
free -h
```

- [ ] **Step 2: Commit setup-swap script**

```bash
git add infra/prod/setup-swap.sh
git commit -m "feat: add automated swap setup script for Lightsail instance"
```

---

### Task 2: Configure Nginx Upstream Active Failover

**Files:**
- Modify: `infra/prod/nginx-api.conf`

- [ ] **Step 1: Apply max_fails, fail_timeout, and proxy_next_upstream**

Modify `infra/prod/nginx-api.conf` to configure failover boundaries in the upstream and proxy retry behavior in location blocks:
```nginx
events {}

http {
  upstream api_servers {
    server api-a:8080 max_fails=1 fail_timeout=10s;
    server LIGHTSAIL_B_PRIVATE_IP:8080 max_fails=1 fail_timeout=10s;
  }

  server {
    listen 8080;

    location /api/ {
      if ($request_method = OPTIONS) {
        add_header Access-Control-Allow-Origin "*" always;
        add_header Access-Control-Allow-Methods "GET, POST, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Content-Type" always;
        add_header Access-Control-Max-Age 3600 always;
        return 204;
      }

      add_header Access-Control-Allow-Origin "*" always;
      add_header Access-Control-Allow-Methods "GET, POST, OPTIONS" always;
      add_header Access-Control-Allow-Headers "Content-Type" always;
      proxy_pass http://api_servers;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_next_upstream error timeout http_502 http_503 http_504;
    }

    location ~ ^/api/simulations/.*/events$ {
      add_header Access-Control-Allow-Origin "*" always;
      proxy_pass http://api_servers;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header Connection "";
      proxy_buffering off;
      proxy_cache off;
      proxy_next_upstream error timeout http_502 http_503 http_504;
    }

    location = /health {
      proxy_pass http://api_servers;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_next_upstream error timeout http_502 http_503 http_504;
    }

    location / {
      default_type text/plain;
      return 404 "API server only. Use the Vercel frontend.\n";
    }
  }
}
```

- [ ] **Step 2: Commit Nginx config**

```bash
git add infra/prod/nginx-api.conf
git commit -m "infra: configure active failover and proxy retries in nginx"
```

---

### Task 3: Create Custom Jenkins Dockerfile and Add to Compose

**Files:**
- Create: `infra/prod/Dockerfile.jenkins`
- Modify: `infra/prod/lightsail-a.compose.yml`

- [ ] **Step 1: Write Dockerfile.jenkins**

Create `infra/prod/Dockerfile.jenkins` to construct the Jenkins image with Docker CLI preinstalled:
```dockerfile
FROM jenkins/jenkins:lts-jdk17
USER root
RUN apt-get update && apt-get install -y lsb-release curl
RUN curl -fsSLo /usr/share/keyrings/docker-archive-keyring.asc \
  https://download.docker.com/linux/debian/gpg
RUN echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.asc] \
  https://download.docker.com/linux/debian $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
RUN apt-get update && apt-get install -y docker-ce-cli
# Retain root user to allow writing to the mounted docker socket
USER root
```

- [ ] **Step 2: Update lightsail-a.compose.yml**

Update `infra/prod/lightsail-a.compose.yml` to define the `jenkins` container service:
```yaml
services:
  api-a:
    build:
      context: ../../backend
    env_file:
      - .env
    environment:
      APP_INSTANCE_ID: api-a
      SPRING_PROFILES_ACTIVE: prod
    expose:
      - "8080"
    restart: unless-stopped

  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./nginx-api.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:8080"
    depends_on:
      - api-a
    restart: unless-stopped

  jenkins:
    build:
      context: .
      dockerfile: Dockerfile.jenkins
    user: root
    ports:
      - "9090:8080"
      - "50000:50000"
    environment:
      - JAVA_OPTS=-Xmx512m -XX:MaxRAMPercentage=50.0
    volumes:
      - /var/jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock
    restart: unless-stopped
    mem_limit: 1g
```

- [ ] **Step 3: Commit Jenkins Docker configurations**

```bash
git add infra/prod/Dockerfile.jenkins infra/prod/lightsail-a.compose.yml
git commit -m "infra: integrate jenkins docker service with resource limit constraints"
```

---

### Task 4: Create Dockerfile.production for backend

**Files:**
- Create: `backend/Dockerfile.production`

- [ ] **Step 1: Write Dockerfile.production**

Create `backend/Dockerfile.production` which expects a pre-compiled `app.jar` to minimize package-downloading and image compilation overhead:
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Commit Dockerfile.production**

```bash
git add backend/Dockerfile.production
git commit -m "infra: create lightweight production dockerfile for backend service"
```

---

### Task 5: Define the Declarative Jenkinsfile Pipeline

**Files:**
- Create: `Jenkinsfile`

- [ ] **Step 1: Write Jenkinsfile**

Create `Jenkinsfile` at the root directory to execute Gradle build, push to Docker Hub, and sequentially rolling-deploy to A and B:
```groovy
pipeline {
    agent any

    environment {
        DOCKER_IMAGE_NAME = 'chocojipsa/timedeal-backend'
        DOCKER_HUB_CREDENTIALS_ID = 'docker-hub-credentials'
        LIGHTSAIL_B_IP = '172.26.4.84'
        SSH_CREDENTIALS_ID = 'lightsail-b-ssh'
    }

    stages {
        stage('Build JAR') {
            agent {
                docker {
                    image 'eclipse-temurin:17-jdk'
                    args '-v /var/jenkins_home/.gradle:/root/.gradle'
                }
            }
            steps {
                dir('backend') {
                    sh 'chmod +x ./gradlew'
                    sh './gradlew bootJar -Dorg.gradle.jvmargs="-Xmx512m -XX:MaxMetaspaceSize=256m" --no-daemon'
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    sh 'cp backend/build/libs/*.jar backend/app.jar'
                    sh "docker build -f backend/Dockerfile.production -t ${DOCKER_IMAGE_NAME}:latest -t ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER} ./backend"
                }
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: DOCKER_HUB_CREDENTIALS_ID, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                        sh "docker push ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}"
                        sh "docker push ${DOCKER_IMAGE_NAME}:latest"
                    }
                }
            }
        }

        stage('Deploy to Lightsail A (Local)') {
            steps {
                script {
                    echo "Deploying to Lightsail A (Local)..."
                    dir('infra/prod') {
                        sh 'docker compose -f lightsail-a.compose.yml pull api-a'
                        sh 'docker compose -f lightsail-a.compose.yml up -d --no-deps api-a'
                    }
                    
                    echo "Checking health on Local api-a..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
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
                                cd ~/simulation/infra/prod && \
                                docker compose -f lightsail-b.compose.yml pull api-b worker traffic-generator && \
                                docker compose -f lightsail-b.compose.yml up -d --no-deps api-b worker traffic-generator
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
    }

    post {
        always {
            sh 'rm -f backend/app.jar'
            sh 'docker logout'
        }
    }
}
```

- [ ] **Step 2: Commit Jenkinsfile**

```bash
git add Jenkinsfile
git commit -m "ci: define declarative jenkinsfile pipeline with sequential local/remote rolling deploys"
```
