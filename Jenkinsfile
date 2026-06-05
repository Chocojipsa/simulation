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
                    sh './gradlew clean bootJar -Dorg.gradle.jvmargs="-Xmx512m -XX:MaxMetaspaceSize=256m" --no-daemon'
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    sh 'find backend/build/libs/ -name "*.jar" ! -name "*-plain.jar" -exec cp {} backend/app.jar \\;'
                    sh "docker build -f backend/Dockerfile.production -t \${DOCKER_IMAGE_NAME}:latest -t \${DOCKER_IMAGE_NAME}:\${BUILD_NUMBER} ./backend"
                }
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: DOCKER_HUB_CREDENTIALS_ID, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                        sh "docker push \${DOCKER_IMAGE_NAME}:\${BUILD_NUMBER}"
                        sh "docker push \${DOCKER_IMAGE_NAME}:latest"
                    }
                }
            }
        }

        stage('Deploy to Lightsail A (Local)') {
            steps {
                script {
                    echo "Deploying to Lightsail A (Local)..."
                    dir('infra/prod') {
                        // Securely stage the template config to avoid truncating nginx-api.conf on failure
                        sh "git show HEAD:infra/prod/nginx-api.conf > nginx-api.conf.tmp"
                        sh "sed 's/LIGHTSAIL_B_PRIVATE_IP/${LIGHTSAIL_B_IP}/g' nginx-api.conf.tmp > nginx-api.conf"
                        sh "rm -f nginx-api.conf.tmp"

                        sh "BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-a.compose.yml pull api-a"
                        sh "BACKEND_VERSION=${BUILD_NUMBER} docker compose -f lightsail-a.compose.yml up -d --no-deps api-a"
                        // Dynamic Nginx configuration reload to apply active failover settings without downtime
                        sh "docker compose -f lightsail-a.compose.yml exec -T nginx nginx -s reload || docker compose -f lightsail-a.compose.yml restart nginx"
                    }
                    
                    echo "Checking health on Local api-a via Nginx Proxy..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
                                    // Query through the reverse proxy to verify actual routing is functional
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://nginx:8080/health", returnStdout: true).trim()
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
    }

    post {
        always {
            sh 'rm -f backend/app.jar'
            // Revert local nginx configuration file modifications in workspace safely preserving Inode
            sh """
                git show HEAD:infra/prod/nginx-api.conf > infra/prod/nginx-api.conf.tmp && \\
                cat infra/prod/nginx-api.conf.tmp > infra/prod/nginx-api.conf || true
                rm -f infra/prod/nginx-api.conf.tmp
            """
            sh 'docker logout'
        }
    }
}

