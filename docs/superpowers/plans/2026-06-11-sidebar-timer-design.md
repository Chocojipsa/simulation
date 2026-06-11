# Sidebar Timer Card Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the sidebar event status indicator and time label to be centered, color-coded, and highly visible using a card-style digital clock timer.

**Architecture:** Update `Sidebar.tsx` to group status and timer details into a `.status-timer-card` layout. Add styles in `styles.css` for color-coded status badges (`.status-ready`, `.status-countdown`, `.status-open`, `.status-ended`) and the digital timer text.

**Tech Stack:** React, TypeScript, Vitest, Vanilla CSS

---

### Task 1: Update Sidebar Component to use Centered Timer Card
**Files:**
- Modify: `frontend/src/components/Sidebar.tsx`

- [ ] **Step 1: Write helper functions and update render output in Sidebar.tsx**
Modify `frontend/src/components/Sidebar.tsx`. Add helper functions to format the labels and time values. Group status/timer into the centered card:

```tsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus } from '../domain/liveEventSelectors';

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

function getTimerLabelText(status: string) {
  if (status === 'COUNTDOWN') return '오픈까지';
  if (status === 'OPEN') return '남은 시간';
  if (status === 'ENDED') return '이벤트 상태';
  return '이벤트 상태';
}

function getTimerValueText(status: string, opensAt: string | null, endsAt: string | null, now: Date) {
  const secondsUntil = (target: string) => {
    return Math.max(0, Math.ceil((new Date(target).getTime() - now.getTime()) / 1000));
  };
  if (status === 'COUNTDOWN' && opensAt) {
    return `${secondsUntil(opensAt)}초`;
  }
  if (status === 'OPEN' && endsAt) {
    return `${secondsUntil(endsAt)}초`;
  }
  if (status === 'ENDED') {
    return '종료됨';
  }
  return '대기 중';
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
            <span className={`status-badge status-${snapshot.status.toLowerCase()}`}>
              {snapshot.status === 'OPEN' && <span className="pulsing-dot-green"></span>}
              {formatEventStatus(snapshot.status)}
            </span>
            
            <div className="status-timer-card">
              <span className="timer-label">{getTimerLabelText(snapshot.status)}</span>
              <span className="timer-value">{getTimerValueText(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
              {(snapshot.status === 'OPEN' || snapshot.status === 'ENDED') && (
                <span className="timer-subtext">
                  예약 완료: {snapshot.metrics.reservedCount} / {snapshot.seats.length}
                </span>
              )}
            </div>
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
                이벤트 시작하기
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

- [ ] **Step 2: Run tests to verify they pass**
Run: `npm test` inside `frontend` directory.
Expected: Tests pass (they do not inspect classes, but checking buttons/links will pass).

- [ ] **Step 3: Commit**
```bash
git add frontend/src/components/Sidebar.tsx
git commit -m "feat: refactor Sidebar status panel to use centered timer card with progress subtext"
```

### Task 2: Implement CSS Styles for Status Badges and Timer Card
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Update styles.css**
Modify the CSS styling inside the `/* 사이드바 하단 제어 패널 */` block of `frontend/src/styles.css` to center the controls, style status colors, and define the timer card:

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
  align-items: center;
  gap: 8px;
  width: 100%;
}

.control-status .status-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 9999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* Color coding for event status badges */
.control-status .status-ready {
  color: #475569;
  background: #F1F5F9;
  border: 1px solid var(--border-line);
}

.control-status .status-countdown {
  color: #D97706;
  background: #FEF3C7;
  border: 1px solid #FDE68A;
}

.control-status .status-open {
  color: #047857;
  background: #D1FAE5;
  border: 1px solid #A7F3D0;
}

.control-status .status-ended {
  color: #B91C1C;
  background: #FEE2E2;
  border: 1px solid #FCA5A5;
}

.status-timer-card {
  background: #F8FAFC;
  border: 1px solid var(--border-line);
  border-radius: var(--radius-md);
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.02);
}

.timer-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 2px;
}

.timer-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--primary-indigo);
  font-family: monospace;
}

.timer-subtext {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
  font-weight: 500;
}

/* Pulsing dot for live status badge */
.pulsing-dot-green {
  width: 6px;
  height: 6px;
  background-color: var(--success-mint);
  border-radius: 50%;
  display: inline-block;
  margin-right: 6px;
  vertical-align: middle;
  animation: pulse-green 1.5s infinite cubic-bezier(0.66, 0, 0, 1);
}

@keyframes pulse-green {
  0% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  100% {
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0);
  }
}

.control-form {
...
```

- [ ] **Step 2: Run build to verify**
Run: `npm run build` inside `frontend` directory.
Expected: Build succeeds.

- [ ] **Step 3: Commit**
```bash
git add frontend/src/styles.css
git commit -m "style: implement centered status-timer card with color-coded status badges and pulsing dots"
```
