# Admin Dashboard Layout Redesign & SSE Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the dashboard layout by separating the metrics/seat-map from detailed monitoring logs into dedicated routes (`/` and `/monitoring`), and optimize the SSE payload by lazy loading log timelines on-demand via a new API endpoint.

**Architecture:** 
1. In the backend, strip individual participant log timelines from the main SSE broadcast, reducing snapshot sizes.
2. Expose a new REST API endpoint to fetch detailed timelines for a participant.
3. In the frontend, set up `/monitoring` route in App.tsx and load/render participant activity lists and logs on-demand.

**Tech Stack:** Java, Spring Boot, React, React Router, SSE, REST API

---

### Task 1: Backend SSE Optimization & Timeline API

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventHub.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`

- [ ] **Step 1: Modify SimulationEventHub.java to strip timelines from snapshot broadcast**

In `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventHub.java` (around line 186):
Before serialization, map the users inside the snapshot and set their timeline to an empty list.

```java
        List<VirtualUserView> strippedUsers = snapshot.users().stream()
                .map(u -> new VirtualUserView(
                        u.id(), u.displayName(), u.type(), u.status(), u.selectedSeatLabel(),
                        List.of(), // strip timeline!
                        u.seatAttemptCount(), u.conflictCount(), u.paymentAttemptCount(),
                        u.reservationId(), u.seatHoldExpiresAt()
                )).toList();
        SimulationSnapshot strippedSnapshot = new SimulationSnapshot(
                snapshot.simulationId(),
                snapshot.seats(),
                strippedUsers,
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running()
        );
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(strippedSnapshot);
        } catch (JsonProcessingException e) {
```

- [ ] **Step 2: Add getParticipantTimeline in LiveEventService.java**

In `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`:
Add the public method to fetch the timeline.

```java
    public List<TimelineEntry> getParticipantTimeline(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        VirtualUserView participant = stateGateway.participant(eventId, participantId);
        return participant != null ? participant.timeline() : List.of();
    }
```

- [ ] **Step 3: Add endpoint in LiveEventController.java**

In `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`:
Expose `GET /api/events/{eventId}/participants/{participantId}/timeline`.

```java
    @GetMapping("/{eventId}/participants/{participantId}/timeline")
    public List<TimelineEntry> getParticipantTimeline(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        return liveEventService.getParticipantTimeline(eventId, participantId);
    }
```

- [ ] **Step 4: Add test in LiveEventControllerTest.java**

Add a test in `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java` to verify the new endpoint:

```java
    @Test
    void getParticipantTimelineReturnsList() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(liveEventService.getParticipantTimeline(eq(eventId), eq(participantId)))
                .thenReturn(List.of(new TimelineEntry("THINKING", "탐색 중")));

        mockMvc.perform(get("/api/events/" + eventId + "/participants/" + participantId + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("THINKING"))
                .andExpect(jsonPath("$[0].message").value("탐색 중"));
    }
```

- [ ] **Step 5: Run tests and verify**

Run: `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew test --tests "com.timedeal.seatreservation.event.LiveEventControllerTest"`
Expected: Build Successful, tests passing.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventHub.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java
git commit -m "backend: strip timelines from snapshot broadcast and add REST timeline endpoint"
```

---

### Task 2: Frontend API Client Update

**Files:**
- Modify: `frontend/src/api/liveEventApi.ts`

- [ ] **Step 1: Add fetchParticipantTimeline in liveEventApi.ts**

In `frontend/src/api/liveEventApi.ts`, add the timeline entry interface and the fetch function:

```typescript
export interface TimelineEntry {
  label: string;
  message: string;
}

export async function fetchParticipantTimeline(
  baseUrl: string,
  eventId: string,
  participantId: string
): Promise<TimelineEntry[]> {
  const url = `${baseUrl}/api/events/${eventId}/participants/${participantId}/timeline`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error('Failed to fetch participant timeline');
  }
  return res.json();
}
```

- [ ] **Step 2: Verify frontend compilation**

Run: `npx tsc --noEmit` in the `frontend` folder.
Expected: Compilation successful.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/liveEventApi.ts
git commit -m "frontend: add fetchParticipantTimeline API client"
```

---

### Task 3: Setup Router and Monitoring View

**Files:**
- Create: `frontend/src/components/MonitoringConsole.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/Dashboard.tsx`
- Modify: `frontend/src/components/EventHeader.tsx`

- [ ] **Step 1: Create MonitoringConsole.tsx**

Create `frontend/src/components/MonitoringConsole.tsx` which renders `QueuePanel` and `EventActivityPanel`. It should:
1. Call `useLiveEventRoom(apiBaseUrl)` to subscribe to the event snapshot.
2. Integrate the new on-demand `fetchParticipantTimeline` client API inside a `useEffect` trigger when `selectedParticipantId` changes.

```typescript
import { useState, useEffect } from 'react';
import { useLiveEventRoom } from '../hooks/useLiveEventRoom';
import { QueuePanel } from './QueuePanel';
import { EventActivityPanel } from './EventActivityPanel';
import { EventHeader } from './EventHeader';
import { fetchParticipantTimeline, type TimelineEntry } from '../api/liveEventApi';

const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_BASE_URL) return import.meta.env.VITE_API_BASE_URL;
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.startsWith('192.168.')) {
      return 'https://ticket-api.chocojipsa.blog';
    }
  }
  return '';
};
const apiBaseUrl = getApiBaseUrl();

export function MonitoringConsole() {
  const room = useLiveEventRoom(apiBaseUrl);
  const [selectedParticipantId, setSelectedParticipantId] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);

  useEffect(() => {
    if (room.snapshot && selectedParticipantId) {
      fetchParticipantTimeline(apiBaseUrl, room.snapshot.eventId, selectedParticipantId)
        .then(setTimeline)
        .catch(console.error);
    }
  }, [selectedParticipantId, room.snapshot]);

  if (!room.snapshot) {
    return (
      <main className="dashboard">
        <section className="panel empty-state">
          <h1>이벤트를 불러오는 중입니다...</h1>
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <EventHeader snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />
      <div className="dashboard-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '20px', marginTop: '20px' }}>
        <QueuePanel
          snapshot={room.snapshot}
          participantId={room.participantId}
          selectedParticipantId={selectedParticipantId}
          onSelectParticipant={setSelectedParticipantId}
        />
        <EventActivityPanel
          snapshot={room.snapshot}
          participantId={room.participantId}
          selectedParticipantId={selectedParticipantId}
          apiBaseUrl={apiBaseUrl}
        />
      </div>
    </main>
  );
}
```

- [ ] **Step 2: Update App.tsx with /monitoring Route**

In `frontend/src/App.tsx`, import `MonitoringConsole` and declare the new route:

```typescript
import { Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './Dashboard';
import { TicketingWindow } from './components/TicketingWindow';
import { MonitoringConsole } from './components/MonitoringConsole';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/monitoring" element={<MonitoringConsole />} />
      <Route path="/ticketing/:eventId" element={<TicketingWindow />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
```

- [ ] **Step 3: Update Dashboard.tsx to remove logs and participant list**

In `frontend/src/Dashboard.tsx`, remove `QueuePanel` and `EventActivityPanel`. The layout should only have `MyTicketPanel`, `SeatMap`, and `InsightPanel`.

- [ ] **Step 4: Update EventHeader.tsx to add Navigation Links**

In `frontend/src/components/EventHeader.tsx`, add a navigation link near the title block to switch between Dashboard (`/`) and Monitoring Console (`/monitoring`).

```typescript
import { Link, useLocation } from 'react-router-dom';
...
      <div className="event-title-block">
        <span className="eyebrow">LIVE CONSOLE</span>
        <h1>{snapshot.title}</h1>
        <div style={{ display: 'flex', gap: '10px', marginTop: '5px' }}>
          <Link to="/" style={{ color: useLocation().pathname === '/' ? 'var(--mint)' : '#fff', textDecoration: 'none', fontWeight: 'bold' }}>Dashboard</Link>
          <Link to="/monitoring" style={{ color: useLocation().pathname === '/monitoring' ? 'var(--mint)' : '#fff', textDecoration: 'none', fontWeight: 'bold' }}>Monitoring</Link>
        </div>
      </div>
```

- [ ] **Step 5: Verify compilation**

Run: `npx tsc --noEmit` in the `frontend` folder.
Expected: Compilation successful.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/MonitoringConsole.tsx frontend/src/App.tsx frontend/src/Dashboard.tsx frontend/src/components/EventHeader.tsx
git commit -m "frontend: set up /monitoring console route, link it in header, and optimize dashboard grid layout"
```
