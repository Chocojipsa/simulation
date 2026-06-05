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
                        sh 'docker compose -f lightsail-a.compose.yml pull api-a'
                        sh 'docker compose -f lightsail-a.compose.yml up -d --no-deps api-a'
                        // Safe update of nginx service (reloads container if config changed, without recreating jenkins)
                        sh 'docker compose -f lightsail-a.compose.yml up -d --no-deps nginx'
                    }
                    
                    echo "Checking health on Local api-a..."
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            script {
                                try {
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://api-a:8080/health", returnStdout: true).trim()
                                    echo "Health check response: \${response}"
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
                            ssh -o StrictHostKeyChecking=no ubuntu@\${LIGHTSAIL_B_IP} '
                                cd ~/simulation && git pull && \
                                cd infra/prod && \
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
                                    def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://\${LIGHTSAIL_B_IP}:8080/health", returnStdout: true).trim()
                                    echo "Health check response: \${response}"
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
