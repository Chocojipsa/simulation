# Scrollbar Layout Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the main content vertical scrollbar placement so that it aligns to the absolute right edge of the browser window instead of floating in the center on wide screens, while keeping dashboard contents centered at a max width of 1400px.

**Architecture:** Separate scroll/flex container responsibility (`.main-content-wrapper`, full-width) from the width-limiting content container responsibility (`.main-content`, max-width 1400px centered). Remove `overflow-y: auto` from `.main-content` and put it on `.main-content-wrapper`.

**Tech Stack:** React, CSS

---

### Task 1: Update Stylesheet Rules

**Files:**
- Modify: `frontend/src/styles.css:145-152`

- [ ] **Step 1: Replace .main-content rule and add .main-content-wrapper rule in stylesheet**

Modify `frontend/src/styles.css` to change the `.main-content` definition and add `.main-content-wrapper`:

```css
.main-content-wrapper {
  flex: 1;
  overflow-y: auto;
  height: 100%;
  width: 100%;
}

.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 32px;
  width: 100%;
  box-sizing: border-box;
}
```

- [ ] **Step 2: Commit changes**

```bash
git add frontend/src/styles.css
git commit -m "style: define main-content-wrapper and remove overflow from main-content"
```

---

### Task 2: Update Dashboard Component Layout

**Files:**
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Wrap main content inside main-content-wrapper in Dashboard.tsx**

Modify `frontend/src/Dashboard.tsx` to wrap `<main className="main-content">` with `<div className="main-content-wrapper">` in the main return block:

```tsx
  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="dashboard"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <div className="main-content-wrapper">
        <main className="main-content">
          <header style={{ marginBottom: '24px' }}>
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1 style={{ fontSize: '24px', fontWeight: '800', color: 'var(--text-primary)', marginTop: '4px' }}>{room.snapshot.title}</h1>
          </header>
          {room.error ? <div className="error-banner">{room.error}</div> : null}
          {room.message ? <div className="info-banner">{room.message}</div> : null}
          
          <div className="metric-strip" aria-label="실시간 이벤트 지표">
            <Metric label="SEATS RESERVED" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="예약 완료" />
            <Metric label="WAITING QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="대기 유저" />
            <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="실시간 커넥션" />
          </div>

          <div className="dashboard-hero-grid">
            <div className="panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', color: 'var(--text-primary)' }}>실시간 예매 좌석도</h3>
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
    </div>
  );
```

- [ ] **Step 2: Commit changes**

```bash
git add frontend/src/Dashboard.tsx
git commit -m "style: wrap main content in main-content-wrapper for Dashboard layout"
```

---

### Task 3: Update MonitoringConsole Component Layout

**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx`

- [ ] **Step 1: Wrap main content inside main-content-wrapper in MonitoringConsole.tsx**

Modify `frontend/src/components/MonitoringConsole.tsx` to wrap `<main className="main-content">` with `<div className="main-content-wrapper">` in the main return block:

```tsx
  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="monitoring"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <div className="main-content-wrapper">
        <main className="main-content">
          <header style={{ marginBottom: '24px' }}>
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1 style={{ fontSize: '24px', fontWeight: '800', color: 'var(--text-primary)', marginTop: '4px' }}>{room.snapshot.title}</h1>
          </header>
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

          {/* InsightPanel (서버 분산 및 시스템 인프라) */}
          <div className="insight-section" style={{ marginTop: '24px' }}>
            <InsightPanel snapshot={room.snapshot} metrics={metrics} />
          </div>
        </main>
      </div>
    </div>
  );
```

- [ ] **Step 2: Commit changes**

```bash
git add frontend/src/components/MonitoringConsole.tsx
git commit -m "style: wrap main content in main-content-wrapper for MonitoringConsole layout"
```

---

### Task 4: Verify Tests and Production Build

- [ ] **Step 1: Run tests**

Run: `npm run test` inside `frontend` directory.
Expected: All 27 tests pass successfully.

- [ ] **Step 2: Run production build**

Run: `npm run build` inside `frontend` directory.
Expected: Production build compiles successfully with code 0.
