import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu } from 'lucide-react';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus } from '../domain/liveEventSelectors';
import { EventControlPanel } from './EventControlPanel';

interface SidebarProps {
  activeTab: 'dashboard' | 'monitoring';
  snapshot: LiveEventSnapshot | null;
  onStart?: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
  onReset?: () => void;
}

export function Sidebar({ activeTab, snapshot, onStart, onReset }: SidebarProps) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <div className="mobile-header">
        <button type="button" className="menu-toggle-btn" onClick={() => setIsOpen(!isOpen)} aria-label="Toggle menu">
          <Menu size={24} />
        </button>
        <div className="sidebar-brand">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>
        <div style={{ width: 40 }}></div>
      </div>

      {isOpen && <div className="sidebar-backdrop" onClick={() => setIsOpen(false)} />}

      <aside className={`sidebar ${isOpen ? 'open' : ''}`}>
        {/* Brand logo */}
        <div className="sidebar-brand desktop-only">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>

        {/* Navigation */}
        <nav className="sidebar-nav">
          <Link to="/" className={`sidebar-link ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => setIsOpen(false)}>
            <span className="link-icon">D</span>
            <span className="link-text">대시보드</span>
          </Link>
          <Link to="/monitoring" className={`sidebar-link ${activeTab === 'monitoring' ? 'active' : ''}`} onClick={() => setIsOpen(false)}>
            <span className="link-icon">M</span>
            <span className="link-text">모니터링 콘솔</span>
          </Link>
        </nav>

      {/* Event controls at the bottom */}
      {snapshot && (
        <EventControlPanel
          snapshot={snapshot}
          onStart={onStart}
          onReset={onReset}
        />
      )}
      </aside>
    </>
  );
}
