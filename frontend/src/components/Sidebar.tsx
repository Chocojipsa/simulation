import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

// Module-level cache variables to preserve values across tab transitions
let cachedAiCount = 150;
let cachedAiConcurrency = 50;
let cachedAiSpeed: 'SLOW' | 'NORMAL' | 'FAST' = 'NORMAL';

interface SidebarProps {
  activeTab: 'dashboard' | 'monitoring';
  snapshot: LiveEventSnapshot | null;
  onStart?: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
  onReset?: () => void;
}

export function Sidebar({ activeTab, snapshot, onStart, onReset }: SidebarProps) {
  const [now, setNow] = useState(() => new Date());
  const [aiCount, setAiCount] = useState(cachedAiCount);
  const [aiConcurrency, setAiConcurrency] = useState(cachedAiConcurrency);
  const [aiSpeed, setAiSpeed] = useState(cachedAiSpeed);

  // Sync state changes with module cache variables
  useEffect(() => { cachedAiCount = aiCount; }, [aiCount]);
  useEffect(() => { cachedAiConcurrency = aiConcurrency; }, [aiConcurrency]);
  useEffect(() => { cachedAiSpeed = aiSpeed; }, [aiSpeed]);

  // Update time label every second when running
  useEffect(() => {
    if (!snapshot || (snapshot.status !== 'COUNTDOWN' && snapshot.status !== 'OPEN')) return undefined;
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, [snapshot?.status]);

  return (
    <aside className="sidebar">
      {/* Brand logo */}
      <div className="sidebar-brand">
        <span className="brand-logo">⏱️</span>
        <span className="brand-text">TIMEDEAL</span>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        <Link to="/" className={`sidebar-link ${activeTab === 'dashboard' ? 'active' : ''}`}>
          <span className="link-icon">D</span>
          <span className="link-text">대시보드</span>
        </Link>
        <Link to="/monitoring" className={`sidebar-link ${activeTab === 'monitoring' ? 'active' : ''}`}>
          <span className="link-icon">M</span>
          <span className="link-text">모니터링 콘솔</span>
        </Link>
      </nav>

      {/* Event controls at the bottom */}
      {snapshot && (
        <div className="sidebar-control-panel">
          <div className="sidebar-divider"></div>
          
          <div className="control-status">
            <span className="status-badge">{formatEventStatus(snapshot.status)}</span>
            <span className="status-time">{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt, now)}</span>
          </div>

          {snapshot.status === 'READY' && (
            <div className="control-form">
              <div className="control-field">
                <label htmlFor="sidebar-ai-count">AI 유저 수</label>
                <input
                  id="sidebar-ai-count"
                  type="number"
                  min={0}
                  max={1000}
                  value={aiCount}
                  onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                />
              </div>
              <div className="control-field">
                <label htmlFor="sidebar-ai-concurrency">동시 인입 수</label>
                <input
                  id="sidebar-ai-concurrency"
                  type="number"
                  min={1}
                  max={120}
                  value={aiConcurrency}
                  onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                />
              </div>
              <div className="control-field">
                <label htmlFor="sidebar-ai-speed">행동 속도</label>
                <select
                  id="sidebar-ai-speed"
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
                시뮬레이션 시작
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
      )}
    </aside>
  );
}
