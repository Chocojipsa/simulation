# Sidebar Event Controller Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant top-bar and consolidate the event status/control panel vertically inside the left sidebar.

**Architecture:** Create a reusable `Sidebar.tsx` component that houses the branding, navigation, status badge, simulation configuration fields (with tab caching), and controls. Replace the top-bar layout with this sidebar, center the main content (`max-width: 1400px`), and delete `EventHeader.tsx`.

**Tech Stack:** React, TypeScript, Vitest, Vanilla CSS

---

### Task 1: Create Unit Tests for the Sidebar Component
**Files:**
- Create: `frontend/src/components/Sidebar.test.tsx`

- [ ] **Step 1: Write the tests**
Create a new test file `frontend/src/components/Sidebar.test.tsx` with the following content:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import type { LiveEventSnapshot } from '../api/liveEventApi';

const mockSnapshot = (status: 'READY' | 'OPEN' | 'ENDED'): LiveEventSnapshot => ({
  eventId: 'event-1',
  title: '시뮬레이션',
  status,
  generation: 1,
  opensAt: null,
  endsAt: null,
  seats: [],
  participants: [],
  metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
  serverStats: [],
  running: false,
  myParticipantId: null,
  myQueuePosition: null,
});

describe('Sidebar', () => {
  it('renders branding and nav links', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={null} />
      </MemoryRouter>
    );
    expect(screen.getByText('TIMEDEAL')).toBeInTheDocument();
    expect(screen.getByText('대시보드')).toBeInTheDocument();
    expect(screen.getByText('모니터링 콘솔')).toBeInTheDocument();
  });

  it('renders simulation form when status is READY', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={mockSnapshot('READY')} />
      </MemoryRouter>
    );
    expect(screen.getByLabelText('AI 유저 수')).toBeInTheDocument();
    expect(screen.getByLabelText('동시 인입 수')).toBeInTheDocument();
    expect(screen.getByLabelText('행동 속도')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '시뮬레이션 시작' })).toBeInTheDocument();
  });

  it('renders reset button when status is ENDED', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={mockSnapshot('ENDED')} />
      </MemoryRouter>
    );
    expect(screen.getByRole('button', { name: '새 이벤트 시작' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**
Run: `npm test` inside `frontend` directory.
Expected: FAIL with "Failed to resolve import ./Sidebar"

- [ ] **Step 3: Commit**
```bash
git add frontend/src/components/Sidebar.test.tsx
git commit -m "test: add unit tests for new Sidebar component"
```

### Task 2: Implement the Reusable Sidebar Component
**Files:**
- Create: `frontend/src/components/Sidebar.tsx`

- [ ] **Step 1: Write the implementation**
Create `frontend/src/components/Sidebar.tsx` with tab-caching variable state and status controllers:

```tsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

// Module-level cache variables to preserve values across tab transitions
let cachedAiCount = 150;
let cachedAiConcurrency = 50;
let cachedAiSpeed: 'SLOW' | 'NORMAL' | 'FAST' = 'NORMAL';

interface SidebarProps {
  activeTab: 'dashboard' | 'monitoring';
  snapshot: LiveEventSnapshot | null;
  onStart?: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
  onReset?: () => void;
}

export function Sidebar({ activeTab, snapshot, onStart, onReset }: SidebarProps) {
  const [now, setNow] = useState(() => new Date());
  const [aiCount, setAiCount] = useState(cachedAiCount);
  const [aiConcurrency, setAiConcurrency] = useState(cachedAiConcurrency);
  const [aiSpeed, setAiSpeed] = useState(cachedAiSpeed);

  // Sync state changes with module cache variables
  useEffect(() => { cachedAiCount = aiCount; }, [aiCount]);
  useEffect(() => { cachedAiConcurrency = aiConcurrency; }, [aiConcurrency]);
  useEffect(() => { cachedAiSpeed = aiSpeed; }, [aiSpeed]);

  // Update time label every second when running
  useEffect(() => {
    if (!snapshot || (snapshot.status !== 'COUNTDOWN' && snapshot.status !== 'OPEN')) return undefined;
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, [snapshot?.status]);

  return (
    <aside className="sidebar">
      {/* Brand logo */}
      <div className="sidebar-brand">
        <span className="brand-logo">⏱️</span>
        <span className="brand-text">TIMEDEAL</span>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        <Link to="/" className={`sidebar-link ${activeTab === 'dashboard' ? 'active' : ''}`}>
          <span className="link-icon">D</span>
          <span className="link-text">대시보드</span>
        </Link>
        <Link to="/monitoring" className={`sidebar-link ${activeTab === 'monitoring' ? 'active' : ''}`}>
          <span className="link-icon">M</span>
          <span className="link-text">모니터링 콘솔</span>
        </Link>
      </nav>

      {/* Event controls at the bottom */}
      {snapshot && (
        <div className="sidebar-control-panel">
          <div className="sidebar-divider"></div>
          
          <div className="control-status">
            <span className="status-badge">{formatEventStatus(snapshot.status)}</span>
            <span className="status-time">{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
          </div>

          {snapshot.status === 'READY' && (
            <div className="control-form">
              <div className="control-field">
                <label htmlFor="sidebar-ai-count">AI 유저 수</label>
                <input
                  id="sidebar-ai-count"
                  type="number"
                  min={0}
                  max={1000}
                  value={aiCount}
                  onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                />
              </div>
              <div className="control-field">
                <label htmlFor="sidebar-ai-concurrency">동시 인입 수</label>
                <input
                  id="sidebar-ai-concurrency"
                  type="number"
                  min={1}
                  max={120}
                  value={aiConcurrency}
                  onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                />
              </div>
              <div className="control-field">
                <label htmlFor="sidebar-ai-speed">행동 속도</label>
                <select
                  id="sidebar-ai-speed"
                  value={aiSpeed}
                  onChange={(e) => setAiSpeed(e.target.value as any)}
                >
                  <option value="SLOW">느림 (1.5초)</option>
                  <option value="NORMAL">보통 (0.5초)</option>
                  <option value="FAST">빠름 (0.1초)</option>
                </select>
              </div>
              <button
                type="button"
                className="btn btn-primary control-btn"
                onClick={() => onStart?.({ aiUserCount: aiCount, aiConcurrency, aiSpeed })}
              >
                시뮬레이션 시작
              </button>
            </div>
          )}

          {snapshot.status === 'ENDED' && (
            <button
              type="button"
              className="btn btn-primary control-btn"
              onClick={onReset}
            >
              새 이벤트 시작
            </button>
          )}
        </div>
      )}
    </aside>
  );
}
```

- [ ] **Step 2: Run test to verify it passes**
Run: `npm test` inside `frontend` directory.
Expected: `Sidebar.test.tsx` PASS.

- [ ] **Step 3: Commit**
```bash
git add frontend/src/components/Sidebar.tsx
git commit -m "feat: implement reusable brand Sidebar component with bottom control panel"
```

### Task 3: Add CSS Styles for Sidebar Controls
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Append styling rules**
Append CSS rules for the sidebar control panel at the end of `frontend/src/styles.css`:

```css
/* 사이드바 하단 제어 패널 */
.sidebar-control-panel {
  margin-top: auto;
  width: 100%;
  padding: 0 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-divider {
  height: 1px;
  background: var(--border-line);
  width: 100%;
  margin-bottom: 8px;
}

.control-status {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.control-status .status-badge {
  font-size: 13px;
  color: var(--primary-indigo);
  background: rgba(79, 70, 229, 0.08);
  padding: 4px 10px;
  border-radius: 9999px;
  font-weight: 700;
  display: inline-block;
  align-self: flex-start;
}

.control-status .status-time {
  font-size: 11px;
  color: var(--text-secondary);
}

.control-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.control-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.control-field label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
}

.control-field input, .control-field select {
  padding: 8px 10px;
  border: 1px solid var(--border-line);
  border-radius: var(--radius-md);
  font-size: 12px;
  width: 100%;
  background-color: var(--bg-card);
  color: var(--text-primary);
  outline: none;
}

.control-field input:focus, .control-field select:focus {
  border-color: var(--primary-indigo);
}

.control-btn {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  margin-top: 8px;
}
```

- [ ] **Step 2: Commit**
```bash
git add frontend/src/styles.css
git commit -m "style: add CSS styles for sidebar event control panel"
```

### Task 4: Integrate Sidebar Component and Clean Up old Layouts
**Files:**
- Modify: `frontend/src/Dashboard.tsx`
- Modify: `frontend/src/components/MonitoringConsole.tsx`
- Delete: `frontend/src/components/EventHeader.tsx`

- [ ] **Step 1: Integrate Sidebar and remove EventHeader in Dashboard.tsx**
Modify `frontend/src/Dashboard.tsx` to:
1. Import `Sidebar` from `./components/Sidebar` instead of manual sidebar structure and `EventHeader`.
2. Replace `<aside className="sidebar">` and `<EventHeader ... />` with `<Sidebar activeTab="dashboard" snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />` in both return cases.

```tsx
// Around imports:
import { Sidebar } from './components/Sidebar';
// ... remove import { EventHeader } from './components/EventHeader';

// In first return:
  if (!room.snapshot) {
    return (
      <div className="dashboard-container">
        <Sidebar activeTab="dashboard" snapshot={null} />
        <main className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <section className="panel empty-state">
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1>예매 이벤트를 불러오는 중입니다</h1>
            {room.error ? <p>{room.error}</p> : null}
          </section>
        </main>
      </div>
    );
  }

// In main return:
  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="dashboard"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <main className="main-content">
        {room.error ? <div className="error-banner">{room.error}</div> : null}
        {room.message ? <div className="info-banner">{room.message}</div> : null}
        
        <div className="metric-strip" aria-label="실시간 이벤트 지표">
```

- [ ] **Step 2: Integrate Sidebar and remove EventHeader in MonitoringConsole.tsx**
Modify `frontend/src/components/MonitoringConsole.tsx` to:
1. Import `Sidebar` from `./Sidebar` instead of manual sidebar structure and `EventHeader`.
2. Replace `<aside className="sidebar">` and `<EventHeader ... />` with `<Sidebar activeTab="monitoring" snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />` in both return cases.

```tsx
// Around imports:
import { Sidebar } from './Sidebar';
// ... remove import { EventHeader } from './EventHeader';

// In first return:
  if (!room.snapshot) {
    return (
      <div className="dashboard-container">
        <Sidebar activeTab="monitoring" snapshot={null} />
        <main className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <section className="panel empty-state">
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1>이벤트를 불러오는 중입니다...</h1>
            {room.error ? <p>{room.error}</p> : null}
          </section>
        </main>
      </div>
    );
  }

// In main return:
  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="monitoring"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <main className="main-content">
        {room.error ? <div className="error-banner">{room.error}</div> : null}
        {room.message ? <div className="info-banner">{room.message}</div> : null}
        
        <div className="dashboard-grid">
```

- [ ] **Step 3: Delete EventHeader.tsx**
Remove the file `frontend/src/components/EventHeader.tsx`.

- [ ] **Step 4: Run tests and verify build**
Run: `npm test` inside `frontend` directory (all tests must PASS).
Run: `npm run build` inside `frontend` directory (compilation must succeed).

- [ ] **Step 5: Commit**
```bash
rm frontend/src/components/EventHeader.tsx
git add frontend/src/Dashboard.tsx frontend/src/components/MonitoringConsole.tsx
git rm frontend/src/components/EventHeader.tsx
git commit -m "refactor: integrate shared Sidebar component, remove EventHeader top-bar, and clean up"
```
