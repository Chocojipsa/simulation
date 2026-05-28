import type { LiveEventSnapshot } from '../api/liveEventApi';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  onStartAi: () => void;
}

export function EventActivityPanel({ snapshot, onStartAi }: EventActivityPanelProps) {
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
        <button className="secondary-action compact" onClick={onStartAi}>AI 참가자 시작</button>
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
