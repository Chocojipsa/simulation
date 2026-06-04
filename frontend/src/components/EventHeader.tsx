import { useEffect, useState } from 'react';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

interface EventHeaderProps {
  snapshot: LiveEventSnapshot;
  onStart: () => void;
  onReset: () => void;
}

export function EventHeader({ snapshot, onStart, onReset }: EventHeaderProps) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    if (snapshot.status !== 'COUNTDOWN' && snapshot.status !== 'OPEN') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setNow(new Date());
    }, 1000);
    return () => window.clearInterval(timer);
  }, [snapshot.status]);

  return (
    <header className="top-bar">
      <div className="event-title-block">
        <span className="eyebrow">LIVE CONSOLE</span>
        <h1>{snapshot.title}</h1>
        <div className="infra-tags" aria-label="시스템 구성">
          <span>nginx</span>
          <span>api-a/b</span>
          <span>redis</span>
          <span>postgres</span>
          <span>kafka</span>
        </div>
      </div>
      <div className="event-status">
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
        <span>{snapshot.metrics.reservedCount} reserved</span>
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
