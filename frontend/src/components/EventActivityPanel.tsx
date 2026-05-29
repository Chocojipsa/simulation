import type { LiveEventSnapshot } from '../api/liveEventApi';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
}

export function EventActivityPanel({ snapshot, participantId }: EventActivityPanelProps) {
  const myParticipants = participantId
    ? snapshot.participants.filter((participant) => participant.id === participantId)
    : [];
  const myRecent = recentEntries(myParticipants, 6);
  const allRecent = recentEntries(snapshot.participants, 10);

  return (
    <section className="panel activity-panel">
      <div className="panel-title-row">
        <span className="eyebrow">STREAM</span>
        <h2>진행 로그</h2>
      </div>
      <div className="infra-grid">
        {snapshot.serverStats.map((stat) => (
          <div key={stat.serverId}>
            <strong>{stat.serverId}</strong>
            <span>{stat.requestCount}건 처리</span>
          </div>
        ))}
      </div>
      <div className="activity-sections">
        {participantId ? <ActivityList title="내 진행" entries={myRecent} emptyMessage="내 진행 내역이 아직 없습니다." /> : null}
        <ActivityList title="전체 로그" entries={allRecent} emptyMessage="아직 표시할 진행 내역이 없습니다." />
      </div>
    </section>
  );
}

function ActivityList({
  title,
  entries,
  emptyMessage,
}: {
  title: string;
  entries: ActivityEntry[];
  emptyMessage: string;
}) {
  return (
    <div className="activity-section">
      <h3>{title}</h3>
      {entries.length === 0 ? (
        <p className="empty-log">{emptyMessage}</p>
      ) : (
        <ol className="activity-list">
          {entries.map((entry) => (
            <li key={entry.id}>
              <strong>{entry.participant}</strong>
              <span>{entry.label}</span>
              <p>{entry.message}</p>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

interface ActivityEntry {
  id: string;
  participant: string;
  label: string;
  message: string;
}

function recentEntries(participants: LiveEventSnapshot['participants'], limit: number): ActivityEntry[] {
  return participants.flatMap((participant) =>
    participant.timeline.slice(-6).map((entry, index) => ({
      id: `${participant.id}-${index}-${entry.label}-${entry.message}`,
      participant: participant.displayName,
      label: entry.label,
      message: entry.message,
    })),
  ).slice(-limit);
}
