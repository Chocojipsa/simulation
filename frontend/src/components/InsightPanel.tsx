import { useEffect, useState } from 'react';
import type { LiveEventSnapshot, SystemMetrics } from '../api/liveEventApi';
import type { SimulationSnapshot } from '../api/simulationApi';
import { fetchSystemMetrics } from '../api/liveEventApi';
import { countSeatsByStatus } from '../domain/simulationSelectors';

interface InsightPanelProps {
  snapshot: SimulationSnapshot | LiveEventSnapshot;
  apiBaseUrl?: string;
}

export function InsightPanel({ snapshot, apiBaseUrl = '' }: InsightPanelProps) {
  const seatCounts = countSeatsByStatus(snapshot.seats);
  const [metrics, setMetrics] = useState<SystemMetrics | null>(null);

  useEffect(() => {
    let mounted = true;
    const fetchMetrics = async () => {
      try {
        const data = await fetchSystemMetrics(apiBaseUrl);
        if (mounted) setMetrics(data);
      } catch (e) {
        // ignore errors for metrics
      }
    };
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 5000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [apiBaseUrl]);

  return (
    <aside className="insight-row">
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
        <h2>시스템 성능</h2>
        <div className="metric-row"><span>TPS</span><strong>{metrics ? metrics.tps.toFixed(1) : '0.0'}</strong></div>
        <div className="metric-row"><span>응답 시간</span><strong>{metrics ? Math.round(metrics.avgResponseTimeMs) : 0}ms</strong></div>
      </section>
      <section className="panel">
        <h2>Redis 대기열</h2>
        <div className="metric-row"><span>대기</span><strong>{snapshot.metrics.queueSize}</strong></div>
        <div className="metric-row"><span>입장</span><strong>{snapshot.metrics.admittedCount}</strong></div>
        <div className="metric-row"><span>분산 락 (Redis)</span><strong>{metrics ? metrics.redisLockCount : 0}</strong></div>
      </section>
      <section className="panel">
        <h2>Kafka 결제</h2>
        <div className="metric-row"><span>결제 중</span><strong>{snapshot.metrics.paymentInProgressCount}</strong></div>
        <div className="metric-row"><span>예약 완료</span><strong>{snapshot.metrics.reservedCount}</strong></div>
        <div className="metric-row"><span>실패</span><strong>{snapshot.metrics.failedCount}</strong></div>
        <div className="metric-row"><span>컨슈머 렉 (Kafka)</span><strong>{metrics ? metrics.kafkaLag : 0}</strong></div>
      </section>
      <section className="panel">
        <h2>PostgreSQL 좌석 선점</h2>
        <div className="metric-row"><span>가능</span><strong>{seatCounts.AVAILABLE}</strong></div>
        <div className="metric-row"><span>예약</span><strong>{seatCounts.RESERVED}</strong></div>
      </section>
    </aside>
  );
}
