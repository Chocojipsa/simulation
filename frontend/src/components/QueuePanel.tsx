import type { LiveEventSnapshot } from '../api/liveEventApi';
import { getQueuePosition } from '../domain/liveEventSelectors';

interface QueuePanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
}

export function QueuePanel({ snapshot, participantId }: QueuePanelProps) {
  const position = getQueuePosition(snapshot, participantId);

  return (
    <section className="panel queue-panel">
      <h2>대기열</h2>
      <div className="status-line">
        <span>현재 대기</span>
        <strong>{snapshot.metrics.queueSize}명</strong>
      </div>
      <div className="status-line">
        <span>내 순서</span>
        <strong>{position ? `${position}번째` : '-'}</strong>
      </div>
      <div className="queue-list">
        {snapshot.participants
          .filter((participant) => participant.status === 'QUEUED')
          .slice(0, 6)
          .map((participant, index) => (
            <div key={participant.id} className={participant.id === participantId ? 'queue-row queue-row-me' : 'queue-row'}>
              <span>{index + 1}</span>
              <strong>{participant.displayName}</strong>
              <em>{participant.type}</em>
            </div>
          ))}
      </div>
    </section>
  );
}
