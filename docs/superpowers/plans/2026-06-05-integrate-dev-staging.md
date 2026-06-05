# Integrate Dev Staging Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the Developer Staging deployment stage into Jenkinsfile and apply CPU/memory constraints to the dev-api container.

**Architecture:** Add a new Jenkins pipeline stage `Deploy to Dev (Remote)` before `Deploy to Lightsail A (Local)`, update the Lightsail B deployment stage, and configure `dev-api` with resource limits in the compose file.

**Tech Stack:** Jenkins Pipeline (Groovy), Docker Compose

---

### Task 1: Update Jenkinsfile

**Files:**
- Modify: [Jenkinsfile](file:///mnt/c/users/kwon/desktop/workspace/timedeal/Jenkinsfile#L52-L127)

- [ ] **Step 1: Replace lines 52 to 127 in Jenkinsfile with the new stages**
  Ensure correct variable interpolation:
  - `${LIGHTSAIL_B_IP}` is NOT escaped.
  - `${BUILD_NUMBER}` is NOT escaped.
  - `${response}` is NOT escaped.
  - `\$PROD_ENV` is escaped with a single backslash.
  - `\$(git rev-parse ...)` is escaped with a single backslash.

### Task 2: Configure memory limits on dev-api

**Files:**
- Modify: [lightsail-b.compose.yml](file:///mnt/c/users/kwon/desktop/workspace/timedeal/infra/prod/lightsail-b.compose.yml#L13-L23)

- [ ] **Step 1: Add mem_limit and JAVA_TOOL_OPTIONS to dev-api**
  Apply `mem_limit: 280m` and environment variable `JAVA_TOOL_OPTIONS: "-Xmx192m -XX:MaxRAMPercentage=70.0"`. Verify `${BACKEND_VERSION:-latest}` does not have a backslash in front of `$`.

### Task 3: Verification and Commit

- [ ] **Step 1: Verify docker compose file syntax**
  Create a temporary `.env` file in `infra/prod` and run `docker compose -f lightsail-b.compose.yml config`.
- [ ] **Step 2: Commit all changes**
  Commit the modified files with message: `ci: integrate dev staging gating stage and configure resource constraints on dev-api`
