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
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
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
    <header className="top-bar" style={{ position: 'relative' }}>
      <div className="event-title-block">
        <span className="eyebrow">LIVE CONSOLE</span>
        <h1>{snapshot.title}</h1>
        <div className="nav-links">
          <Link to="/" className={`nav-tab ${useLocation().pathname === '/' ? 'active' : ''}`}>Dashboard</Link>
          <Link to="/monitoring" className={`nav-tab ${useLocation().pathname === '/monitoring' ? 'active' : ''}`}>Monitoring Console</Link>
        </div>
      </div>
      <div className="event-status" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
        <span>{snapshot.metrics.reservedCount} reserved</span>
      </div>
      <div className="event-actions" style={{ position: 'relative' }}>
        {snapshot.status === 'READY' ? (
          <>
            <button 
              type="button" 
              className="btn btn-primary" 
              onClick={() => setIsSettingsOpen(!isSettingsOpen)}
            >
              이벤트 시작하기 {isSettingsOpen ? '▲' : '▼'}
            </button>
            {isSettingsOpen && (
              <div 
                className="panel" 
                style={{
                  position: 'absolute',
                  right: 0,
                  top: 'calc(100% + 8px)',
                  zIndex: 50,
                  width: '320px',
                  padding: '20px',
                  boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
                  background: '#FFFFFF',
                  border: '1px solid var(--border-line)',
                  borderRadius: 'var(--radius-lg)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '12px',
                  textAlign: 'left'
                }}
              >
                <h3 style={{ margin: 0, fontSize: '14px', fontWeight: '700', color: 'var(--text-primary)' }}>AI 시뮬레이션 설정</h3>
                
                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <label htmlFor="popover-ai-count" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>AI 유저 수</label>
                  <input
                    id="popover-ai-count"
                    type="number"
                    min={0}
                    max={1000}
                    value={aiCount}
                    onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                    style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                  />
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <label htmlFor="popover-ai-concurrency" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>동시 인입 수</label>
                  <input
                    id="popover-ai-concurrency"
                    type="number"
                    min={1}
                    max={120}
                    value={aiConcurrency}
                    onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                    style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}
                  />
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <label htmlFor="popover-ai-speed" style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)' }}>행동 속도</label>
                  <select
                    id="popover-ai-speed"
                    value={aiSpeed}
                    onChange={(e) => setAiSpeed(e.target.value as any)}
                    style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', fontSize: '13px', backgroundColor: '#FFFFFF' }}
                  >
                    <option value="SLOW">느림 (1.5초)</option>
                    <option value="NORMAL">보통 (0.5초)</option>
                    <option value="FAST">빠름 (0.1초)</option>
                  </select>
                </div>

                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => {
                    setIsSettingsOpen(false);
                    onStart({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed });
                  }}
                  style={{ width: '100%', marginTop: '8px' }}
                >
                  시뮬레이션 및 예매 시작
                </button>
              </div>
            )}
          </>
        ) : null}
        {snapshot.status === 'ENDED' ? (
          <button className="btn btn-primary" onClick={onReset}>새 이벤트 시작</button>
        ) : null}
      </div>
    </header>
  );
}
