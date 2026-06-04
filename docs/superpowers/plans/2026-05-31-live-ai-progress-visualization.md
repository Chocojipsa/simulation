# Design Doc: Live AI Progress Visualization

## Objective
Provide a real-time, granular view of AI participants' internal "thought processes" and actions during the simulation. Users should be able to click on an AI and see its live activity log (e.g., "Scanning for seats", "Attempting to hold A-1", "Payment failed, retrying").

## Background & Motivation
Currently, the simulation only shows discrete state changes (e.g., WAITING -> SEAT_HELD). The intermediate actions and decisions made by the AI (both in `demo` mode and `generator` mode) are invisible, making the simulation feel like a "black box". A Playwright-like action log will enhance observability and user engagement.

## Proposed Solution

### 1. Granular User Activity Events
We will introduce a `UserActivityEvent` that captures more than just state changes.
- **Fields**: `simulationId`, `userId`, `label` (e.g., "THINKING"), `message` (e.g., "Seat A-1 looks good"), `timestamp`.

### 2. Backend: Event Emission
- **Generator Mode (`HttpVirtualUserHttpClient`)**:
  - Add "Thought" logs before API calls.
  - Add "Action" logs when receiving API responses (including failures/retries).
  - Since this runs in a separate process/node, it will send these logs to the backend via a new internal API: `POST /internal/simulations/{id}/users/{userId}/activity`.
- **Demo Mode (`SimulationRunner`)**:
  - Emit events directly during the `tick()` cycle when AI logic executes.
- **Simulation State**:
  - Store a limited buffer (e.g., last 10 entries) of these granular logs in the `VirtualUserView`'s `timeline` for initial page loads.

### 3. Backend: Real-time Streaming (SSE)
- **`SimulationEventHub` Enhancement**:
  - Support subscribing to user-specific events.
  - Integrate with Redis Pub/Sub to ensure events are distributed across all API nodes.
- **New Controller Endpoint**:
  - `GET /api/events/{eventId}/participants/{participantId}/stream` (SSE).

### 4. Frontend: Live Log UI
- **`useUserActivityStream` Hook**: 
  - Manages the `EventSource` connection for the selected user.
- **`UserPanel` Update**:
  - Add a "Live Activity" tab or section.
  - Display a scrolling log of events.
  - Visual indicator (e.g., pulse icon) showing the AI is currently "active".

## Implementation Plan

### Phase 1: Backend Foundation (Events & Internal API)
1.  **Create `UserActivityEvent` DTO**:
    *   Path: `backend/src/main/java/com/timedeal/seatreservation/simulation/UserActivityEvent.java`
    *   Fields: `simulationId` (UUID), `userId` (UUID), `label` (String), `message` (String), `timestamp` (Instant).
2.  **Add Internal Activity API**:
    *   File: `TrafficGeneratorController.java`
    *   New Endpoint: `POST /internal/simulations/{id}/users/{userId}/activity`
    *   Action: Broadcasts the event via `SimulationEventHub` and (optionally) updates the in-memory/Redis state for persistent timeline.
3.  **Update `SimulationStateGateway` / `SimulationStateStore`**:
    *   Add `recordUserActivity(UUID simulationId, UUID userId, String label, String message)` method.
    *   Implementation should append the entry to the user's `timeline` with a max size (e.g., 20) to prevent memory bloating.

### Phase 2: AI Logic Enrichment (The "Thoughts")
1.  **Update `HttpVirtualUserHttpClient.java` (Generator Mode)**:
    *   Inject `RestClient` or a dedicated logging client to call the internal activity API.
    *   Add `logActivity(UUID userId, String label, String message)` helper.
    *   Insert calls in `runUser`, `waitUntilAdmitted`, and `confirmPaymentUntilAccepted`:
        *   Before joining: `logActivity(id, "INTENT", "이벤트 입장을 시도합니다.")`
        *   During queueing: `logActivity(id, "WAITING", "대기열 순서를 기다리는 중입니다...")`
        *   Before seat selection: `logActivity(id, "THINKING", "어떤 좌석이 비었는지 확인 중...")`
        *   After seat selection: `logActivity(id, "ACTION", label + " 좌석 선점을 시도합니다.")`
        *   On conflict: `logActivity(id, "RETRY", "좌석이 이미 찼네요. 다른 좌석을 찾아볼게요.")`
2.  **Update `SimulationRunner.java` (Demo Mode)**:
    *   Similar logic but direct calls to `stateStore.recordUserActivity`.

### Phase 3: SSE Streaming (Real-time Delivery)
1.  **Enhance `SimulationEventHub.java`**:
    *   Maintain a mapping for user-specific emitters: `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> userEmitters`.
    *   Add `openUserStream(UUID userId)` method.
    *   Add `publishUserActivity(UserActivityEvent event)` method.
2.  **Add SSE Endpoint**:
    *   File: `LiveEventController.java`
    *   New Endpoint: `GET /api/events/{eventId}/participants/{participantId}/stream`
    *   Action: Returns `eventHub.openUserStream(participantId)`.

### Phase 4: Frontend Visualization (The "Live View")
1.  **Create `useUserActivityStream.ts`**:
    *   Path: `frontend/src/hooks/useUserActivityStream.ts`
    *   Logic: Uses `EventSource` to connect to the new SSE endpoint. Updates a local `activities` state.
2.  **Update `UserPanel.tsx`**:
    *   Integrate `useUserActivityStream`.
    *   Add a scrolling log container for "Live Activity".
    *   Style new activity entries with distinctive colors (e.g., INTENT=blue, ACTION=green, RETRY=orange).
3.  **Add Activity Indicator**:
    *   In the user list, add a small "pulsing" dot next to users who have had activity in the last 5 seconds.

## Verification & Testing
- **Unit Tests**: Test that `HttpVirtualUserHttpClient` correctly emits logs.
- **Integration Tests**: Verify SSE endpoint returns expected events when an activity is posted.
- **Manual Verification**: Run simulation in `demo` and `generator` modes, click AI users, and verify the log scrolls in real-time.

## Alternatives Considered
- **WebSockets**: Overkill for one-way log streaming. SSE is lighter and fits the existing `SimulationEventHub` pattern.
- **Polling**: Too slow and high overhead for "live" feel.
