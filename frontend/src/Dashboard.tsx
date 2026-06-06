import { useState, useEffect } from 'react';
import { EventActivityPanel } from './components/EventActivityPanel';
import { EventHeader } from './components/EventHeader';
import { MyTicketPanel } from './components/MyTicketPanel';
import { QueuePanel } from './components/QueuePanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';
import { InsightPanel } from './components/InsightPanel';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export default function Dashboard() {
  const room = useLiveEventRoom(apiBaseUrl);
  const [selectedParticipantId, setSelectedParticipantId] = useState<string | null>(null);

  useEffect(() => {
    if (room.participantId && !selectedParticipantId) {
      setSelectedParticipantId(room.participantId);
    }
  }, [room.participantId, selectedParticipantId]);

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
      <EventHeader snapshot={room.snapshot} onStart={() => void room.start()} onReset={() => void room.reset()} />
      {room.error ? <div className="error-banner">{room.error}</div> : null}
      {room.message ? <div className="info-banner">{room.message}</div> : null}
      <div className="metric-strip" aria-label="실시간 이벤트 지표">
        <Metric label="SEATS" value={`${room.snapshot.metrics.reservedCount}/${room.snapshot.seats.length}`} detail="reserved" />
        <Metric label="QUEUE" value={`${room.snapshot.metrics.queueSize}`} detail="waiting" />
        <Metric label="HELD" value={`${room.snapshot.metrics.heldCount + room.snapshot.metrics.paymentInProgressCount}`} detail="checkout" />
        <Metric label="NODES" value={`${room.snapshot.serverStats.length}`} detail="active" />
      </div>
      <div className="dashboard-grid">
        <MyTicketPanel
          status={room.snapshot.status}
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join(randomGuestName())}
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
      </div>
      <InsightPanel snapshot={room.snapshot} />
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
