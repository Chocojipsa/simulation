# Fix Simulation Bottlenecks and UI Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the EventSource SSE stream cancellation errors, improve queue transition robustness, bypass the participant name entry at queue start, move name entry to the payment step, and optimize the AI simulation performance by eliminating heavy Redis lock-write operations for transient activity logs.

**Architecture:** 
1. Optimize the traffic generator to use direct, memory-based Redis Pub/Sub broadcasting for transient logs via autowired `SimulationService` instead of HTTP REST loops.
2. Update the `TrafficGeneratorController` to handle incoming activity logs via direct pub/sub publishing, avoiding Redis locking/database writes.
3. Clean up the unused `displayName` field and dead `handleJoin` code in the frontend `TicketingWindow.tsx` and ensure that `EventSource` auto-reconnects rather than closing permanently on errors.
4. Increase the default seat selection TTL from 15 seconds to 60 seconds to prevent premature queue expiration due to transient network lag.

**Tech Stack:** Java 17, Spring Boot, Redis, React, TypeScript

---

### Task 1: Optimize AI Traffic Generator Activity Logging

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`

- [ ] **Step 1: Autowire SimulationService in HttpVirtualUserHttpClient**
  Add `@Autowired(required = false) private SimulationService simulationService;` to bypass REST calls when running locally.

- [ ] **Step 2: Update logActivity to publish directly**
  Modify the `logActivity` method to check if `simulationService` is present, and if so, call `publishUserActivityDirectly(...)` instead of making a REST call.

```java
    @Autowired(required = false)
    private com.timedeal.seatreservation.simulation.SimulationService simulationService;

    private void logActivity(UUID simulationId, UUID userId, String label, String message) {
        if (simulationService != null) {
            try {
                simulationService.publishUserActivityDirectly(simulationId, userId, label, message);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            restClient.post()
                    .uri(controlBaseUrl + "/internal/traffic-generator/simulations/{simulationId}/users/{userId}/activity", simulationId, userId)
                    .body(new UserActivityEvent(simulationId, userId, label, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Logging failure should not break the simulation
        }
    }
```

- [ ] **Step 3: Run unit tests to verify compile and correctness**
  Run: `./gradlew compileJava compileTestJava`

- [ ] **Step 4: Commit changes**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java
  git commit -m "perf: publish virtual user activity directly to bypass REST overhead"
  ```

---

### Task 2: Optimize Controller Endpoint for External Activity Logs

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorController.java`

- [ ] **Step 1: Modify recordActivity to bypass database state writes**
  Update the mapping in `TrafficGeneratorController` to call `publishUserActivityDirectly` instead of `recordUserActivity`.

```java
    @PostMapping("/{simulationId}/users/{userId}/activity")
    public void recordActivity(
            @PathVariable UUID simulationId,
            @PathVariable UUID userId,
            @RequestBody UserActivityEvent event
    ) {
        simulationService.publishUserActivityDirectly(simulationId, userId, event.label(), event.message());
    }
```

- [ ] **Step 2: Verify compile**
  Run: `./gradlew compileJava`

- [ ] **Step 3: Commit changes**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorController.java
  git commit -m "perf: bypass persistent state mutations for external activity logs"
  ```

---

### Task 3: Increase Default Seat Selection TTL

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: Update fallback TTL in SimulationService**
  In `SimulationService.java`, change the default value in `@Value("${waiting-queue.selection-ttl-seconds:15}")` to `60` seconds.

- [ ] **Step 2: Update configuration files**
  In `application-local.yml` and `application-prod.yml`, change `selection-ttl-seconds: ...:15` fallback to `60`.

- [ ] **Step 3: Verify tests and compile**
  Run: `./gradlew test` (Wait, since we changed the default in tests, if any test asserts exactly 15, we may need to check).

- [ ] **Step 4: Commit changes**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/main/resources/application-local.yml backend/src/main/resources/application-prod.yml
  git commit -m "config: increase seat selection TTL to 60 seconds to prevent premature queue expiration"
  ```

---

### Task 4: Improve Frontend EventSource Robustness and Cleanup

**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx`

- [ ] **Step 1: Prevent EventSource from closing on error**
  Modify `eventSource.onerror` inside the Step 2 `useEffect` hook to log the error but *not* close the stream. This allows the browser to auto-reconnect.

- [ ] **Step 2: Remove unused handleJoin and displayName code**
  Remove the dead `handleJoin` function and clean up the unused state/inputs related to it.

- [ ] **Step 3: Verify typescript build**
  Run: `npm run build` or equivalent type check in `frontend/` directory.

- [ ] **Step 4: Commit changes**
  ```bash
  git add frontend/src/components/TicketingWindow.tsx
  git commit -m "frontend: enable EventSource auto-reconnect and clean up dead name-joining code"
  ```
