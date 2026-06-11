import { MyTicketPanel } from './components/MyTicketPanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';
import { Sidebar } from './components/Sidebar';


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
      <div className="dashboard-container">
        <Sidebar activeTab="dashboard" snapshot={null} />
        <main className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <section className="panel empty-state">
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1>예매 이벤트를 불러오는 중입니다</h1>
            {room.error ? <p>{room.error}</p> : null}
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      <Sidebar
        activeTab="dashboard"
        snapshot={room.snapshot}
        onStart={(request) => void room.start(request)}
        onReset={() => void room.reset()}
      />

      <div className="main-content-wrapper">
        <main className="main-content">
          <header style={{ marginBottom: '24px' }}>
            <span className="eyebrow">LIVE CONSOLE</span>
            <h1 style={{ fontSize: '24px', fontWeight: '800', color: 'var(--text-primary)', marginTop: '4px' }}>{room.snapshot.title}</h1>
          </header>
          {room.error ? <div className="error-banner">{room.error}</div> : null}
          {room.message ? <div className="info-banner">{room.message}</div> : null}
          
          <div className="metric-strip" aria-label="실시간 이벤트 지표">
            <Metric label="SEATS RESERVED" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="예약 완료" />
            <Metric label="WAITING QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="대기 유저" />
            <Metric label="ACTIVE USERS" value={`${room.snapshot.activeConnections ?? 0}`} detail="실시간 커넥션" />
          </div>

          <div className="dashboard-hero-grid">
            <div className="panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', marginBottom: '16px', color: 'var(--text-primary)' }}>실시간 예매 좌석도</h3>
              <SeatMap
                status={room.snapshot.status}
                seats={room.snapshot.seats}
                participant={room.myParticipant}
                selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
                onSelectSeat={(seatId) => void room.selectSeat(seatId)}
                readOnly={true}
              />
            </div>
            <MyTicketPanel
              status={room.snapshot.status}
              participant={room.myParticipant}
              loading={room.loading}
              onJoin={() => void room.join(randomGuestName())}
              onReserve={openTicketingWindow}
              onPay={() => void room.pay()}
            />
          </div>
        </main>
      </div>
    </div>
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
