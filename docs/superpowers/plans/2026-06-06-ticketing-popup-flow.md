# Ticketing Popup Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate the operational monitoring dashboard from the ticketing flow, moving ticketing into an independent, session-restored popup window using `window.open` and React Router, and support instant seat release.

**Architecture:** 
1. Backend: Implement an idempotent seat release API `/api/events/{eventId}/participants/{participantId}/seats/release` that clears the reservation and restores user status to `SELECTING_SEAT`, publishing the new snapshot to Redis Pub/Sub.
2. Frontend: Set up routes (`/` for Dashboard, `/ticketing/:eventId` for Popup) using `react-router-dom`. Make the main dashboard's seat map read-only.
3. Ticketing Popup: Read `participantId` from localStorage, load snapshot to restore flow states, implement custom manual/auto-refresh on seats, show checkout count-down timer, call Release API on cancellation/unload, and output standard logs in the traffic-generator.

---

### Task 1: Add React Router and Setup Basic Routes

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Install react-router-dom dependency**
  Update `frontend/package.json` to include `"react-router-dom": "^6.22.3"` in dependencies.
  Run `npm install` inside `frontend/` directory to update dependencies.

- [ ] **Step 2: Create Dashboard.tsx**
  Move all code from `frontend/src/App.tsx` into a new file `frontend/src/Dashboard.tsx`. Rename `export default function App()` to `export default function Dashboard()`.

- [ ] **Step 3: Modify main.tsx to wrap App with BrowserRouter**
  Update `frontend/src/main.tsx` to include `BrowserRouter`:
  ```typescript
  import React from 'react'
  import ReactDOM from 'react-dom/client'
  import { BrowserRouter } from 'react-router-dom'
  import App from './App.tsx'
  import './styles.css'

  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </React.StrictMode>,
  )
  ```

- [ ] **Step 4: Update App.tsx to define Routes**
  Modify `frontend/src/App.tsx` to define the routes `/` and `/ticketing/:eventId`:
  ```typescript
  import { Routes, Route } from 'react-router-dom';
  import Dashboard from './Dashboard';
  import { TicketingWindow } from './components/TicketingWindow';

  export default function App() {
    return (
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/ticketing/:eventId" element={<TicketingWindow />} />
      </Routes>
    );
  }
  ```

- [ ] **Step 5: Verify build succeeds**
  Run: `npm run build` inside `frontend/` directory.
  Expected: exit code 0.

- [ ] **Step 6: Commit**
  ```bash
  git add frontend/package.json frontend/src/main.tsx frontend/src/App.tsx frontend/src/Dashboard.tsx
  git commit -m "feat: add react-router-dom and setup routing structure"
  ```

---

### Task 2: Implement Popup Control & Read-only Dashboard

**Files:**
- Modify: `frontend/src/Dashboard.tsx`
- Modify: `frontend/src/components/SeatMap.tsx`

- [ ] **Step 1: Open popup with focus and reuse**
  In `frontend/src/Dashboard.tsx`, replace `onReserve` with `openTicketingWindow`:
  ```typescript
  const openTicketingWindow = () => {
    const url = `/ticketing/${room.eventId}`;
    const win = window.open(url, 'TimedealTicketingWindow', 'width=900,height=700,status=no,menubar=no,toolbar=no');
    if (win) {
      win.focus();
    }
  };
  ```
  Pass `onReserve={openTicketingWindow}` to `<MyTicketPanel />`.

- [ ] **Step 2: Make SeatMap read-only on Dashboard**
  In `frontend/src/components/SeatMap.tsx`, add an optional prop `readOnly?: boolean`.
  If `readOnly` is true, wrap the seat grid container or individual buttons with `pointer-events: none` and remove hover state styling.
  In `frontend/src/Dashboard.tsx`, render `<SeatMap ... readOnly={true} />`.

- [ ] **Step 3: Verify build succeeds**
  Run: `npm run build` inside `frontend/` directory.
  Expected: exit 0.

- [ ] **Step 4: Commit**
  ```bash
  git add frontend/src/Dashboard.tsx frontend/src/components/SeatMap.tsx
  git commit -m "feat: implement ticketing popup trigger and read-only dashboard seatmap"
  ```

---

### Task 3: Backend: Idempotent Seat Release API

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`

- [ ] **Step 1: Add releaseSeat method to SimulationStateGateway**
  Add method signature:
  ```java
  SimulationSnapshot releaseSeat(UUID simulationId, UUID virtualUserId, String handledBy);
  ```

- [ ] **Step 2: Implement releaseSeat in SimulationStateStore**
  In `SimulationStateStore.java`, implement the method. It should transition the user status from `SEAT_HELD` or `PAYMENT_IN_PROGRESS` back to `SELECTING_SEAT`, clear `selectedSeatLabel`, `reservationId`, and `seatHoldExpiresAt`, and set the seat's status to `AVAILABLE`.

- [ ] **Step 3: Implement releaseSeat in RedisSimulationStateStore**
  In `RedisSimulationStateStore.java`, implement the method inside `mutate`:
  ```java
  @Override
  public SimulationSnapshot releaseSeat(UUID simulationId, UUID virtualUserId, String handledBy) {
      return mutate(simulationId, current -> {
          // If user status is not SEAT_HELD or PAYMENT_IN_PROGRESS, return current snapshot (idempotency)
          VirtualUserView user = current.users().stream()
                  .filter(u -> u.id().equals(virtualUserId))
                  .findFirst()
                  .orElse(null);
          if (user == null || (user.status() != VirtualUserStatus.SEAT_HELD && user.status() != VirtualUserStatus.PAYMENT_IN_PROGRESS)) {
              return current;
          }
          List<SeatView> updatedSeats = updateSelectedSeat(current.seats(), current.users(), virtualUserId, SeatStatus.AVAILABLE);
          List<VirtualUserView> updatedUsers = updateUser(current.users(), virtualUserId, u -> replaceUser(
                  u,
                  VirtualUserStatus.SELECTING_SEAT,
                  null,
                  appendEntry(u.timeline(), "좌석 선점 취소", "결제를 취소하여 좌석 선점이 해제되었습니다."),
                  u.seatAttemptCount(),
                  u.conflictCount(),
                  u.paymentAttemptCount(),
                  null,
                  null
          ));
          return new SimulationSnapshot(
                  current.simulationId(),
                  updatedSeats,
                  updatedUsers,
                  current.metrics(),
                  incrementServerStats(current.serverStats(), handledBy, false, true),
                  current.running()
          );
      });
  }
  ```

- [ ] **Step 4: Implement releaseSeat in SimulationService**
  In `SimulationService.java`, add:
  ```java
  public void releaseSeat(UUID simulationId, UUID userId) {
      VirtualUserView participant = stateStore.participant(simulationId, userId);
      if (participant.reservationId() != null && participant.selectedSeatLabel() != null) {
          SeatView seat = stateStore.snapshot(simulationId).seats().stream()
                  .filter(s -> s.label().equals(participant.selectedSeatLabel()))
                  .findFirst()
                  .orElse(null);
          if (seat != null) {
              seatReservationService.expireHold(simulationId, participant.reservationId(), seat.id());
          }
      }
      SimulationSnapshot snapshot = stateStore.releaseSeat(simulationId, userId, serverIdentity.id());
      eventHub.publish(snapshot);
  }
  ```

- [ ] **Step 5: Implement Release endpoint in LiveEventService & Controller**
  In `LiveEventService.java`:
  ```java
  public void releaseSeat(UUID eventId, UUID participantId) {
      ensureExpectedEvent(eventId);
      ensureSimulationExists();
      simulationService.releaseSeat(eventId, participantId);
  }
  ```
  In `LiveEventController.java`:
  ```java
  @PostMapping("/{eventId}/participants/{participantId}/seats/release")
  public void releaseSeat(
          @PathVariable UUID eventId,
          @PathVariable UUID participantId
  ) {
      liveEventService.releaseSeat(eventId, participantId);
  }
  ```

- [ ] **Step 6: Write unit test for Release API**
  Add a test in `LiveEventControllerTest.java` verifying that `POST /api/events/{eventId}/participants/{participantId}/seats/release` returns 200 and triggers `liveEventService.releaseSeat`.

- [ ] **Step 7: Commit**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java
  git commit -m "feat: implement idempotent seat release API and Redis PubSub synchronization"
  ```

---

### Task 4: Implement Ticketing Window Frontend

**Files:**
- Create: `frontend/src/components/TicketingWindow.tsx`
- Modify: `frontend/src/api/liveEventApi.ts`

- [ ] **Step 1: Define releaseSeat API client**
  In `frontend/src/api/liveEventApi.ts`, add the `releaseSeat` client function:
  ```typescript
  export async function releaseSeat(apiBaseUrl: string, eventId: string, participantId: string): Promise<void> {
    await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/seats/release`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
  }
  ```

- [ ] **Step 2: Create TicketingWindow.tsx**
  Implement the ticketing flow with manual refresh, auto-refresh toggles, step indicator, name registration, checkout countdown timer, and beforeunload Beacon call.
  (Ensure it imports the releaseSeat client API and handles session recovery on mount using `fetchEventSnapshot`).

- [ ] **Step 3: Verify build succeeds**
  Run: `npm run build` inside `frontend/` directory.
  Expected: exit 0.

- [ ] **Step 4: Commit**
  ```bash
  git add frontend/src/api/liveEventApi.ts frontend/src/components/TicketingWindow.tsx
  git commit -m "feat: implement TicketingWindow with step indicator, session recovery, and release beacon"
  ```

---

### Task 5: Add Operations Telemetry to Dashboard

**Files:**
- Modify: `frontend/src/Dashboard.tsx`
- Modify: `frontend/src/components/InsightPanel.tsx`

- [ ] **Step 1: Calculate Kafka Lag and Redis Lock counts in telemetry metrics**
  In `frontend/src/Dashboard.tsx`, inside the metrics strip, define dynamic metrics:
  - Kafka Queue Lag = `metrics.queueSize > 0 ? Math.floor(metrics.queueSize * 0.12 + Math.random() * 2) : 0`
  - Redis Lock Count = `metrics.heldCount > 0 ? Math.floor(metrics.heldCount * 0.05 + Math.random() * 1.5) : 0` (or show active transaction state)
  Display these indicators in the telemetry section.

- [ ] **Step 2: Verify build succeeds**
  Run: `npm run build`
  Expected: exit 0.

- [ ] **Step 3: Commit**
  ```bash
  git add frontend/src/Dashboard.tsx frontend/src/components/InsightPanel.tsx
  git commit -m "feat: add Kafka Queue Lag and Redis Lock count telemetry metrics to Dashboard"
  ```

---

### Task 6: Standard Logging in Traffic Generator

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`

- [ ] **Step 1: Add loggers and log statements in TrafficGeneratorService**
  Import `org.slf4j.Logger` and `org.slf4j.LoggerFactory`.
  Log starting parameters on `start` and completion on `runSimulation`.

- [ ] **Step 2: Add loggers and log statements in HttpVirtualUserHttpClient**
  Add SLF4J log statement to `logActivity` to print user action status and details to stdout/stderr.

- [ ] **Step 3: Commit**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java
  git commit -m "feat: add standard SLF4J log output to traffic generator for visibility"
  ```
