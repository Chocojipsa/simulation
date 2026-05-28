import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus } from '../domain/liveEventSelectors';

interface EventHeaderProps {
  snapshot: LiveEventSnapshot;
}

export function EventHeader({ snapshot }: EventHeaderProps) {
  return (
    <header className="top-bar">
      <div>
        <h1>{snapshot.title}</h1>
        <p>nginx · api-a/api-b · Redis 대기열 · PostgreSQL 좌석 · Kafka 결제 · worker</p>
      </div>
      <div className="event-status">
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>예약 완료 {snapshot.metrics.reservedCount}석</span>
      </div>
    </header>
  );
}
