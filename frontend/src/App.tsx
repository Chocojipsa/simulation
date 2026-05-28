import { EventActivityPanel } from './components/EventActivityPanel';
import { EventHeader } from './components/EventHeader';
import { MyTicketPanel } from './components/MyTicketPanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export default function App() {
  const room = useLiveEventRoom(apiBaseUrl);

  if (!room.snapshot) {
    return (
      <main className="dashboard">
        <section className="panel empty-state">
          <h1>티켓팅 이벤트를 불러오는 중입니다</h1>
          {room.error ? <p>{room.error}</p> : null}
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <EventHeader snapshot={room.snapshot} />
      {room.error ? <div className="error-banner">{room.error}</div> : null}
      <div className="dashboard-grid">
        <MyTicketPanel
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join('나')}
          onReserve={() => void room.reserve()}
          onPay={() => void room.pay()}
        />
        <SeatMap
          seats={room.snapshot.seats}
          selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
          onSelectSeat={(seatId) => void room.selectSeat(seatId)}
        />
        <EventActivityPanel snapshot={room.snapshot} onStartAi={() => void room.startAi(150, 50)} />
      </div>
    </main>
  );
}
