import type { LiveEventSnapshot, SystemMetrics } from '../api/liveEventApi';
import type { SimulationSnapshot } from '../api/simulationApi';

interface InsightPanelProps {
  snapshot: SimulationSnapshot | LiveEventSnapshot;
  metrics: SystemMetrics | null;
}

export function InsightPanel({ snapshot, metrics }: InsightPanelProps) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
      <section className="panel">
        <h2>서버 분산</h2>
        {((metrics?.serverStats ?? snapshot?.serverStats) ?? []).map((stats) => (
          <div className="metric-row" key={stats.serverId}>
            <span>{stats.serverId}</span>
            <strong>{stats.requestCount}</strong>
            <small>충돌 {stats.conflictCount} · 성공 {stats.successCount}</small>
          </div>
        ))}
      </section>
      
      <section className="panel">
        <h2>시스템 및 인프라 상태</h2>
        <div className="metric-row"><span>평균 응답 속도</span><strong>{metrics?.avgResponseTimeMs ? Math.round(metrics.avgResponseTimeMs) : 0}ms</strong></div>
        <div className="metric-row"><span>Kafka Lag</span><strong>{metrics?.kafkaLag ?? 0} messages</strong></div>
        <div className="metric-row"><span>Redis Active Locks</span><strong>{metrics?.redisLockCount ?? 0} locks</strong></div>
      </section>
    </div>
  );
}
