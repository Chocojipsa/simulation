import { useState, useEffect } from 'react';
import type { LiveEventSnapshot } from '../api/liveEventApi';

let cachedAiCount = 150;
let cachedAiConcurrency = 50;
let cachedAiSpeed: 'SLOW' | 'NORMAL' | 'FAST' = 'NORMAL';

interface EventControlPanelProps {
  snapshot: LiveEventSnapshot;
  onStart?: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
  onReset?: () => void;
  className?: string;
}

function getTimerLabelText(status: string) {
  if (status === 'COUNTDOWN') return '오픈까지';
  if (status === 'OPEN') return '남은 시간';
  if (status === 'ENDED') return '이벤트 상태';
  return '이벤트 상태';
}

function getTimerValueText(status: string, opensAt: string | null, endsAt: string | null, now: Date) {
  const secondsUntil = (target: string) => {
    return Math.max(0, Math.ceil((new Date(target).getTime() - now.getTime()) / 1000));
  };
  if (status === 'COUNTDOWN' && opensAt) {
    return `${secondsUntil(opensAt)}초`;
  }
  if (status === 'OPEN' && endsAt) {
    return `${secondsUntil(endsAt)}초`;
  }
  if (status === 'ENDED') {
    return '종료됨';
  }
  return '시작 전';
}

export function EventControlPanel({ snapshot, onStart, onReset, className = '' }: EventControlPanelProps) {
  const [now, setNow] = useState(() => new Date());
  const [aiCount, setAiCount] = useState(cachedAiCount);
  const [aiConcurrency, setAiConcurrency] = useState(cachedAiConcurrency);
  const [aiSpeed, setAiSpeed] = useState(cachedAiSpeed);

  useEffect(() => { cachedAiCount = aiCount; }, [aiCount]);
  useEffect(() => { cachedAiConcurrency = aiConcurrency; }, [aiConcurrency]);
  useEffect(() => { cachedAiSpeed = aiSpeed; }, [aiSpeed]);

  useEffect(() => {
    if (snapshot.status !== 'COUNTDOWN' && snapshot.status !== 'OPEN') return undefined;
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, [snapshot.status]);

  return (
    <div className={`sidebar-control-panel ${className}`}>
      {className !== 'panel' && <div className="sidebar-divider"></div>}
      
      <div className="control-status">
        <div className="status-timer-card">
          <span className="timer-label">{getTimerLabelText(snapshot.status)}</span>
          <span className="timer-value">{getTimerValueText(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
          {(snapshot.status === 'OPEN' || snapshot.status === 'ENDED') && (
            <span className="timer-subtext">
              예약 완료: {snapshot.metrics.reservedCount} / {snapshot.seats.length}
            </span>
          )}
        </div>
      </div>

      {snapshot.status === 'READY' && (
        <div className="control-form">
          <div className="control-field">
            <label htmlFor="event-ai-count">AI 유저 수</label>
            <input
              id="event-ai-count"
              type="number"
              min={0}
              max={1000}
              value={aiCount}
              onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
            />
          </div>
          <div className="control-field">
            <label htmlFor="event-ai-concurrency">동시 인입 수</label>
            <input
              id="event-ai-concurrency"
              type="number"
              min={1}
              max={120}
              value={aiConcurrency}
              onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
            />
          </div>
          <div className="control-field">
            <label htmlFor="event-ai-speed">행동 속도</label>
            <select
              id="event-ai-speed"
              value={aiSpeed}
              onChange={(e) => setAiSpeed(e.target.value as any)}
            >
              <option value="SLOW">느림 (1.5초)</option>
              <option value="NORMAL">보통 (0.5초)</option>
              <option value="FAST">빠름 (0.1초)</option>
            </select>
          </div>
          <button
            type="button"
            className="btn btn-primary control-btn"
            onClick={() => onStart?.({ aiUserCount: aiCount, aiConcurrency, aiSpeed })}
          >
            이벤트 시작하기
          </button>
        </div>
      )}

      {snapshot.status === 'ENDED' && (
        <button
          type="button"
          className="btn btn-primary control-btn"
          onClick={onReset}
        >
          새 이벤트 시작
        </button>
      )}
    </div>
  );
}
