# 대시보드 및 헤더 UX 개선 구현 계획서 (Dashboard & Header UX Redesign Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이벤트 시작 컨트롤을 헤더 내의 절대 좌표 오버레이 드롭다운으로 구현하여, 대시보드 본문의 레이아웃 변화를 방지하고 깔끔한 관제 UI를 확보합니다.

**Architecture:**
1. `EventHeader.tsx`에서 AI 관련 설정 및 시작 폼을 지우고, 대신 절대 좌표를 쓰는 오버레이 드롭다운 설정 팝오버를 구현합니다.
2. `Dashboard.tsx` 본문 내의 AI 시뮬레이터 시작 카드를 제거하고, `<EventHeader>` 호출 시 `onStart` 프롭을 원복 전달합니다.
3. `MonitoringConsole.tsx` 내부의 `<EventHeader>` 호출부 역시 `onStart` 프롭을 넘겨주어 기능 호환성을 지원합니다.
4. 테스트 코드(`App.test.tsx`)를 수정하여 "이벤트 시작하기" 버튼 검출 등의 테스트 사양을 맞춥니다.

**Tech Stack:** React 18, TypeScript, CSS3, Vitest

---

### Task 1: EventHeader 컴포넌트의 설정 드롭다운 팝오버 구현

**Files:**
- Modify: `frontend/src/components/EventHeader.tsx`

- [ ] **Step 1: EventHeader.tsx 수정**
  AI 설정 전용 상태(`isSettingsOpen`, `aiCount`, `aiConcurrency`, `aiSpeed`)를 선언하고, `이벤트 시작하기` 버튼을 누르면 화면을 해치지 않는 절대 좌표 오버레이 드롭다운 설정 팝오버를 노출하도록 마크업을 구축합니다.

  *수정 후 코드:*
  ```tsx
  import { useEffect, useState } from 'react';
  import type { LiveEventSnapshot } from '../api/liveEventApi';
  import { Link, useLocation } from 'react-router-dom';
  import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

  interface EventHeaderProps {
    snapshot: LiveEventSnapshot;
    onStart: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
    onReset: () => void;
  }

  export function EventHeader({ snapshot, onStart, onReset }: EventHeaderProps) {
    const [now, setNow] = useState(() => new Date());
    const [isSettingsOpen, setIsSettingsOpen] = useState(false);
    const [aiCount, setAiCount] = useState<number>(150);
    const [aiConcurrency, setAiConcurrency] = useState<number>(50);
    const [aiSpeed, setAiSpeed] = useState<'SLOW' | 'NORMAL' | 'FAST'>('NORMAL');

    useEffect(() => {
      if (snapshot.status !== 'COUNTDOWN' && snapshot.status !== 'OPEN') {
        return undefined;
      }
      const timer = window.setInterval(() => {
        setNow(new Date());
      }, 1000);
      return () => window.clearInterval(timer);
    }, [snapshot.status]);

    return (
      <header className="top-bar" style={{ position: 'relative' }}>
        <div className="event-title-block">
          <span className="eyebrow">LIVE CONSOLE</span>
          <h1>{snapshot.title}</h1>
          <div className="nav-links">
            <Link to="/" className={`nav-tab ${useLocation().pathname === '/' ? 'active' : ''}`}>Dashboard</Link>
            <Link to="/monitoring" className={`nav-tab ${useLocation().pathname === '/monitoring' ? 'active' : ''}`}>Monitoring Console</Link>
          </div>
        </div>
        <div className="event-status" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <strong>{formatEventStatus(snapshot.status)}</strong>
          <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
          <span>{snapshot.metrics.reservedCount} reserved</span>
        </div>
        <div className="event-actions" style={{ position: 'relative' }}>
          {snapshot.status === 'READY' ? (
            <>
              <button 
                type="button" 
                className="btn btn-primary" 
                onClick={() => setIsSettingsOpen(!isSettingsOpen)}
              >
                이벤트 시작하기 {isSettingsOpen ? '▲' : '▼'}
              </button>
              {isSettingsOpen && (
                <div 
                  className="panel" 
                  style={{
                    position: 'absolute',
                    right: 0,
                    top: 'calc(100% + 8px)',
                    zIndex: 50,
                    width: '320px',
                    padding: '20px',
                    boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
                    background: '#FFFFFF',
                    border: '1px solid var(--border-line)',
                    borderRadius: 'var(--radius-lg)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px',
                    textAlign: 'left'
                  }}
                >
                  <h3 style={{ margin: 0, fontSize: '14px', fontWeight: '700', color: 'var(--text-primary)' }}>AI 시뮬레이션 설정</h3>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <label htmlFor="popover-ai-count" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>AI 유저 수</label>
                    <input
                      id="popover-ai-count"
                      type="number"
                      min={0}
                      max={1000}
                      value={aiCount}
                      onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                      style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                    />
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <label htmlFor="popover-ai-concurrency" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>동시 인입 수</label>
                    <input
                      id="popover-ai-concurrency"
                      type="number"
                      min={1}
                      max={120}
                      value={aiConcurrency}
                      onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                      style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                    />
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <label htmlFor="popover-ai-speed" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>행동 속도</label>
                    <select
                      id="popover-ai-speed"
                      value={aiSpeed}
                      onChange={(e) => setAiSpeed(e.target.value as any)}
                      style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px', backgroundColor: '#FFFFFF' }}
                    >
                      <option value="SLOW">느림 (1.5초)</option>
                      <option value="NORMAL">보통 (0.5초)</option>
                      <option value="FAST">빠름 (0.1초)</option>
                    </select>
                  </div>

                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => {
                      setIsSettingsOpen(false);
                      onStart({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed });
                    }}
                    style={{ width: '100%', marginTop: '8px' }}
                  >
                    시뮬레이션 및 예매 시작
                  </button>
                </div>
              )}
            </>
          ) : null}
          {snapshot.status === 'ENDED' ? (
            <button className="btn btn-primary" onClick={onReset}>새 이벤트 시작</button>
          ) : null}
        </div>
      </header>
    );
  }
  ```

- [ ] **Step 2: 커밋**
  Run:
  ```bash
  git add frontend/src/components/EventHeader.tsx
  git commit -m "feat: implement dropdown absolute popover for AI configurations on EventHeader"
  ```

---

### Task 2: Dashboard 컴포넌트의 본문 시작 카드 제거 및 props 롤백

**Files:**
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Dashboard.tsx 수정**
  대시보드 본문에 임시로 추가했던 AI 설정용 카드 UI 마크업 및 관련 내부 로컬 상태들을 말끔히 삭제하고, `<EventHeader>` 호출 시 복구된 `onStart` 핸들러 프롭을 전달합니다.

  *수정 후 코드 구조:*
  ```tsx
  export default function Dashboard() {
    const room = useLiveEventRoom(apiBaseUrl);
    const [metrics, setMetrics] = useState<SystemMetrics | null>(null);

    useEffect(() => {
      let mounted = true;
      const loadMetrics = async () => {
        try {
          const data = await fetchSystemMetrics(apiBaseUrl);
          if (mounted) setMetrics(data);
        } catch (e) {
          // ignore errors
        }
      };
      loadMetrics();
      const interval = setInterval(loadMetrics, 5000);
      return () => {
        mounted = false;
        clearInterval(interval);
      };
    }, []);

    const openTicketingWindow = () => {
      if (!room.eventId) return;
      const url = `/ticketing/${room.eventId}`;
      const win = window.open(url, 'TimedealTicketingWindow', 'width=900,height=700,status=no,menubar=no,toolbar=no');
      if (win) {
        win.focus();
      }
    };

    if (!room.snapshot) {
      return (
        <div className="dashboard-container">
          <aside className="sidebar">
            <Link to="/" className="sidebar-icon active">D</Link>
          </aside>
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

    return (
      <div className="dashboard-container">
        <aside className="sidebar">
          <Link to="/" className="sidebar-icon active" title="Dashboard">D</Link>
          <Link to="/monitoring" className="sidebar-icon" title="Monitoring">M</Link>
        </aside>

        <main className="main-content">
          <EventHeader
            snapshot={room.snapshot}
            onStart={(request) => void room.start(request)}
            onReset={() => void room.reset()}
          />
          {room.error ? <div className="error-banner">{room.error}</div> : null}
          {room.message ? <div className="info-banner">{room.message}</div> : null}

          <div className="metric-strip" aria-label="실시간 이벤트 지표">
            <Metric label="SEATS RESERVED" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="예약 완료" />
            <Metric label="WAITING QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="대기 유저" />
            <Metric label="TPS PEAK" value={`${metrics ? metrics.tps.toFixed(1) : '0.0'}`} detail="거래량/초" />
            <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="실시간 커넥션" />
          </div>

          <div className="dashboard-hero-grid">
            <div className="panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', color: 'var(--text-primary)' }}>실시간 예매 좌석도 (관제 전용)</h3>
              <SeatMap
                status={room.snapshot.status}
                seats={room.snapshot.seats}
                participant={room.myParticipant}
                selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
                onSelectSeat={(seatId) => void room.selectSeat(seatId)}
                readOnly={true}
              />
            </div>
            <MyTicketPanel
              status={room.snapshot.status}
              participant={room.myParticipant}
              loading={room.loading}
              onJoin={() => void room.join(randomGuestName())}
              onReserve={openTicketingWindow}
              onPay={() => void room.pay()}
            />
          </div>
        </main>
      </div>
    );
  }
  ```

- [ ] **Step 2: 커밋**
  Run:
  ```bash
  git add frontend/src/Dashboard.tsx
  git commit -m "feat: clean up simulation starter card markup and restore EventHeader onStart prop"
  ```

---

### Task 3: MonitoringConsole 컴포넌트의 onStart 바인딩

**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx`

- [ ] **Step 1: MonitoringConsole.tsx 수정**
  모니터링 콘솔 내의 `<EventHeader>` 호출부에도 마찬가지로 `onStart` 핸들러 프롭을 복구 바인딩해 줍니다. (두 탭 간의 완벽한 기능 호환성 유지)

  *수정 대상 코드:*
  ```tsx
        <main className="main-content">
          <EventHeader snapshot={room.snapshot} onReset={() => void room.reset()} />
  ```

  *수정 후 코드:*
  ```tsx
        <main className="main-content">
          <EventHeader
            snapshot={room.snapshot}
            onStart={(request) => void room.start(request)}
            onReset={() => void room.reset()}
          />
  ```

- [ ] **Step 2: 커밋**
  Run:
  ```bash
  git add frontend/src/components/MonitoringConsole.tsx
  git commit -m "feat: bind onStart event handler to EventHeader in MonitoringConsole view"
  ```

---

### Task 4: 테스트 수정 및 최종 검증

**Files:**
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: App.test.tsx 수정**
  다시 대시보드 및 모니터링콘솔의 "이벤트 시작하기" 버튼 관련 엘리먼트 쿼리 단언으로 원복합니다.

  *수정 후 App.test.tsx:*
  ```tsx
  import { render, screen } from '@testing-library/react';
  import { describe, expect, it, vi } from 'vitest';
  import { MemoryRouter } from 'react-router-dom';
  import App from './App';

  vi.mock('./hooks/useLiveEventRoom', () => ({
    useLiveEventRoom: () => ({
      eventId: 'event-1',
      participantId: null,
      snapshot: {
        eventId: 'event-1',
        title: '티켓팅 시뮬레이터',
        status: 'READY',
        generation: 1,
        opensAt: null,
        endsAt: null,
        seats: [{ id: 1, label: 'A-1', status: 'AVAILABLE' }],
        participants: [],
        metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
        serverStats: [{ serverId: 'api-a', requestCount: 1, conflictCount: 0, successCount: 0 }],
        running: false,
        myParticipantId: null,
        myQueuePosition: null,
      },
      myParticipant: null,
      loading: false,
      error: null,
      message: null,
      join: vi.fn(),
      reserve: vi.fn(),
      selectSeat: vi.fn(),
      pay: vi.fn(),
      start: vi.fn(),
      reset: vi.fn(),
      startAi: vi.fn(),
    }),
  }));

  describe('App', () => {
    it('shows the start action prominently on the dashboard before the event starts', () => {
      render(
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      );

      expect(screen.getByRole('heading', { name: /티켓팅/ })).toBeInTheDocument();
      expect(screen.getByText('LIVE CONSOLE')).toBeInTheDocument();
      expect(screen.getByText('SEATS RESERVED')).toBeInTheDocument();
      expect(screen.getAllByText('WAITING QUEUE').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('시작 전')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /이벤트 시작하기/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '예약하기' })).toBeDisabled();
      expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
      expect(screen.queryByText('예매가 아직 시작되지 않았습니다.')).not.toBeInTheDocument();
      expect(screen.queryByText('서버 분산')).not.toBeInTheDocument();
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
      expect(screen.getByRole('button', { name: /이벤트 시작하기/ })).toBeInTheDocument();
      expect(screen.getByText('참가자 현황')).toBeInTheDocument();
      expect(screen.getByText('서버 분산')).toBeInTheDocument();
    });
  });
  ```

- [ ] **Step 2: 테스트 수행**
  Run: `npm run test`
  Expected: 모든 24개 테스트 PASS

- [ ] **Step 3: 최종 빌드 및 완료 확인**
  Run: `npm run build`
  Expected: 정상 빌드 완료

- [ ] **Step 4: 커밋**
  Run:
  ```bash
  git add frontend/src/App.test.tsx
  git commit -m "test: align App testing specs with dropdown popover simulation header UI"
  ```
