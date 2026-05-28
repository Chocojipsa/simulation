import { useMemo, useState } from 'react';
import type { SimulationSnapshot } from './api/simulationApi';
import { ControlPanel } from './components/ControlPanel';
import { InsightPanel } from './components/InsightPanel';
import { SeatMap } from './components/SeatMap';
import { UserPanel } from './components/UserPanel';
import { getDefaultSelectedUserId, shortenId } from './domain/simulationSelectors';

const fixtureSnapshot: SimulationSnapshot = {
  simulationId: 'preview-simulation',
  seats: Array.from({ length: 120 }, (_, index) => ({
    id: index + 1,
    label: `${String.fromCharCode(65 + Math.floor(index / 12))}-${(index % 12) + 1}`,
    status: index % 5 === 0 ? 'RESERVED' : index % 17 === 0 ? 'PAYMENT_IN_PROGRESS' : 'AVAILABLE',
  })),
  users: [
    {
      id: 'u1',
      displayName: '사용자 1',
      status: 'RESERVED',
      selectedSeatLabel: 'A-1',
      timeline: [{ label: '예약 완료', message: 'A-1 좌석 예약이 완료되었습니다.' }],
      seatAttemptCount: 1,
      conflictCount: 0,
    },
    {
      id: 'u2',
      displayName: '사용자 2',
      status: 'FAILED',
      selectedSeatLabel: 'H-9',
      timeline: [{ label: '좌석 선택 실패', message: '이미 선택된 좌석입니다: H-9' }],
      seatAttemptCount: 30,
      conflictCount: 30,
    },
  ],
  metrics: { queueSize: 0, admittedCount: 96, heldCount: 0, paymentInProgressCount: 0, reservedCount: 96, failedCount: 54 },
  serverStats: [
    { serverId: 'api-a', requestCount: 595, conflictCount: 448, successCount: 70 },
    { serverId: 'api-b', requestCount: 595, conflictCount: 471, successCount: 50 },
    { serverId: 'worker', requestCount: 120, conflictCount: 0, successCount: 96 },
  ],
  running: false,
};

export default function App() {
  const [virtualUserCount, setVirtualUserCount] = useState(150);
  const [concurrency, setConcurrency] = useState(50);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(() => getDefaultSelectedUserId(fixtureSnapshot));
  const selectedUser = useMemo(
    () => fixtureSnapshot.users.find((user) => user.id === selectedUserId) ?? null,
    [selectedUserId],
  );

  return (
    <main className="dashboard">
      <header className="top-bar">
        <div>
          <h1>분산 좌석 예매 시뮬레이터</h1>
          <p>nginx · api-a/api-b · Redis · PostgreSQL · Kafka · worker</p>
        </div>
        <div className="simulation-id">시뮬레이션 {shortenId(fixtureSnapshot.simulationId)}</div>
      </header>
      <div className="dashboard-grid">
        <ControlPanel
          virtualUserCount={virtualUserCount}
          concurrency={concurrency}
          running={fixtureSnapshot.running}
          onVirtualUserCountChange={setVirtualUserCount}
          onConcurrencyChange={setConcurrency}
          onStart={() => undefined}
        />
        <SeatMap seats={fixtureSnapshot.seats} selectedSeatLabel={selectedUser?.selectedSeatLabel ?? null} />
        <InsightPanel snapshot={fixtureSnapshot} />
      </div>
      <UserPanel users={fixtureSnapshot.users} selectedUserId={selectedUserId} onSelectUser={setSelectedUserId} />
    </main>
  );
}
