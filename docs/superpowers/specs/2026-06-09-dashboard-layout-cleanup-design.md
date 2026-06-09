# Dashboard Layout Cleanup & Centered Seat Map Design Spec

**Goal:** Simplify the dashboard layout by placing the `SeatMap` at the absolute center as the hero component (full width), reducing the top metrics to 4 core real-time metrics (moving TPS to the top), and organizing the remaining system metrics into a cleaner 2-column layout at the bottom.

## 1. Top Metrics Strip Cleanup
Reduce the top metrics strip from 8 items to the 4 most critical metrics:
1. **SEATS**: Reserved seats count / total seats (`room.snapshot.metrics.reservedCount` / `room.snapshot.seats.length`)
2. **QUEUE**: Current queue size (`room.snapshot.metrics.queueSize`)
3. **TPS**: Transaction Throughput (`metrics ? metrics.tps.toFixed(1) : '0.0'`) - *promoted from the bottom of the page*
4. **ACTIVE USERS**: Real-time connected socket users (`room.snapshot.activeConnections`)

Other redundant or low-level metrics (`HELD`, `ADMISSIONS AVAILABLE`, `REDIS LOCKS`, `NODES`) are removed from the top strip.

## 2. Hero Seat Map Placement
The `SeatMap` will be moved out of the 2-column grid and placed directly under the metric strip. It will span the **full width** of the container, making it the central focal point of the dashboard.

## 3. Bottom Grid Split (My Ticket & Insights)
A 2-column grid will be added under the `SeatMap`:
* **Left Column (Width: ~300px)**: `MyTicketPanel` (Guest Ticket Pass & Actions).
* **Right Column (Flex: 1)**: `InsightPanel` (Server Load & Infrastructure).

## 4. Insight Panel Simplification
Remove the `PostgreSQL 좌석 선점` panel entirely since it is redundant with the Seat Map.
Organize the remaining panels in `InsightPanel` into 2 columns:
* **Left Column (서버 분산)**: Show load distribution among backend nodes (`api-a`, `api-b`) request count, conflicts, and successes.
* **Right Column (인프라 상태 및 성능)**: Show technical health parameters:
  - TPS & Average Response Time.
  - Kafka Consumer Lag.
  - Redis Active Locks.

---

## Technical Changes

### `frontend/src/Dashboard.tsx`
* Update the layout structure:
```tsx
      <div className="metric-strip" aria-label="실시간 이벤트 지표">
        <Metric label="SEATS" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="reserved" />
        <Metric label="QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="waiting" />
        <Metric label="TPS" value={`${metrics ? metrics.tps.toFixed(1) : '0.0'}`} detail="transactions/s" />
        <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="connected" />
      </div>
      
      {/* Hero Seat Map */}
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

      {/* Bottom Grid Split */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(230px, 280px) 1fr', gap: '16px' }}>
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

### `frontend/src/components/InsightPanel.tsx`
* Clean up the cards and arrange them:
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
