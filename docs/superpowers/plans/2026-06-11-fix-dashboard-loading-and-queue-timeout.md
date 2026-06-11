# Dashboard Loading and Queue Timeout Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the frontend dashboard flickering / "Loading booking events..." flashing by persisting snapshot state across page remounts, and resolve the wait queue blockage at concurrency 1 by immediately failing/releasing AI users when no seats are available.

**Architecture:**
1. **Frontend**: Introduce a module-level snapshot cache and last active event ID in `useLiveEventRoom.ts` so that when a component (like Dashboard or MonitoringConsole) remounts, it starts with the cached snapshot instead of `null`. Provide a cleanup helper for test isolation.
2. **Backend**: Expose a `/fail` participant endpoint to allow users (specifically AI virtual users) to transition to `FAILED` status and release their selecting seat active slots immediately.
3. **AI HttpClient**: In `RestVirtualUserCommandClient.java`, if no seats are available, call `/fail` and return `FAILED` status to exit the retry loop immediately.

**Tech Stack:** Spring Boot (Java), React, TypeScript, Vitest, Gradle

---

### Task 1: Frontend Snapshot Caching

**Files:**
- Modify: `frontend/src/hooks/useLiveEventRoom.ts`
- Modify: `frontend/src/hooks/useLiveEventRoom.test.tsx`

- [ ] **Step 1: Implement module-level cache and update initialization in useLiveEventRoom.ts**

Modify [useLiveEventRoom.ts](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/hooks/useLiveEventRoom.ts):
```typescript
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
// ... other imports ...

const participantStorageKey = 'timedeal.participantId';

// Module-level cache to persist snapshot and event ID across page remounts
const snapshotCache = new Map<string, LiveEventSnapshot>();
let lastActiveEventId: string | null = null;

export function clearLiveEventRoomCache() {
  snapshotCache.clear();
  lastActiveEventId = null;
}

export function useLiveEventRoom(apiBaseUrl: string) {
  const [eventId, setEventId] = useState<string | null>(lastActiveEventId);
  const [participantId, setParticipantId] = useState<string | null>(() => window.localStorage.getItem(participantStorageKey));
  const [snapshot, setSnapshot] = useState<LiveEventSnapshot | null>(() => {
    return lastActiveEventId ? (snapshotCache.get(lastActiveEventId) || null) : null;
  });
  // ...
```

Update `refresh`, the SSE event listener, and `reset` inside [useLiveEventRoom.ts](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/hooks/useLiveEventRoom.ts) to populate and delete from `snapshotCache`:
```typescript
  const refresh = useCallback(async () => {
    if (!eventId || refreshingRef.current) return;
    refreshingRef.current = true;
    try {
      const next = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
      setSnapshot(prev => {
        const updated = normalizeSnapshot(next, prev);
        if (updated) {
          snapshotCache.set(eventId, updated);
        }
        return updated;
      });
    } finally {
      refreshingRef.current = false;
    }
  }, [apiBaseUrl, eventId, participantId]);
```

Update the `fetchActiveEvent` effect and introduction of `updateEventId` helper:
```typescript
  const updateEventId = useCallback((newId: string | null) => {
    lastActiveEventId = newId;
    setEventId(newId);
    if (newId) {
      setSnapshot(snapshotCache.get(newId) || null);
    } else {
      setSnapshot(null);
    }
  }, []);

  useEffect(() => {
    void fetchActiveEvent(apiBaseUrl).then((event) => {
      updateEventId(event.eventId);
    });
  }, [apiBaseUrl, updateEventId]);
```

In the SSE event listener:
```typescript
              setSnapshot(prev => {
                const updated = normalizeSnapshot(snap, prev);
                if (eventId && updated) {
                  snapshotCache.set(eventId, updated);
                }
                return updated;
              });
```

In `reset`:
```typescript
  const reset = useCallback(async () => {
    if (!eventId) return;
    await resetEvent(apiBaseUrl, eventId);
    window.localStorage.removeItem(participantStorageKey);
    setParticipantId(null);
    snapshotCache.delete(eventId);
    setSnapshot(null);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);
```

- [ ] **Step 2: Clean up cache in useLiveEventRoom.test.tsx after each test**

Modify [useLiveEventRoom.test.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/hooks/useLiveEventRoom.test.tsx):
```typescript
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/liveEventApi';
import { useLiveEventRoom, clearLiveEventRoomCache } from './useLiveEventRoom';

// ...

describe('useLiveEventRoom', () => {
  afterEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
    vi.restoreAllMocks();
    clearLiveEventRoomCache();
  });
// ...
```

- [ ] **Step 3: Run frontend unit tests to verify correctness**

Run in terminal:
`npm run test --run src/hooks/useLiveEventRoom.test.tsx`
Cwd: `/mnt/c/users/kwon/desktop/workspace/timedeal/frontend`
Expected: PASS

---

### Task 2: Backend Fail Participant Endpoint

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`

- [ ] **Step 1: Add failParticipant endpoint to LiveEventController.java**

Modify [LiveEventController.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java):
```java
    @PostMapping("/{eventId}/participants/{participantId}/seats/release")
    public void releaseSeat(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        liveEventService.releaseSeat(eventId, participantId);
    }

    @PostMapping("/{eventId}/participants/{participantId}/fail")
    public void failParticipant(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        liveEventService.failParticipant(eventId, participantId);
    }
```

- [ ] **Step 2: Implement failParticipant in LiveEventService.java**

Modify [LiveEventService.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java):
```java
    public void releaseSeat(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        simulationService.releaseSeat(eventId, participantId);
    }

    public void failParticipant(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        simulationService.failParticipant(eventId, participantId);
    }
```

- [ ] **Step 3: Implement failParticipant in SimulationService.java**

Modify [SimulationService.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java):
```java
    public void releaseSeat(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        SimulationSnapshot updatedSnapshot = stateStore.releaseSeat(simulationId, userId, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updatedSnapshot);
        }
    }

    public void failParticipant(UUID simulationId, UUID userId) {
        SimulationSnapshot updated = stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }
    }
```

---

### Task 3: AI Client Stop on Empty Seats

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/RestVirtualUserCommandClient.java`

- [ ] **Step 1: Update holdRandomSeat in RestVirtualUserCommandClient.java**

Modify [RestVirtualUserCommandClient.java](file:///mnt/c/users/kwon/desktop/workspace/timedeal/backend/src/main/java/com/timedeal/seatreservation/generator/RestVirtualUserCommandClient.java):
```java
    @Override
    public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
        LiveEventSnapshot snapshot = restClient.get()
                .uri(baseUrl + "/api/events/{eventId}/snapshot?participantId={participantId}", eventId, participantId)
                .retrieve()
                .body(LiveEventSnapshot.class);
        List<SeatView> availableSeats = snapshot.seats().stream()
                .filter(candidate -> candidate.status() == SeatStatus.AVAILABLE)
                .toList();
        if (availableSeats.isEmpty()) {
            restClient.post()
                    .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/fail", eventId, participantId)
                    .retrieve()
                    .toBodilessEntity();
            return new SeatHoldResponse(eventId, participantId, 0L, "FAILED", "선택 가능한 좌석이 없습니다.", null, "generator");
        }
        SeatView seat = availableSeats.get(ThreadLocalRandom.current().nextInt(availableSeats.size()));
        return restClient.post()
                .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/seats/{seatId}/hold", eventId, participantId, seat.id())
                .retrieve()
                .body(SeatHoldResponse.class);
    }
```

---

### Task 4: Verification and Backend Tests

- [ ] **Step 1: Run backend tests to verify no regressions**

Run in terminal:
`./gradlew test`
Cwd: `/mnt/c/users/kwon/desktop/workspace/timedeal/backend`
Expected: BUILD SUCCESS (All tests pass)
