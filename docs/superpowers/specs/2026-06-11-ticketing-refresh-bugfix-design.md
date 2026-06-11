# Ticketing Auto-Refresh & SSE Admission Bugfix Design Specification

**Goal:** Fix the issue where seats are unclickable immediately after being admitted from the waiting queue, and enable auto-refresh by default in the ticketing window.

## Bug Analysis
1. **SSE Queue Admission State Inconsistency:**
   When the Server-Sent Event (SSE) listener receives the `queue_admitted` activity label, it immediately calls `setStep(3)` to show the seat selection map. However, the local React `snapshot` state is not updated with the participant's new status (`SELECTING_SEAT`).
   As a result, the `canSelectSeat` selector continues to check the old cached status (`QUEUED`) and disables seat selection, making the seat map unclickable until a status poll or manual refresh updates the snapshot.
2. **Auto-refresh State Initialization:**
   The `autoRefresh` state is initialized to `false` in `TicketingWindow.tsx`, requiring users to manually toggle it.

## Design Changes

### 1. Fetch Snapshot on SSE Admission
In `frontend/src/components/TicketingWindow.tsx`, update the `queue_admitted` SSE event handler to fetch the latest event snapshot using `fetchEventSnapshot` before changing the step:
```typescript
} else if (data.label === 'queue_admitted') {
  // Fetch latest snapshot to update participant status in React state
  fetchEventSnapshot(apiBaseUrl, eventId, participantId).then((snap) => {
    if (active) {
      setSnapshot(snap);
      setStep(3);
    }
  }).catch((err) => {
    console.error('Failed to fetch snapshot on queue admission:', err);
    if (active) setStep(3); // Fallback to transition step
  });
  if (eventSource) {
    eventSource.close();
  }
}
```

### 2. Enable Auto-Refresh by Default
In `frontend/src/components/TicketingWindow.tsx`, change the initial state value of `autoRefresh` to `true`:
```typescript
const [autoRefresh, setAutoRefresh] = useState(true);
```
