import type { LiveEventSnapshot } from '../api/liveEventApi';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  onStart: () => void;
  onReset: () => void;
}

export function EventActivityPanel({ snapshot, onStart, onReset }: EventActivityPanelProps) {
  const recent = snapshot.participants.flatMap((participant) =>
    participant.timeline.slice(-2).map((entry) => ({
      id: `${participant.id}-${entry.label}-${entry.message}`,
      participant: participant.displayName,
      label: entry.label,
      message: entry.message,
    })),
  ).slice(-8);

  return (
    <section className="panel activity-panel">
      <div className="panel-title-row">
        <h2>실시간 진행</h2>
        {snapshot.status === 'READY' ? (
          <button className="secondary-action compact" onClick={onStart}>이벤트 시작하기</button>
        ) : null}
        {snapshot.status === 'ENDED' ? (
          <button className="secondary-action compact" onClick={onReset}>새 이벤트 시작</button>
        ) : null}
      </div>
      <div className="infra-grid">
        {snapshot.serverStats.map((stat) => (
          <div key={stat.serverId}>
            <strong>{stat.serverId}</strong>
            <span>{stat.requestCount}건 처리</span>
          </div>
        ))}
      </div>
      <ol className="activity-list">
        {recent.map((entry) => (
          <li key={entry.id}>
            <strong>{entry.participant}</strong>
            <span>{entry.label}</span>
            <p>{entry.message}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
