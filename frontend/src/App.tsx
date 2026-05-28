import { EventActivityPanel } from './components/EventActivityPanel';
import { EventHeader } from './components/EventHeader';
import { MyTicketPanel } from './components/MyTicketPanel';
import { QueuePanel } from './components/QueuePanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export default function App() {
  const room = useLiveEventRoom(apiBaseUrl);

  if (!room.snapshot) {
    return (
      <main className="dashboard">
        <section className="panel empty-state">
          <h1>예매 이벤트를 불러오는 중입니다</h1>
          {room.error ? <p>{room.error}</p> : null}
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <EventHeader snapshot={room.snapshot} />
      {room.error ? <div className="error-banner">{room.error}</div> : null}
      {room.message ? <div className="info-banner">{room.message}</div> : null}
      <div className="dashboard-grid">
        <MyTicketPanel
          status={room.snapshot.status}
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join('나')}
          onReserve={() => void room.reserve()}
          onPay={() => void room.pay()}
        />
        <SeatMap
          status={room.snapshot.status}
          seats={room.snapshot.seats}
          participant={room.myParticipant}
          selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
          onSelectSeat={(seatId) => void room.selectSeat(seatId)}
        />
        <div className="side-column">
          <QueuePanel snapshot={room.snapshot} participantId={room.participantId} />
          <EventActivityPanel snapshot={room.snapshot} onStart={() => void room.start()} onReset={() => void room.reset()} />
        </div>
      </div>
    </main>
  );
}
