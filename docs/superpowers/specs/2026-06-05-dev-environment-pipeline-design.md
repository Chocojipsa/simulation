# Dev Staging Environment & Pipeline Integration Design Spec

This document specifies the design for introducing a separate Developer Staging (`dev-api`) environment on AWS Lightsail Instance B and integrating it as an automated gating stage in the Jenkins CI/CD pipeline. 

---

## 1. Goal and Constraints

- **Primary Goal**: Deploy a dedicated developer environment (`dev-api`) to isolate staging verification from production. The production environment should only be updated if the deployment to the dev environment passes health checks.
- **Constraints**:
  - **Memory Limits**: Lightsail instance B runs production `api-b`, `worker`, and `traffic-generator`, consuming ~860MB out of 2GB. The new `dev-api` service must run within safe memory bounds (~280MB) to prevent OOM termination.
  - **Port Isolation**: Since production `api-b` binds to port `8080`, the `dev-api` service must bind to a distinct host port (`8082`).
  - **Zero Compilation on B**: The dev container must only pull pre-compiled images from Docker Hub, eliminating compilation overhead on Node B.

---

## 2. Infrastructure Architecture

```
   [ AWS Lightsail A (Jenkins) ]                [ AWS Lightsail B (Hosts App) ]
   ├── Nginx Load Balancer                      ├── Production api-b (Port 8080)
   └── Jenkins Controller                       ├── Staging dev-api (Port 8082) [NEW]
            │                                   ├── Production worker
            │ (1) SSH remote deploy commands    └── Production traffic-generator
            ▼                                               │
   [ Docker Hub Registry ] ◄── (2) Pull image ──────────────┘
```

1. **Jenkins (A)** builds, tags, and pushes `chocojipsa/timedeal-backend:${BUILD_NUMBER}` to Docker Hub.
2. **Jenkins (A)** SSHs into **Lightsail B** and restarts the `dev-api` container using the new image.
3. **Jenkins (A)** polls `http://172.26.4.84:8082/health` to verify status.
4. **Jenkins (A)** only proceeds to deploy to production (`api-a` on Local A, `api-b` on Remote B) if the dev health check succeeds.

---

## 3. Configuration Changes

### 3.1 Docker Compose Configuration (`infra/prod/lightsail-b.compose.yml`)
Add the `dev-api` service configuration binding to host port `8082`:
```yaml
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
```

### 3.2 Jenkinsfile Pipeline Changes (`Jenkinsfile`)
1. Insert the `Deploy to Dev (Remote)` stage after the `Docker Push` stage.
2. Configure it to connect to Lightsail B via SSH, pull the new image for the `dev-api` service, and recreate it.
3. Add a health checking loop that queries `http://${LIGHTSAIL_B_IP}:8082/health` for up to 5 minutes.
4. Ensure that the existing `Deploy to Lightsail B (Remote)` stage does not touch or restart the `dev-api` container (it only manages `api-b`, `worker`, and `traffic-generator`).

---

## 4. Security & Network Setup
- **Lightsail B Firewall**: Add an inbound port rule for TCP port `8082` on Lightsail B so the Jenkins controller on A can perform direct health checks on the staging container.
- **Environment Isolation**: The `dev-api` container shares the production database/Redis/Kafka configuration for resource-saving purposes, but uses `APP_INSTANCE_ID: dev-api` to distinguish its logs and metrics in trace outputs.
