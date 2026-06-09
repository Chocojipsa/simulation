import { useEffect, useState } from 'react';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { Link, useLocation } from 'react-router-dom';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

interface EventHeaderProps {
  snapshot: LiveEventSnapshot;
  onStart: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
  onReset: () => void;
}

export function EventHeader({ snapshot, onStart, onReset }: EventHeaderProps) {
  const [now, setNow] = useState(() => new Date());
  const [aiCount, setAiCount] = useState<number>(150);
  const [aiConcurrency, setAiConcurrency] = useState<number>(50);
  const [aiSpeed, setAiSpeed] = useState<'SLOW' | 'NORMAL' | 'FAST'>('NORMAL');

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
        <div className="nav-links">
          <Link to="/" className={`nav-tab ${useLocation().pathname === '/' ? 'active' : ''}`}>Dashboard</Link>
          <Link to="/monitoring" className={`nav-tab ${useLocation().pathname === '/monitoring' ? 'active' : ''}`}>Monitoring Console</Link>
        </div>
      </div>
      <div className="event-status">
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
        <span>{snapshot.metrics.reservedCount} reserved</span>
      </div>
      <div className="event-actions">
        {snapshot.status === 'READY' ? (
          <div className="ai-config-toolbar" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <label htmlFor="ai-count-input" style={{ fontSize: '12px', fontWeight: '800' }}>AI 유저</label>
              <input
                id="ai-count-input"
                type="number"
                min={0}
                max={1000}
                value={aiCount}
                onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                style={{ width: '70px', padding: '6px', border: '2px solid var(--line)', fontFamily: 'monospace', fontWeight: '800' }}
              />
            </div>
            <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <label htmlFor="ai-concurrency-input" style={{ fontSize: '12px', fontWeight: '800' }}>동시성</label>
              <input
                id="ai-concurrency-input"
                type="number"
                min={1}
                max={120}
                value={aiConcurrency}
                onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                style={{ width: '60px', padding: '6px', border: '2px solid var(--line)', fontFamily: 'monospace', fontWeight: '800' }}
              />
            </div>
            <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <label htmlFor="ai-speed-select" style={{ fontSize: '12px', fontWeight: '800' }}>속도</label>
              <select
                id="ai-speed-select"
                value={aiSpeed}
                onChange={(e) => setAiSpeed(e.target.value as any)}
                style={{ padding: '6px', border: '2px solid var(--line)', fontWeight: '800', backgroundColor: 'var(--paper)' }}
              >
                <option value="SLOW">느림 (1.5초)</option>
                <option value="NORMAL">보통 (0.5초)</option>
                <option value="FAST">빠름 (0.1초)</option>
              </select>
            </div>
            <button 
              type="button"
              className="header-action" 
              onClick={() => onStart({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed })}
            >
              이벤트 시작하기
            </button>
          </div>
        ) : null}
        {snapshot.status === 'ENDED' ? (
          <button className="header-action" onClick={onReset}>새 이벤트 시작</button>
        ) : null}
      </div>
    </header>
  );
}
