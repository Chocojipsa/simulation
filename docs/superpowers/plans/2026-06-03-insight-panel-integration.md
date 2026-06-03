# Insight Panel Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate `InsightPanel` into the live event dashboard (`App.tsx`) to show infrastructure metrics using a 3-column layout.

**Architecture:** We will modify `InsightPanel.tsx` to accept `LiveEventSnapshot` (which is backwards compatible since they share `metrics`, `serverStats`, and `seats`), and then place `<InsightPanel snapshot={room.snapshot} />` as the first child of `.dashboard-grid` in `App.tsx`.

**Tech Stack:** React, TypeScript, Vitest

---

### Task 1: Update InsightPanel to accept LiveEventSnapshot

**Files:**
- Modify: `src/components/InsightPanel.tsx`

- [ ] **Step 1: Write the failing test for InsightPanel**

First, create a test file `src/components/InsightPanel.test.tsx` to verify that `InsightPanel` renders correctly with `LiveEventSnapshot`.

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { InsightPanel } from './InsightPanel';

describe('InsightPanel', () => {
  it('renders server stats and metrics from LiveEventSnapshot', () => {
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: 'Test Event',
      status: 'OPEN',
      generation: 1,
      opensAt: null,
      endsAt: null,
      seats: [
        { id: 1, label: 'A-1', status: 'AVAILABLE', version: 1 },
        { id: 2, label: 'A-2', status: 'RESERVED', version: 1 },
      ],
      participants: [],
      metrics: { queueSize: 10, admittedCount: 5, heldCount: 0, paymentInProgressCount: 2, reservedCount: 1, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 100, conflictCount: 5, successCount: 95 }],
      running: true,
      myParticipantId: null,
      myQueuePosition: null,
    };

    render(<InsightPanel snapshot={snapshot} />);

    expect(screen.getByText('api-a')).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument(); // queueSize
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test`
Expected: FAIL because `InsightPanel.tsx` imports and types `snapshot` as `SimulationSnapshot`, which throws type errors when used with `LiveEventSnapshot` in some TS configurations, or test passes immediately if types are structurally equivalent. Actually, `SimulationSnapshot` vs `LiveEventSnapshot` only differ in user/participant fields, but it's best to fix the imports. Wait, if it passes, we still need to update the prop types.

- [ ] **Step 3: Write minimal implementation**

Update `src/components/InsightPanel.tsx`:

```tsx
import type { LiveEventSnapshot } from '../api/liveEventApi';
import type { SimulationSnapshot } from '../api/simulationApi';
import { countSeatsByStatus } from '../domain/simulationSelectors';

interface InsightPanelProps {
  snapshot: SimulationSnapshot | LiveEventSnapshot;
}

export function InsightPanel({ snapshot }: InsightPanelProps) {
  const seatCounts = countSeatsByStatus(snapshot.seats);

  return (
    <aside className="insight-column">
      <section className="panel">
        <h2>서버 분산</h2>
        {snapshot.serverStats.map((stats) => (
          <div className="metric-row" key={stats.serverId}>
            <span>{stats.serverId}</span>
            <strong>{stats.requestCount}</strong>
            <small>충돌 {stats.conflictCount} · 성공 {stats.successCount}</small>
          </div>
        ))}
      </section>
      <section className="panel">
        <h2>Redis 대기열</h2>
        <div className="metric-row"><span>대기</span><strong>{snapshot.metrics.queueSize}</strong></div>
        <div className="metric-row"><span>입장</span><strong>{snapshot.metrics.admittedCount}</strong></div>
      </section>
      <section className="panel">
        <h2>Kafka 결제</h2>
        <div className="metric-row"><span>결제 중</span><strong>{snapshot.metrics.paymentInProgressCount}</strong></div>
        <div className="metric-row"><span>예약 완료</span><strong>{snapshot.metrics.reservedCount}</strong></div>
        <div className="metric-row"><span>실패</span><strong>{snapshot.metrics.failedCount}</strong></div>
      </section>
      <section className="panel">
        <h2>PostgreSQL 좌석 선점</h2>
        <div className="metric-row"><span>가능</span><strong>{seatCounts.AVAILABLE}</strong></div>
        <div className="metric-row"><span>예약</span><strong>{seatCounts.RESERVED}</strong></div>
      </section>
    </aside>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/InsightPanel.tsx src/components/InsightPanel.test.tsx
git commit -m "feat: make InsightPanel accept LiveEventSnapshot"
```

---

### Task 2: Integrate InsightPanel into App.tsx

**Files:**
- Modify: `src/App.tsx`
- Test: `src/App.test.tsx`

- [ ] **Step 1: Write the failing test**

Update `src/App.test.tsx` to assert that `InsightPanel` is rendered within the dashboard. Find the test for the dashboard rendering and add an assertion.

```tsx
// In src/App.test.tsx
// Find the relevant test rendering `<App />` and add:
// expect(screen.getByText('서버 분산')).toBeInTheDocument();
```
(If it's easier, write a quick test check verifying `InsightPanel` text "서버 분산").

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test`
Expected: FAIL because `InsightPanel` is not rendered in `App.tsx`.

- [ ] **Step 3: Write minimal implementation**

Update `src/App.tsx` to import and place `InsightPanel`:

```tsx
// 1. Add import:
import { InsightPanel } from './components/InsightPanel';

// 2. Add <InsightPanel snapshot={room.snapshot} /> as the FIRST child of dashboard-grid
      <div className="dashboard-grid">
        <InsightPanel snapshot={room.snapshot} />
        <MyTicketPanel
          status={room.snapshot.status}
          participant={room.myParticipant}
          loading={room.loading}
// ...
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/App.tsx src/App.test.tsx
git commit -m "feat: integrate InsightPanel into the live event dashboard"
```
