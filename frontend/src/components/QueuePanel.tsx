import { useEffect, useState, useRef } from 'react';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatParticipantStatus } from '../domain/liveEventSelectors';

interface QueuePanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
  selectedParticipantId: string | null;
  onSelectParticipant: (id: string) => void;
}

export function QueuePanel({ snapshot, participantId, selectedParticipantId, onSelectParticipant }: QueuePanelProps) {
  const participant = snapshot.participants.find((candidate) => candidate.id === participantId) ?? null;

  // Track the last active timestamp of each participant
  const [lastActiveMap, setLastActiveMap] = useState<Record<string, number>>({});
  const prevParticipantsRef = useRef<typeof snapshot.participants>([]);

  useEffect(() => {
    const nextMap = { ...lastActiveMap };
    let changed = false;

    snapshot.participants.forEach((p) => {
      const prev = prevParticipantsRef.current.find((prevP) => prevP.id === p.id);
      if (!prev) {
        // Initialize new participant's active status
        nextMap[p.id] = Date.now();
        changed = true;
        return;
      }

      // Detect any status, attempts, conflicts, or timeline changes
      const hasChanged =
        prev.status !== p.status ||
        prev.seatAttemptCount !== p.seatAttemptCount ||
        prev.conflictCount !== p.conflictCount ||
        prev.timeline.length !== p.timeline.length;

      if (hasChanged) {
        nextMap[p.id] = Date.now();
        changed = true;
      }
    });

    prevParticipantsRef.current = snapshot.participants;

    if (changed) {
      setLastActiveMap(nextMap);
    }
  }, [snapshot.participants]);

  // Group participants by status
  const groups = {
    active: snapshot.participants.filter(p => ['SELECTING_SEAT', 'SEAT_HELD', 'PAYMENT_IN_PROGRESS'].includes(p.status)),
    waiting: snapshot.participants.filter(p => p.status === 'QUEUED'),
    finished: snapshot.participants.filter(p => p.status === 'RESERVED' || p.status === 'FAILED'),
  };

  const renderUserRow = (p: typeof snapshot.participants[0]) => {
    const isMe = p.id === participantId;
    const isSelected = p.id === selectedParticipantId;
    const lastActive = lastActiveMap[p.id];
    const isUserActive = lastActive !== undefined && (Date.now() - lastActive < 5000);

    return (
      <button
        key={p.id}
        className={`queue-row ${isMe ? 'queue-row-me' : ''} ${isSelected ? 'active' : ''}`}
        onClick={() => onSelectParticipant(p.id)}
      >
        <strong className="p-name">
          {isUserActive && (
            <span
              className="pulsing-dot"
              title="최근 활동함"
              style={{
                width: '8px',
                height: '8px',
                backgroundColor: 'var(--mint)',
                borderRadius: '50%',
                display: 'inline-block',
                marginRight: '6px',
                verticalAlign: 'middle',
              }}
            ></span>
          )}
          {p.displayName} {isMe ? '(나)' : ''}
        </strong>
        <span className={`p-status status-${p.status.toLowerCase()}`}>{formatParticipantStatus(p.status)}</span>
        {p.selectedSeatLabel && <em className="p-seat">{p.selectedSeatLabel}</em>}
      </button>
    );
  };

  return (
    <section className="panel queue-panel participant-flow-panel">
      <div className="panel-heading">
        <span className="eyebrow">PARTICIPANTS</span>
        <h2>참가자 현황</h2>
      </div>

      <div className="participant-flow-container">
        <div className="flow-section">
          <h3>진행 중 <span>{groups.active.length}</span></h3>
          <div className="flow-list">
            {groups.active.map(renderUserRow)}
            {groups.active.length === 0 && <p className="empty-mini">활동 중인 유저 없음</p>}
          </div>
        </div>

        <div className="flow-section">
          <h3>대기열 <span>{groups.waiting.length}명</span></h3>
          <div className="flow-list">
            {groups.waiting.map(renderUserRow)}
            {groups.waiting.length === 0 && <p className="empty-mini">대기자 없음</p>}
          </div>
        </div>

        <div className="flow-section">
          <h3>완료 <span>{groups.finished.length}</span></h3>
          <div className="flow-list">
            {groups.finished.map(renderUserRow)}
            {groups.finished.length === 0 && <p className="empty-mini">완료자 없음</p>}
          </div>
        </div>
      </div>

      <div className="status-line-footer">
        <span>내 상태: <strong>{formatParticipantStatus(participant?.status)}</strong></span>
        {participant?.status === 'QUEUED' && snapshot.myQueuePosition !== null && (
          <span className="queue-position"> (대기 순서: <strong>{snapshot.myQueuePosition}번째</strong>)</span>
        )}
      </div>
    </section>
  );
}

