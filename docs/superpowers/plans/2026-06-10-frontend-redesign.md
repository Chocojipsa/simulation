# 프론트엔드 디자인 전면 리뉴얼 (SaaS Light Mode) 이행 계획서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 브루탈리즘 스타일의 어두운 UI를 전면 삭제하고, 채용 담당자 포트폴리오용으로 적합한 깔끔하고 세련된 데스크톱 PC 웹(SaaS Light Mode) 디자인 시스템 및 2-윈도우 아키텍처(대시보드 + 예매창 팝업)를 완벽히 구현합니다.

**Architecture:** 디자인 규격서(디자인.md)에 따라 CSS 변수를 개편하고, 둥근 모서리, 고해상도 그림자 및 넓은 공백을 바탕으로 글로벌 스타일을 개편합니다. 실시간 모니터링 대시보드(/)는 데스크톱 관제 그리드로 리팩토링하고, 별도 예매 팝업창(/ticketing/{eventId})은 좌석도가 결제 단계에서 가려지는 순차적 4단계 풀스크린 카드 방식(대기열 ➔ 좌석 선택 ➔ 결제 수단 ➔ 영수증)으로 개편합니다.

**Tech Stack:** React, TypeScript, Vite, React Router, Lucide React, Vitest, CSS Variables

---

### Task 1: 스타일 시스템 기초 설정 및 글로벌 CSS 개편

**Files:**
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: styles.css 전면 리팩토링 (디자인 토큰 적용)**
  
  `frontend/src/styles.css` 파일의 루트 변수와 기본 요소를 다음과 같이 수정합니다.
  
  ```css
  :root {
    color: #0F172A;
    background: #F8FAFC;
    font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    
    /* Brand Colors */
    --primary-indigo: #4F46E5;
    --primary-indigo-hover: #4338CA;
    --teal-accent: #00C2C2;
    --success-mint: #10B981;
    --danger-red: #EF4444;
    --warning-amber: #F59E0B;
    
    /* Seat Colors */
    --seat-available: #E2E8F0;
    --seat-booked: #334155;
    --seat-selected: #00C2C2;
    
    /* Neutral Colors */
    --bg-main: #F8FAFC;
    --bg-card: #FFFFFF;
    --border-line: #E2E8F0;
    --text-primary: #0F172A;
    --text-secondary: #475569;
    --text-tertiary: #94A3B8;
    
    /* Shadows & Radius */
    --card-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
    --hover-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
    --radius-lg: 12px;
    --radius-md: 8px;
    --radius-sm: 4px;
  }
  
  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }
  
  body {
    margin: 0;
    background-color: var(--bg-main);
    color: var(--text-primary);
    -webkit-font-smoothing: antialiased;
  }
  
  /* 전역 컴포넌트 스타일 리셋 */
  button, input, select {
    font-family: inherit;
    font-size: 14px;
  }
  ```

- [ ] **Step 2: 기본 카드 및 레이아웃 그리드 스타일 재정의**
  
  `frontend/src/styles.css` 하단에 현대적인 카드, 사이드바, 버튼 컴포넌트용 CSS 추가:
  
  ```css
  /* Layout Structures */
  .dashboard-container {
    display: flex;
    min-height: 100vh;
  }
  
  .sidebar {
    width: 64px;
    background: var(--bg-card);
    border-right: 1px solid var(--border-line);
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 24px 0;
    gap: 20px;
    flex-shrink: 0;
  }
  
  .sidebar-icon {
    width: 40px;
    height: 40px;
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-tertiary);
    cursor: pointer;
    transition: all 0.2s ease;
  }
  
  .sidebar-icon.active {
    background: rgba(79, 70, 229, 0.1);
    color: var(--primary-indigo);
  }
  
  .main-content {
    flex: 1;
    padding: 32px;
    overflow-y: auto;
  }
  
  /* Card Design */
  .card {
    background: var(--bg-card);
    border: 1px solid var(--border-line);
    border-radius: var(--radius-lg);
    box-shadow: var(--card-shadow);
    padding: 24px;
    margin-bottom: 24px;
    transition: box-shadow 0.2s ease;
  }
  
  .card:hover {
    box-shadow: var(--hover-shadow);
  }
  
  /* Premium Buttons */
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 10px 16px;
    font-weight: 600;
    border-radius: var(--radius-md);
    border: none;
    cursor: pointer;
    transition: all 0.2s ease;
  }
  
  .btn-primary {
    background: var(--primary-indigo);
    color: white;
  }
  
  .btn-primary:hover {
    background: var(--primary-indigo-hover);
    transform: translateY(-1px);
  }
  
  .btn-primary:active {
    transform: translateY(0);
  }
  
  .btn-secondary {
    background: var(--bg-main);
    color: var(--text-secondary);
    border: 1px solid var(--border-line);
  }
  
  .btn-secondary:hover {
    background: #F1F5F9;
  }
  ```

- [ ] **Step 3: 테스트 실행으로 기존 CSS 영향도 파악**
  
  Run: `npm run test`
  Expected: 기존 스타일 관련 단언(assertion)이 실패할 수 있음 (Task 6에서 최종 정비 예정).

- [ ] **Step 4: Git 커밋**
  
  ```bash
  git add frontend/src/styles.css
  git commit -m "style: define light mode SaaS design tokens and global layout variables"
  ```

---

### Task 2: 메인 대시보드 레이아웃 및 컴포넌트 리팩토링

**Files:**
- Modify: `frontend/src/Dashboard.tsx`, `frontend/src/components/EventHeader.tsx`, `frontend/src/components/MyTicketPanel.tsx`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Dashboard.tsx 레이아웃 전면 리팩토링**
  
  `frontend/src/Dashboard.tsx`의 렌더링 부분을 새로운 대시보드 레이아웃 구조로 전면 수정합니다:
  
  ```tsx
  // Dashboard.tsx의 메인 반환부 수정
  return (
    <div className="dashboard-container">
      {/* 1. Slim Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-icon active" title="Dashboard">
          {/* Dashboard Icon SVG or Lucide Icon */}
          <span style={{ fontWeight: 'bold' }}>D</span>
        </div>
        <div className="sidebar-icon" title="Monitoring">
          <span style={{ fontWeight: 'bold' }}>M</span>
        </div>
      </aside>
      
      {/* 2. Main Area */}
      <main className="main-content">
        <EventHeader snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />
        
        {room.error && <div style={{ background: '#FEE2E2', color: 'var(--danger-red)', padding: '12px', borderRadius: 'var(--radius-md)', marginBottom: '16px' }}>{room.error}</div>}
        
        {/* Metrics Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' }}>
          <Metric label="SEATS RESERVED" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="예약 완료" />
          <Metric label="WAITING QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="대기 유저" />
          <Metric label="TPS PEAK" value={`${metrics ? metrics.tps.toFixed(1) : '0.0'}`} detail="거래량/초" />
          <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="실시간 커넥션" />
        </div>
        
        {/* Main Hero Panels */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '24px', alignItems: 'start' }}>
          <div className="card">
            <h3 style={{ marginBottom: '16px', color: 'var(--text-primary)' }}>실시간 예매 좌석도 (관제 전용)</h3>
            <SeatMap
              status={room.snapshot.status}
              seats={room.snapshot.seats}
              participant={room.myParticipant}
              selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
              onSelectSeat={(seatId) => void room.selectSeat(seatId)}
              readOnly={true}
            />
          </div>
          
          <div className="card">
            <MyTicketPanel
              status={room.snapshot.status}
              participant={room.myParticipant}
              loading={room.loading}
              onJoin={() => void room.join(randomGuestName())}
              onReserve={openTicketingWindow}
              onPay={() => void room.pay()}
            />
          </div>
        </div>
      </main>
    </div>
  );
  ```

- [ ] **Step 2: EventHeader.tsx 디자인 개편**
  
  `frontend/src/components/EventHeader.tsx`를 정돈된 SaaS 스타일 배너 형태로 개편합니다.
  - 배경: `#FFFFFF`, 테두리: `1px solid #E2E8F0`
  - 상태 배지(Status Badge) 추가: `READY` (연한 그레이), `OPEN` (연한 블루 백그라운드에 블루 텍스트)

- [ ] **Step 3: MyTicketPanel.tsx 개편 (예매 참가 버튼)**
  
  `frontend/src/components/MyTicketPanel.tsx`에서 굵은 보더라인의 단추를 지우고, 로얄 인디고 테마의 모던 웹 버튼(`.btn`, `.btn-primary`)으로 통일합니다.

- [ ] **Step 4: 테스트 실행**
  
  Run: `npm run test`
  Expected: 렌더링 변경으로 인한 에러 로그 점검

- [ ] **Step 5: Git 커밋**
  
  ```bash
  git add frontend/src/Dashboard.tsx frontend/src/components/EventHeader.tsx frontend/src/components/MyTicketPanel.tsx
  git commit -m "feat: redesign dashboard with unified SaaS control console structure"
  ```

---

### Task 3: 좌석 배치도 컴포넌트(SeatMap)의 사각형 그리드 개편

**Files:**
- Modify: `frontend/src/components/SeatMap.tsx`
- Test: `frontend/src/domain/liveEventSelectors.test.ts`

- [ ] **Step 1: SeatMap.tsx 그리드 리팩토링**
  
  `seatdesign.PNG` 레퍼런스 스타일을 차용하여, 기존 원형 또는 조잡한 좌석 배치를 가로/세로축 라벨링이 명확한 사각형 좌석판으로 수정합니다.
  - 가로 라벨(1~7), 세로 라벨(A~H) 표시.
  - 좌석 요소를 원형이 아닌 `border-radius: 4px`를 지닌 정사각형 모양(`width: 24px, height: 24px`)으로 재설계.
  - 색상 매핑:
    - AVAILABLE: `#E2E8F0` (마우스 오버 시 `#CBD5E1`로 변경)
    - HELD/SELECTED: `#00C2C2` (Teal)
    - RESERVED: `#334155` (Slate 차콜)
  
  ```tsx
  // SeatMap.tsx 핵심 렌더링 수정
  const rows = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
  const cols = [1, 2, 3, 4, 5, 6, 7];
  
  return (
    <div className="seat-map-container" style={{ padding: '16px', background: '#F8FAFC', borderRadius: 'var(--radius-md)' }}>
      {/* Screen Area */}
      <div style={{ textAlign: 'center', margin: '0 auto 24px', maxWidth: '300px' }}>
        <div style={{ fontSize: '12px', fontWeight: 'bold', color: 'var(--teal-accent)', letterSpacing: '4px', marginBottom: '8px' }}>SCREEN</div>
        <div style={{ height: '4px', background: 'var(--teal-accent)', borderRadius: '2px' }}></div>
      </div>
      
      {/* Grid Map */}
      <div style={{ display: 'grid', gridTemplateColumns: '30px repeat(7, 1fr)', gap: '8px', alignItems: 'center' }}>
        {/* Column Labels Header */}
        <div></div>
        {cols.map(c => (
          <div key={c} style={{ textAlign: 'center', fontSize: '12px', fontWeight: 'bold', color: 'var(--teal-accent)' }}>{c}</div>
        ))}
        
        {/* Rows */}
        {rows.map(r => (
          <Fragment key={r}>
            <div style={{ fontWeight: 'bold', color: 'var(--teal-accent)', fontSize: '12px' }}>{r}</div>
            {cols.map(c => {
              const seatLabel = `${r}-${c}`;
              const seat = seats.find(s => s.label === seatLabel);
              if (!seat) return <div key={seatLabel} style={{ width: '24px', height: '24px' }}></div>;
              
              const isSelected = selectedSeatLabel === seatLabel;
              let bg = 'var(--seat-available)';
              if (seat.status === 'RESERVED') bg = 'var(--seat-booked)';
              if (seat.status === 'HELD' || isSelected) bg = 'var(--seat-selected)';
              
              return (
                <button
                  key={seat.id}
                  disabled={readOnly || seat.status === 'RESERVED'}
                  onClick={() => onSelectSeat && onSelectSeat(seat.id)}
                  style={{
                    width: '24px',
                    height: '24px',
                    backgroundColor: bg,
                    border: 'none',
                    borderRadius: 'var(--radius-sm)',
                    cursor: readOnly || seat.status === 'RESERVED' ? 'default' : 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  title={seatLabel}
                />
              );
            })}
          </Fragment>
        ))}
      </div>
    </div>
  );
  ```

- [ ] **Step 2: 테스트 실행 및 검증**
  
  Run: `npm run test`
  Expected: 좌석 렌더링 테스트의 마크업 확인.

- [ ] **Step 3: Git 커밋**
  
  ```bash
  git add frontend/src/components/SeatMap.tsx
  git commit -m "style: implement modern square seat grid matching seatdesign.PNG"
  ```

---

### Task 4: 독립형 예매 창 (TicketingWindow) 4단계 카드 구조 개편

**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx`
- Test: `frontend/src/components/TicketingWindow.test.tsx` (새로 작성하거나 기존 앱 테스트 보강)

- [ ] **Step 1: TicketingWindow.tsx 전체 디자인 시스템 및 4단계 폼 통합**
  
  `frontend/src/components/TicketingWindow.tsx`의 HTML과 인라인 스타일 태그(`<style>`) 부분을 완전히 전면 개편합니다.
  - 가로로 넓은 900x700 데스크톱 브라우저 크기에서 가장 시각적 밸런스가 뛰어나도록 넓은 화이트 예매 카드 박스 설계.
  - 모바일 프레임 잔재 삭제.
  - 1단계(대기), 2단계(좌석), 3단계(결제수단), 4단계(영수증)으로 이어지는 선형 구조(B안)를 명시적으로 통제.

- [ ] **Step 2: 단계별 컴포넌트 렌더링 구현**
  
  `TicketingWindow.tsx` 내부의 렌더링 분기를 다음과 같이 정돈합니다:
  
  ```tsx
  // Step indicator render
  const steps = [
    { num: 1, label: '대기열 진입' },
    { num: 2, label: '좌석 선택' },
    { num: 3, label: '결제 수단' },
    { num: 4, label: '주문 영수증' }
  ];
  
  // Step 1: Waiting Room Panel
  // - "대기번호 245번, 예상 소요시간"을 로얄 인디고 원형 프로그레스바 형태로 깔끔하게 표현.
  
  // Step 2: Seat Map Panel (좌석도가 2단계에서만 노출됨!)
  // - 좌석 선택 시, SeatMap 컴포넌트를 호출(readOnly={false}).
  // - 우측 사이드에 "선택한 좌석 정보" 및 "결제 단계로 이동" 버튼 위치.
  
  // Step 3: Payment Options Panel (design.PNG 레퍼런스)
  // - 좌석도가 사라지고 결제 카드 리스트만 깔끔하게 정돈됨.
  // - Account Balance (잔액 부족 가상 경고), Credit Card 옵션 라디오 버튼.
  
  // Step 4: Final Receipt Panel (design.PNG 레퍼런스)
  // - 뜯어쓰는 영수증 형상(border-top: 2px dashed #E2E8F0)을 지닌 예쁜 예매 내역서 및 "최종 예매 승인" 확인.
  ```

- [ ] **Step 3: 기능 테스트 실행**
  
  Run: `npm run test`
  Expected: `TicketingWindow`의 렌더링 및 기능 작동 확인.

- [ ] **Step 4: Git 커밋**
  
  ```bash
  git add frontend/src/components/TicketingWindow.tsx
  git commit -m "feat: refactor ticketing window to sequential 4-step full screen cards (Option B)"
  ```

---

### Task 5: 어드민 모니터링 페이지 및 기타 요소 개편

**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: MonitoringConsole.tsx 리디자인**
  
  `/monitoring` 경로에서 동작하는 관제 페이지의 브루탈리즘 스타일 컴포넌트와 보더라인을 지우고, 화이트 패널의 모던한 그리드 대시보드 디자인(SaaS Dashboard)을 적용합니다.

- [ ] **Step 2: 테스트 실행 및 점검**
  
  Run: `npm run test`
  Expected: PASS

- [ ] **Step 3: Git 커밋**
  
  ```bash
  git add frontend/src/components/MonitoringConsole.tsx
  git commit -m "style: update monitoring console to match modern light SaaS design"
  ```

---

### Task 6: 테스트 복구 및 종합 검증

**Files:**
- Modify: `frontend/src/App.test.tsx` (및 필요 시 테스트 단언 수정)
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: App.test.tsx의 스타일 단언 수정**
  
  기존 브루탈리즘 스타일의 텍스트나 클래스 기반 단언(예: `LIVE CONSOLE`, `시작 전` 등) 중 렌더링 리팩토링 과정에서 레이아웃이나 속성이 바뀐 부분이 있다면, 신규 구현된 구조에 맞추어 Vitest 단언 구문을 조정합니다.

- [ ] **Step 2: Vitest 전체 테스트 구동 및 최종 통과 확인**
  
  Run: `npm run test`
  Expected: 모든 테스트 파일(App.test.tsx, liveEventSelectors.test.ts 등)이 **PASS**되어야 함.

- [ ] **Step 3: Git 커밋**
  
  ```bash
  git add frontend/src/App.test.tsx
  git commit -m "test: adjust assertions for new premium SaaS layouts and components"
  ```
