# Session Management & AI Sequential Naming Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the dual-session bug for human users (preventing concurrency exhaustion) and the duplicate AI name bug (generating unique, sequential AI participant names).

**Architecture:** Restructure the front-end to make the Dashboard the sole creator of participant sessions, synchronize session states between windows via the `storage` event, and modify the back-end scheduler to propagate index offsets to subsequent batches of virtual users.

**Tech Stack:** React, TypeScript, Spring Boot, Java

---

### Task 1: Backend DTO & API Changes (Offset Parameter)

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationRequest.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java`

- [ ] **Step 1: Update RunSimulationRequest record**

Modify `RunSimulationRequest.java` to add the `virtualUserOffset` field with a default constructor.
```java
package com.timedeal.seatreservation.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RunSimulationRequest(
        @Min(1) @Max(1000) int virtualUserCount,
        @Min(1) @Max(100) int concurrency,
        int virtualUserOffset
) {
    public RunSimulationRequest(int virtualUserCount, int concurrency) {
        this(virtualUserCount, concurrency, 0);
    }
}
```

- [ ] **Step 2: Update AiBatch record**

Modify `AiBatch.java` to add the `virtualUserOffset` field.
```java
package com.timedeal.seatreservation.event;

import java.time.Duration;

public record AiBatch(
        int participantCount,
        int concurrency,
        Duration delay,
        int virtualUserOffset
) {
    public AiBatch(int participantCount, int concurrency, Duration delay) {
        this(participantCount, concurrency, delay, 0);
    }
}
```

- [ ] **Step 3: Run backend build to verify DTO compatibility**

Run command: `./gradlew compileJava` in the `backend` directory.
Expected output: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit DTO updates**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationRequest.java backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java
git commit -m "refactor: add virtualUserOffset field to RunSimulationRequest and AiBatch"
```

---

### Task 2: Backend AI Starter & Generator Changes (Index Offset Logic)

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`

- [ ] **Step 1: Update LiveEventAiStarter buildCustomSchedule and scheduler calls**

Modify `LiveEventAiStarter.java` lines 114-145 to track `currentOffset` and populate the `AiBatch` constructor, and pass the offset to `RunSimulationRequest`.
```java
        AiBatchSchedule schedule = buildCustomSchedule(count, maxConcurrency, interval);
        for (AiBatch batch : schedule.batches()) {
            scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                    eventId,
                    new RunSimulationRequest(batch.participantCount(), batch.concurrency(), batch.virtualUserOffset())
            ));
        }
```
And update `buildCustomSchedule`:
```java
    private AiBatchSchedule buildCustomSchedule(int participantCount, int maxConcurrency, Duration interval) {
        int remaining = Math.max(0, participantCount);
        int normalizedConcurrency = Math.max(1, maxConcurrency);
        double[] batchPercentages = {0.10, 0.15, 0.20, 0.25, 0.30};
        long delayMillis = interval.toMillis();
        java.util.ArrayList<AiBatch> batches = new java.util.ArrayList<>();
        int currentOffset = 0;
        
        for (double pct : batchPercentages) {
            if (remaining <= 0) break;
            int count = (int) Math.round(participantCount * pct);
            count = Math.min(count, remaining);
            if (count <= 0) continue;
            
            int concurrency = Math.min(normalizedConcurrency, count);
            batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delayMillis), currentOffset));
            currentOffset += count;
            remaining -= count;
            delayMillis += interval.toMillis();
        }
        if (remaining > 0) {
            int concurrency = Math.min(normalizedConcurrency, remaining);
            batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(delayMillis), currentOffset));
        }
        return new AiBatchSchedule(List.copyOf(batches));
    }
```

- [ ] **Step 2: Update TrafficGeneratorService sequence generator**

Modify `TrafficGeneratorService.java` lines 100-103 to start loops with the index offset.
```java
        try {
            for (int number = 1; number <= request.virtualUserCount(); number++) {
                int virtualUserNumber = request.virtualUserOffset() + number;
                executor.submit(() -> runVirtualUser(simulationId, virtualUserNumber));
            }
        } finally {
```

- [ ] **Step 3: Update LiveEventAiStarterTest assertions to include expected offsets**

Modify `LiveEventAiStarterTest.java` lines 45-49 to pass expected offsets:
```java
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(15, 15, 0));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(23, 23, 15));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(30, 30, 38));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(38, 38, 68));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(44, 44, 106));
```

- [ ] **Step 4: Run tests to verify the backend logic**

Run command: `./gradlew test` in the `backend` directory.
Expected output: BUILD SUCCESSFUL (All tests pass).

- [ ] **Step 5: Commit backend generator fixes**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java
git commit -m "feat: implement AI index offset scheduling to prevent duplicate names"
```

---

### Task 3: Front-end Hook Changes (Join Response & storage Sync)

**Files:**
- Modify: `frontend/src/hooks/useLiveEventRoom.ts`

- [ ] **Step 1: Update useLiveEventRoom.ts join return value**

Modify the `join` method inside `useLiveEventRoom.ts` to return the join response:
```typescript
  const join = useCallback(async (displayName: string) => {
    if (!eventId) return null;
    setLoading(true);
    try {
      const res = await joinEvent(apiBaseUrl, eventId, displayName);
      window.localStorage.setItem(participantStorageKey, res.participantId);
      setParticipantId(res.participantId);
      await refresh();
      return res;
    } catch (err) {
      setError('입장에 실패했습니다.');
      throw err;
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl, eventId, refresh]);
```

- [ ] **Step 2: Add localStorage storage listener**

Add a `storage` listener inside `useLiveEventRoom.ts` to sync `participantId` across open windows/tabs:
```typescript
  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === participantStorageKey) {
        setParticipantId(e.newValue);
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);
```

- [ ] **Step 3: Run frontend typecheck**

Run command: `npm run typecheck` or check for build failures in the `frontend` directory.
Expected output: No typescript errors.

- [ ] **Step 4: Commit hook updates**

```bash
git add frontend/src/hooks/useLiveEventRoom.ts
git commit -m "feat: return join response and sync participantId via storage event"
```

---

### Task 4: Front-end UI Components Changes (Dashboard & TicketingWindow)

**Files:**
- Modify: `frontend/src/Dashboard.tsx`
- Modify: `frontend/src/components/TicketingWindow.tsx`

- [ ] **Step 1: Update Dashboard.tsx to auto-join before opening TicketingWindow**

Modify `openTicketingWindow` to be an async function, call `room.join` if `room.participantId` is null, and append `participantId` as a query parameter.
```typescript
  const openTicketingWindow = async () => {
    if (!room.eventId) return;
    let pid = room.participantId;
    if (!pid) {
      try {
        const res = await room.join(randomGuestName());
        if (res) {
          pid = res.participantId;
        }
      } catch (err) {
        console.error(err);
        return;
      }
    }
    if (!pid) return;
    const url = `/ticketing/${room.eventId}?participantId=${pid}`;
    const win = window.open(url, 'TimedealTicketingWindow', 'width=900,height=700,status=no,menubar=no,toolbar=no');
    if (win) {
      win.focus();
    }
  };
```

- [ ] **Step 2: Update TicketingWindow.tsx to read query parameter and forbid auto-joining**

Modify `initSession` in `TicketingWindow.tsx` to read the query parameter, disable `autoJoinAndQueue`, and set an error message if the participant ID is invalid/missing.
```typescript
    const urlParams = new URLSearchParams(window.location.search);
    const queryParticipantId = urlParams.get('participantId');
    const storedId = queryParticipantId || localStorage.getItem('timedeal.participantId');
```
And replace `autoJoinAndQueue` fallback:
```typescript
    const autoJoinAndQueue = async () => {
      if (!isMountedRef.current) return;
      setError('세션이 존재하지 않거나 만료되었습니다. 대시보드 화면에서 "예약하기" 버튼을 다시 눌러주세요.');
      setLoading(false);
    };
```

- [ ] **Step 3: Run frontend lint & build to verify changes**

Run command: `npm run build` in the `frontend` directory.
Expected output: BUILD SUCCESSFUL (No compilation/typescript errors).

- [ ] **Step 4: Commit UI components updates**

```bash
git add frontend/src/Dashboard.tsx frontend/src/components/TicketingWindow.tsx
git commit -m "feat: force session creation on dashboard and reuse in ticketing popup"
```
