# Queue SSE Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transition the queue position check in Step 2 of the Ticketing Window from HTTP polling to SSE stream with automatic fallback.

**Architecture:** 
1. Enable scheduling in backend main class.
2. Implement `LiveEventQueueScheduler` running at 1-second fixed rate to check active admission counts, issue admission tokens to candidates, and broadcast remaining users' updated queue positions.
3. Expose methods in `SimulationService` for checking active admissions limit and doing participant admission.
4. Modify React component `TicketingWindow` to listen to the SSE participant stream and handle fallback gracefully using React `useEffect` hooks.

**Tech Stack:** Spring Boot 3.3 / Java 17 / Redis / React 18 / TypeScript / EventSource (SSE)

---

### Task 1: Backend Scheduling Activation & Exposing Methods in SimulationService

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/SeatReservationApplication.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`

- [ ] **Step 1: Enable scheduling in SeatReservationApplication**
  Modify [SeatReservationApplication.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/SeatReservationApplication.java) to add `@EnableScheduling`.

- [ ] **Step 2: Add helper methods in SimulationService**
  Modify [SimulationService.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java) to:
  1. Add `getMaxActiveAdmissions()` getter.
  2. Add public `admitParticipant(UUID simulationId, UUID userId)` method.

---

### Task 2: Create LiveEventQueueScheduler

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/queue/LiveEventQueueScheduler.java`

- [ ] **Step 1: Write LiveEventQueueScheduler**
  Create [LiveEventQueueScheduler.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/queue/LiveEventQueueScheduler.java):
  - Inject `LiveEventService`, `SimulationService`, `WaitingQueueService`.
  - Add `@Scheduled(fixedRate = 1000)` method `processQueue()`.
  - Retrieve active event, filter active participants, pick candidates if below limit.
  - Process admissions and emit pub/sub events.
  - Loop remaining queued participants and emit queue position updates.

---

### Task 3: Frontend TicketingWindow SSE Connection & Fallback

**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx`

- [ ] **Step 1: Refactor step 2 side-effect**
  Modify [TicketingWindow.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/components/TicketingWindow.tsx):
  - Add local state: `sseQueuePos` and `sseEstimatedSeconds`.
  - Replace the 1.5s interval HTTP polling `useEffect` with SSE `EventSource` connection, event registration, and `onerror` fallback handling to HTTP polling.
  - Clean up EventSource and timer properly on unmount.
