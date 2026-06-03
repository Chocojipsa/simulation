import type { LiveEventSnapshot } from '../api/liveEventApi';
import type { SimulationSnapshot } from '../api/simulationApi';
import { countSeatsByStatus } from '../domain/simulationSelectors';

interface InsightPanelProps {
  snapshot: SimulationSnapshot | LiveEventSnapshot;
}

export function InsightPanel({ snapshot }: InsightPanelProps) {
  const seatCounts = countSeatsByStatus(snapshot.seats);

  return (
    <aside className="insight-row">
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
