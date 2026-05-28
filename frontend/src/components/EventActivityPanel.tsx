import type { LiveEventSnapshot } from '../api/liveEventApi';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
}

export function EventActivityPanel({ snapshot, participantId }: EventActivityPanelProps) {
  const targetParticipants = participantId
    ? snapshot.participants.filter((participant) => participant.id === participantId)
    : snapshot.participants;
  const recent = targetParticipants.flatMap((participant) =>
    participant.timeline.slice(-6).map((entry) => ({
      id: `${participant.id}-${entry.label}-${entry.message}`,
      participant: participant.displayName,
      label: entry.label,
      message: entry.message,
    })),
  ).slice(-8);

  return (
    <section className="panel activity-panel">
      <div className="panel-title-row">
        <h2>{participantId ? '내 진행' : '전체 진행'}</h2>
      </div>
      <div className="infra-grid">
        {snapshot.serverStats.map((stat) => (
          <div key={stat.serverId}>
            <strong>{stat.serverId}</strong>
            <span>{stat.requestCount}건 처리</span>
          </div>
        ))}
      </div>
      {recent.length === 0 ? (
        <p className="empty-log">아직 표시할 진행 내역이 없습니다.</p>
      ) : (
        <ol className="activity-list">
          {recent.map((entry) => (
            <li key={entry.id}>
              <strong>{entry.participant}</strong>
              <span>{entry.label}</span>
              <p>{entry.message}</p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
