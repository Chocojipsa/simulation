import { useState, useEffect } from 'react';
import { useLiveEventRoom } from '../hooks/useLiveEventRoom';
import { QueuePanel } from './QueuePanel';
import { EventActivityPanel } from './EventActivityPanel';
import { Sidebar } from './Sidebar';
import { InsightPanel } from './InsightPanel';
import { fetchSystemMetrics, type SystemMetrics } from '../api/liveEventApi';

const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_BASE_URL) return import.meta.env.VITE_API_BASE_URL;
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.startsWith('192.168.')) {
      return 'https://ticket-api.chocojipsa.blog';
    }
  }
  return '';
};
const apiBaseUrl = getApiBaseUrl();

export function MonitoringConsole() {
  const room = useLiveEventRoom(apiBaseUrl);
  const [selectedParticipantId, setSelectedParticipantId] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<SystemMetrics | null>(null);

  useEffect(() => {
    if (!room.snapshot || (room.snapshot.status !== 'COUNTDOWN' && room.snapshot.status !== 'OPEN')) {
      setMetrics(null);
      return undefined;
    }

    let mounted = true;
    const loadMetrics = async () => {
      try {
        const data = await fetchSystemMetrics(apiBaseUrl);
        if (mounted) setMetrics(data);
      } catch (e) {
        // ignore errors
      }
    };
    loadMetrics();
    const interval = setInterval(loadMetrics, 5000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [room.snapshot?.status]);

  if (!room.snapshot) {
    return (
      <div className="dashboard-container">
        <Sidebar activeTab="monitoring" snapshot={null} />
        <main className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <section className="panel empty-state">
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1>이벤트를 불러오는 중입니다...</h1>
            {room.error ? <p>{room.error}</p> : null}
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="monitoring"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <main className="main-content">
        <header style={{ marginBottom: '24px' }}>
          <span className="eyebrow">LIVE CONSOLE</span>
          <h1 style={{ fontSize: '24px', fontWeight: '800', color: 'var(--text-primary)', marginTop: '4px' }}>{room.snapshot.title}</h1>
        </header>
        {room.error ? <div className="error-banner">{room.error}</div> : null}
        {room.message ? <div className="info-banner">{room.message}</div> : null}
        
        <div className="dashboard-grid">
          <QueuePanel
            snapshot={room.snapshot}
            participantId={room.participantId}
            selectedParticipantId={selectedParticipantId}
            onSelectParticipant={setSelectedParticipantId}
          />
          <EventActivityPanel
            snapshot={room.snapshot}
            participantId={room.participantId}
            selectedParticipantId={selectedParticipantId}
            onSelectParticipant={setSelectedParticipantId}
            apiBaseUrl={apiBaseUrl}
          />
        </div>

        {/* InsightPanel (서버 분산 및 시스템 인프라) */}
        <div className="insight-section" style={{ marginTop: '24px' }}>
          <InsightPanel snapshot={room.snapshot} metrics={metrics} />
        </div>
      </main>
    </div>
  );
}
