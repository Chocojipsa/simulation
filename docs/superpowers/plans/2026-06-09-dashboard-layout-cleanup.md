# Dashboard Layout Cleanup & Centered Seat Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up the main dashboard page layout by making the Seat Map the center-stage hero component (full width), reducing the top metrics to 4 core items, and simplifying the bottom insight panels into a clean 2-column layout.

**Architecture:** 
1. In `Dashboard.tsx`, update the `metric-strip` to show 4 items (Seats, Queue, TPS, and Active Users). Move `SeatMap` above the `dashboard-grid` so it occupies full width.
2. In `InsightPanel.tsx`, remove the `PostgreSQL 좌석 선점` card and group the remaining metrics into 2 columns.
3. Update and run frontend unit tests in `App.test.tsx` and `EventActivityPanel.test.tsx` to verify correct rendering and behavior.

**Tech Stack:** React, TypeScript, Vitest, CSS Grid

---

### Task 1: Update Dashboard Layout & Top Metrics Strip

**Files:**
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Replace metric strip and layout grid in Dashboard.tsx**

In `frontend/src/Dashboard.tsx`, replace the old `metric-strip` (lines 72-81) and layout grid (lines 82-99) with the simplified 4-metric strip and hero Seat Map layout:

```tsx
      <div className="metric-strip" aria-label="실시간 이벤트 지표">
        <Metric label="SEATS" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="reserved" />
        <Metric label="QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="waiting" />
        <Metric label="TPS" value={`${metrics ? metrics.tps.toFixed(1) : '0.0'}`} detail="transactions/s" />
        <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="connected" />
      </div>
      <div style={{ marginBottom: '16px' }}>
        <SeatMap
          status={room.snapshot.status}
          seats={room.snapshot.seats}
          participant={room.myParticipant}
          selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
          onSelectSeat={(seatId) => void room.selectSeat(seatId)}
          readOnly={true}
        />
      </div>
      <div className="dashboard-grid" style={{ gridTemplateColumns: 'minmax(230px, 280px) 1fr', gap: '16px' }}>
        <MyTicketPanel
          status={room.snapshot.status}
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join(randomGuestName())}
          onReserve={openTicketingWindow}
          onPay={() => void room.pay()}
        />
        <InsightPanel snapshot={room.snapshot} metrics={metrics} />
      </div>
```

- [ ] **Step 2: Verify TypeScript Compilation**

Run: `npx tsc --noEmit` inside `frontend` folder
Expected: Compile successful with 0 errors.

- [ ] **Step 3: Commit changes**

```bash
git add frontend/src/Dashboard.tsx
git commit -m "frontend: reorganize dashboard layout to center seatmap and reduce top metrics to 4"
```

---

### Task 2: Simplify Insight Panel

**Files:**
- Modify: `frontend/src/components/InsightPanel.tsx`

- [ ] **Step 1: Simplify cards inside InsightPanel.tsx**

In `frontend/src/components/InsightPanel.tsx` (lines 10-50), simplify the 5 cards into a clean 2-column layout and remove the PostgreSQL seat count card:

```tsx
export function InsightPanel({ snapshot, metrics }: InsightPanelProps) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
      <section className="panel">
        <h2>서버 분산</h2>
        {(metrics ? metrics.serverStats : snapshot.serverStats).map((stats) => (
          <div className="metric-row" key={stats.serverId}>
            <span>{stats.serverId}</span>
            <strong>{stats.requestCount}</strong>
            <small>충돌 {stats.conflictCount} · 성공 {stats.successCount}</small>
          </div>
        ))}
      </section>
      
      <section className="panel">
        <h2>시스템 및 인프라 상태</h2>
        <div className="metric-row"><span>평균 응답 속도</span><strong>{metrics ? Math.round(metrics.avgResponseTimeMs) : 0}ms</strong></div>
        <div className="metric-row"><span>Kafka Lag</span><strong>{metrics ? metrics.kafkaLag : 0} messages</strong></div>
        <div className="metric-row"><span>Redis Active Locks</span><strong>{metrics ? metrics.redisLockCount : 0} locks</strong></div>
      </section>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript Compilation**

Run: `npx tsc --noEmit` inside `frontend` folder
Expected: Compile successful with 0 errors.

- [ ] **Step 3: Commit changes**

```bash
git add frontend/src/components/InsightPanel.tsx
git commit -m "frontend: simplify insight panel to 2 columns and remove PostgreSQL card"
```

---

### Task 3: Update Frontend Tests & Verify Build

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/components/EventActivityPanel.test.tsx`

- [ ] **Step 1: Update App.test.tsx with split dashboard and monitoring tests**

In `frontend/src/App.test.tsx` (lines 39-60), replace the old test case with separate assertions for `/` (Dashboard) and `/monitoring` (Monitoring Console):

```typescript
describe('App', () => {
  it('shows the start action prominently on the dashboard before the event starts', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /티켓팅/ })).toBeInTheDocument();
    expect(screen.getByText('LIVE CONSOLE')).toBeInTheDocument();
    expect(screen.getByText('SEATS')).toBeInTheDocument();
    expect(screen.getAllByText('QUEUE').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('시작 전')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이벤트 시작하기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '예약하기' })).toBeDisabled();
    expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
    expect(screen.getByText('예매가 아직 시작되지 않았습니다.')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
  });

  it('shows the monitoring console route with active participant panels', () => {
    render(
      <MemoryRouter initialEntries={['/monitoring']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /티켓팅/ })).toBeInTheDocument();
    expect(screen.getByText('LIVE CONSOLE')).toBeInTheDocument();
    expect(screen.getByText('시작 전')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이벤트 시작하기' })).toBeInTheDocument();
    expect(screen.getByText('참가자 현황')).toBeInTheDocument();
    expect(screen.getByText('시스템 알림')).toBeInTheDocument();
    expect(screen.getByText('네트워크 최적화 활성화됨')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Update EventActivityPanel.test.tsx with mock API client**

In `frontend/src/components/EventActivityPanel.test.tsx` (lines 1-58), mock `fetchParticipantTimeline` and make the test async using `findAllByText`:

```typescript
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { EventActivityPanel } from './EventActivityPanel';

vi.mock('../api/liveEventApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/liveEventApi')>();
  return {
    ...actual,
    fetchParticipantTimeline: vi.fn().mockResolvedValue([
      { label: '대기열 통과', message: '좌석을 선택해 주세요.' }
    ]),
  };
});

describe('EventActivityPanel', () => {
  it('separates my progress from the full event log', async () => {
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      generation: 1,
      opensAt: '2026-05-28T12:00:00Z',
      endsAt: '2026-05-28T12:05:00Z',
      seats: [],
      metrics: { queueSize: 0, admittedCount: 2, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 3, conflictCount: 0, successCount: 2 }],
      running: true,
      myParticipantId: 'me',
      myQueuePosition: null,
      participants: [
        {
          id: 'me',
          displayName: '나',
          type: 'HUMAN',
          status: 'SELECTING_SEAT',
          selectedSeatLabel: null,
          timeline: [{ label: '대기열 통과', message: '좌석을 선택해 주세요.' }],
          seatAttemptCount: 0,
          conflictCount: 0,
          paymentAttemptCount: 0,
          reservationId: null,
        },
        {
          id: 'ai-1',
          displayName: 'AI-1',
          type: 'AI',
          status: 'RESERVED',
          selectedSeatLabel: 'B-2',
          timeline: [{ label: '결제 성공', message: '예매 완료' }],
          seatAttemptCount: 1,
          conflictCount: 0,
          paymentAttemptCount: 1,
          reservationId: 102,
        },
      ],
    };

    render(<EventActivityPanel snapshot={snapshot} participantId="me" />);

    expect(screen.getByText('내 진행')).toBeInTheDocument();
    expect(screen.getByText('시스템 알림')).toBeInTheDocument();
    expect(screen.getByText('네트워크 최적화 활성화됨')).toBeInTheDocument();
    expect(await screen.findAllByText('좌석을 선택해 주세요.')).toHaveLength(1);
  });
});
```

- [ ] **Step 3: Run Frontend Unit Tests**

Run: `npm test` inside `frontend` folder
Expected: 11 test files passed, 24 tests passed.

- [ ] **Step 4: Verify Production Build**

Run: `npm run build` inside `frontend` folder
Expected: Build succeeds with 0 errors.

- [ ] **Step 5: Commit changes**

```bash
git add frontend/src/App.test.tsx frontend/src/components/EventActivityPanel.test.tsx
git commit -m "test: update frontend tests to align with redesigned dashboard layout and optimized logs"
```
