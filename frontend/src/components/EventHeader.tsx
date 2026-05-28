import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

interface EventHeaderProps {
  snapshot: LiveEventSnapshot;
  onStart: () => void;
  onReset: () => void;
}

export function EventHeader({ snapshot, onStart, onReset }: EventHeaderProps) {
  return (
    <header className="top-bar">
      <div>
        <h1>{snapshot.title}</h1>
        <p>nginx · api-a/api-b · Redis 대기열 · PostgreSQL 좌석 · Kafka 결제 · worker</p>
      </div>
      <div className="event-status">
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt)}</span>
        <span>예약 완료 {snapshot.metrics.reservedCount}명</span>
      </div>
      <div className="event-actions">
        {snapshot.status === 'READY' ? (
          <button className="header-action" onClick={onStart}>이벤트 시작하기</button>
        ) : null}
        {snapshot.status === 'ENDED' ? (
          <button className="header-action" onClick={onReset}>새 이벤트 시작</button>
        ) : null}
      </div>
    </header>
  );
}
