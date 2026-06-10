# 대시보드 및 헤더 UX 개선 구현 계획서 (Dashboard & Header UX Redesign Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이벤트 시작 컨트롤을 대시보드 내 독립 카드로 이전하고, 인프라 관제 지표 패널을 모니터링 콘솔 하단으로 이전하여 헤더 레이아웃이 튀는 현상을 방지하고 깔끔한 관제 UI를 확보합니다.

**Architecture:**
1. `EventHeader.tsx`에서 AI 관련 설정 및 시작 폼을 지워 글로벌 정보 헤더로 축소합니다.
2. `Dashboard.tsx` 본문 내에 `READY` 상태일 때만 드러나는 "AI 시뮬레이션 설정 및 시작" 카드를 구축하고 하단 `InsightPanel`을 삭제합니다.
3. `MonitoringConsole.tsx`에 5초 폴링 메트릭 훅을 복제하고 화면 하단에 `InsightPanel`을 배치합니다.
4. 테스트 코드(`App.test.tsx`) 및 컴포넌트 호출 처리를 정리하여 빌드와 테스트를 유지합니다.

**Tech Stack:** React 18, TypeScript, CSS3, Vitest

---

### Task 1: EventHeader 컴포넌트 축소 및 폼 제거

**Files:**
- Modify: `frontend/src/components/EventHeader.tsx:6-98`

- [ ] **Step 1: EventHeader.tsx 수정**
  AI 설정 상태값 및 입력 폼 마크업을 완전히 제거하고 props 인터페이스를 간소화합니다.

  *수정 후 코드:*
  ```tsx
  import { useEffect, useState } from 'react';
  import type { LiveEventSnapshot } from '../api/liveEventApi';
  import { Link, useLocation } from 'react-router-dom';
  import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

  interface EventHeaderProps {
    snapshot: LiveEventSnapshot;
    onReset: () => void;
  }

  export function EventHeader({ snapshot, onReset }: EventHeaderProps) {
    const [now, setNow] = useState(() => new Date());

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
      <header className="top-bar">
        <div className="event-title-block">
          <span className="eyebrow">LIVE CONSOLE</span>
          <h1>{snapshot.title}</h1>
          <div className="nav-links">
            <Link to="/" className={`nav-tab ${useLocation().pathname === '/' ? 'active' : ''}`}>Dashboard</Link>
            <Link to="/monitoring" className={`nav-tab ${useLocation().pathname === '/monitoring' ? 'active' : ''}`}>Monitoring Console</Link>
          </div>
        </div>
        <div className="event-status">
          <strong>{formatEventStatus(snapshot.status)}</strong>
          <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
          <span>{snapshot.metrics.reservedCount} reserved</span>
        </div>
        <div className="event-actions">
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
  git commit -m "refactor: simplify EventHeader props and remove AI config toolbar"
  ```

---

### Task 2: Dashboard 컴포넌트의 AI 설정 카드 이식 및 인프라 지표 제거

**Files:**
- Modify: `frontend/src/Dashboard.tsx:24-119`

- [ ] **Step 1: Dashboard.tsx 수정**
  AI 설정 전용 로컬 상태를 선언하고, `READY` 상태일 때 대시보드 본문 상단에 시뮬레이션 설정 카드를 렌더링하며, 대시보드 하단에 있던 `InsightPanel`을 걷어냅니다. 또한 `EventHeader`를 호출할 때 더 이상 사용하지 않는 `onStart` 프롭을 제거합니다.

  *수정 대상 코드 구조:*
  ```tsx
  export default function Dashboard() {
    const room = useLiveEventRoom(apiBaseUrl);
    const [metrics, setMetrics] = useState<SystemMetrics | null>(null);
    // AI 설정 폼을 위한 로컬 상태 선언
    const [aiCount, setAiCount] = useState<number>(150);
    const [aiConcurrency, setAiConcurrency] = useState<number>(50);
    const [aiSpeed, setAiSpeed] = useState<'SLOW' | 'NORMAL' | 'FAST'>('NORMAL');

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
          {/* onStart 프롭 제거 */}
          <EventHeader snapshot={room.snapshot} onReset={() => void room.reset()} />
          {room.error ? <div className="error-banner">{room.error}</div> : null}
          {room.message ? <div className="info-banner">{room.message}</div> : null}
          
          {/* 이벤트 시작 전(READY)일 때 본문 상단에 AI 시뮬레이션 설정 및 시작 카드 노출 */}
          {room.snapshot.status === 'READY' && (
            <section className="panel simulation-starter-card" style={{ padding: '24px', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '16px', color: 'var(--text-primary)' }}>AI 시뮬레이션 설정 및 시작</h2>
              <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <label htmlFor="dashboard-ai-count" style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>AI 유저 수</label>
                  <input
                    id="dashboard-ai-count"
                    type="number"
                    min={0}
                    max={1000}
                    value={aiCount}
                    onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                    style={{ width: '120px', padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                  />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <label htmlFor="dashboard-ai-concurrency" style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>동시 인입 수</label>
                  <input
                    id="dashboard-ai-concurrency"
                    type="number"
                    min={1}
                    max={120}
                    value={aiConcurrency}
                    onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                    style={{ width: '120px', padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                  />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <label htmlFor="dashboard-ai-speed" style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>행동 속도</label>
                  <select
                    id="dashboard-ai-speed"
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
                  onClick={() => room.start({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed })}
                  style={{ height: '38px', padding: '0 24px', fontSize: '13px' }}
                >
                  시뮬레이션 및 이벤트 시작하기
                </button>
              </div>
            </section>
          )}

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
          {/* InsightPanel 삭제됨 */}
        </main>
      </div>
    );
  }
  ```

- [ ] **Step 2: 커밋**
  Run:
  ```bash
  git add frontend/src/Dashboard.tsx
  git commit -m "feat: embed AI config panel in Dashboard and remove insight section"
  ```

---

### Task 3: MonitoringConsole의 인프라 지표 패널 탑재

**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx:1-73`

- [ ] **Step 1: MonitoringConsole.tsx 수정**
  `fetchSystemMetrics`, `SystemMetrics` 및 `InsightPanel`을 임포트하고, 시스템 메트릭을 5초 단위로 갱신하는 상태와 `useEffect` 폴링 구문을 삽입합니다. 하단에 `InsightPanel` 영역을 추가하고 `EventHeader`에 전달하던 `onStart` 프롭을 제거합니다.

  *수정 후 코드:*
  ```tsx
  import { useState, useEffect } from 'react';
  import { Link } from 'react-router-dom';
  import { useLiveEventRoom } from '../hooks/useLiveEventRoom';
  import { QueuePanel } from './QueuePanel';
  import { EventActivityPanel } from './EventActivityPanel';
  import { EventHeader } from './EventHeader';
  import { InsightPanel } from './InsightPanel';
  import { fetchSystemMetrics, type SystemMetrics } from '../api/liveEventApi';

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

    if (!room.snapshot) {
      return (
        <div className="dashboard-container">
          <aside className="sidebar">
            <Link to="/" className="sidebar-icon">D</Link>
            <Link to="/monitoring" className="sidebar-icon active">M</Link>
          </aside>
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

    return (
      <div className="dashboard-container">
        <aside className="sidebar">
          <Link to="/" className="sidebar-icon" title="Dashboard">D</Link>
          <Link to="/monitoring" className="sidebar-icon active" title="Monitoring">M</Link>
        </aside>

        <main className="main-content">
          {/* onStart 프롭 제거 */}
          <EventHeader snapshot={room.snapshot} onReset={() => void room.reset()} />
          {room.error ? <div className="error-banner">{room.error}</div> : null}
          {room.message ? <div className="info-banner">{room.message}</div> : null}
          
          <div className="dashboard-grid">
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
              onSelectParticipant={setSelectedParticipantId}
              apiBaseUrl={apiBaseUrl}
            />
          </div>

          {/* InsightPanel 추가됨 */}
          <div className="insight-section" style={{ marginTop: '24px' }}>
            <InsightPanel snapshot={room.snapshot} metrics={metrics} />
          </div>
        </main>
      </div>
    );
  }
  ```

- [ ] **Step 2: 커밋**
  Run:
  ```bash
  git add frontend/src/components/MonitoringConsole.tsx
  git commit -m "feat: migrate system metrics InsightPanel to MonitoringConsole view"
  ```

---

### Task 4: 테스트 수정 및 최종 검증

**Files:**
- Modify: `frontend/src/App.test.tsx:6-37`, `frontend/src/App.test.tsx:40-72`

- [ ] **Step 1: App.test.tsx 수정**
  헤더의 `onStart` 호출이 사라지고 대시보드 및 모니터링콘솔의 prop 사용법이 변경됨에 따라, 테스트 코드 내 단언과 모의 객체(Mock) 설정을 수정해 줍니다. 또한 `App.test.tsx`에서 '이벤트 시작하기' 대신 대시보드 본문에 나오는 '시뮬레이션 및 이벤트 시작하기' 버튼이 렌더링되는지 확인하도록 동기화합니다.

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
      // '이벤트 시작하기' 대신 본문 '시뮬레이션 및 이벤트 시작하기' 버튼 단언 확인
      expect(screen.getByRole('button', { name: '시뮬레이션 및 이벤트 시작하기' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '예약하기' })).toBeDisabled();
      expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
      expect(screen.queryByText('예매가 아직 시작되지 않았습니다.')).not.toBeInTheDocument();
      // 대시보드에서 서버 분산 패널은 빠졌으므로 단언에서 제거 혹은 존재하지 않음 검증
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
      // 대시보드가 아니므로 본문 시작버튼은 없음
      expect(screen.queryByRole('button', { name: '시뮬레이션 및 이벤트 시작하기' })).not.toBeInTheDocument();
      expect(screen.getByText('참가자 현황')).toBeInTheDocument();
      // 모니터링 탭이므로 서버 분산 패널이 노출되어야 함
      expect(screen.getByText('서버 분산')).toBeInTheDocument();
    });
  });
  ```

- [ ] **Step 2: 테스트를 수행하여 모든 24개 테스트가 정상 동작하는지 확인**
  Run: `npm run test`
  Expected: 모든 테스트 PASS

- [ ] **Step 3: 최종 빌드 및 완료 확인**
  Run: `npm run build`
  Expected: 정상 빌드 완료

- [ ] **Step 4: 커밋**
  Run:
  ```bash
  git add frontend/src/App.test.tsx
  git commit -m "test: update App router integration tests to match revised simulation and insight layouts"
  ```
