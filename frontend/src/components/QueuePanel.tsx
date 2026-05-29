import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatParticipantStatus } from '../domain/liveEventSelectors';

interface QueuePanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
}

export function QueuePanel({ snapshot, participantId }: QueuePanelProps) {
  const position = snapshot.myQueuePosition;
  const participant = snapshot.participants.find((candidate) => candidate.id === participantId) ?? null;

  return (
    <section className="panel queue-panel">
      <div className="panel-heading">
        <span className="eyebrow">QUEUE</span>
        <h2>대기열</h2>
      </div>
      <div className="status-line">
        <span>내 상태</span>
        <strong>{formatParticipantStatus(participant?.status)}</strong>
      </div>
      <div className="status-line">
        <span>현재 대기</span>
        <strong>{snapshot.metrics.queueSize}명</strong>
      </div>
      <div className="status-line">
        <span>내 순서</span>
        <strong>{position ? `${position}번째` : '-'}</strong>
      </div>
      <p className="queue-note">
        {participant?.status === 'QUEUED'
          ? '통과 대기 중'
          : participant?.status === 'SELECTING_SEAT'
            ? '좌석 선택 가능'
            : '예매 시작 후 진입'}
      </p>
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
        {snapshot.metrics.queueSize === 0 ? <p className="empty-log">대기 중인 참가자가 없습니다.</p> : null}
      </div>
    </section>
  );
}
