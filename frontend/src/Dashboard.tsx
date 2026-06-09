import { useState, useEffect } from 'react';
import { fetchSystemMetrics, type SystemMetrics } from './api/liveEventApi';
import { EventHeader } from './components/EventHeader';
import { MyTicketPanel } from './components/MyTicketPanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';
import { InsightPanel } from './components/InsightPanel';

const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.startsWith('192.168.')) {
      return 'https://ticket-api.chocojipsa.blog';
    }
  }
  return '';
};
const apiBaseUrl = getApiBaseUrl();

export default function Dashboard() {
  const room = useLiveEventRoom(apiBaseUrl);
  const [metrics, setMetrics] = useState<SystemMetrics | null>(null);

  useEffect(() => {
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
  }, []);


  const openTicketingWindow = () => {
    if (!room.eventId) return;
    const url = `/ticketing/${room.eventId}`;
    const win = window.open(url, 'TimedealTicketingWindow', 'width=900,height=700,status=no,menubar=no,toolbar=no');
    if (win) {
      win.focus();
    }
  };

  if (!room.snapshot) {
    return (
      <main className="dashboard">
        <section className="panel empty-state">
          <span className="eyebrow">LIVE CONSOLE</span>
          <h1>예매 이벤트를 불러오는 중입니다</h1>
          {room.error ? <p>{room.error}</p> : null}
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <EventHeader snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />
      {room.error ? <div className="error-banner">{room.error}</div> : null}
      {room.message ? <div className="info-banner">{room.message}</div> : null}
      <div className="metric-strip" aria-label="실시간 이벤트 지표">
        <Metric label="SEATS" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="reserved" />
        <Metric label="QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="waiting" />
        <Metric label="TPS" value={`${metrics ? metrics.tps.toFixed(1) : '0.0'}`} detail="transactions/s" />
        <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="connected" />
      </div>
      <div style={{ marginBottom: '16px' }}>
        <SeatMap
          status={room.snapshot.status}
          seats={room.snapshot.seats}
          participant={room.myParticipant}
          selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
          onSelectSeat={(seatId) => void room.selectSeat(seatId)}
          readOnly={true}
        />
      </div>
      <div className="dashboard-grid" style={{ gridTemplateColumns: 'minmax(230px, 280px) 1fr', gap: '16px' }}>
        <MyTicketPanel
          status={room.snapshot.status}
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join(randomGuestName())}
          onReserve={openTicketingWindow}
          onPay={() => void room.pay()}
        />
        <InsightPanel snapshot={room.snapshot} metrics={metrics} />
      </div>
    </main>
  );
}

function Metric({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <div className="metric-tile">
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}

function randomGuestName() {
  return `게스트-${Math.floor(1000 + Math.random() * 9000)}`;
}
