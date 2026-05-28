import { useState } from 'react';
import { ControlPanel } from './components/ControlPanel';
import { InsightPanel } from './components/InsightPanel';
import { SeatMap } from './components/SeatMap';
import { UserPanel } from './components/UserPanel';
import { shortenId } from './domain/simulationSelectors';
import { useSimulationDashboard } from './hooks/useSimulationDashboard';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export default function App() {
  const [virtualUserCount, setVirtualUserCount] = useState(150);
  const [concurrency, setConcurrency] = useState(50);
  const dashboard = useSimulationDashboard(apiBaseUrl);
  const selectedUser = dashboard.snapshot?.users.find((user) => user.id === dashboard.selectedUserId) ?? null;

  return (
    <main className="dashboard">
      <header className="top-bar">
        <div>
          <h1>분산 좌석 예매 시뮬레이터</h1>
          <p>nginx · api-a/api-b · Redis · PostgreSQL · Kafka · worker</p>
        </div>
        <div className="simulation-id">
          시뮬레이션 {shortenId(dashboard.snapshot?.simulationId ?? null)}
          {dashboard.lastCommandServer ? ` · 최근 처리 ${dashboard.lastCommandServer}` : ''}
        </div>
      </header>
      {dashboard.error ? <div className="error-banner">{dashboard.error}</div> : null}
      <div className="dashboard-grid">
        <ControlPanel
          virtualUserCount={virtualUserCount}
          concurrency={concurrency}
          running={dashboard.loading || Boolean(dashboard.snapshot?.running)}
          onVirtualUserCountChange={setVirtualUserCount}
          onConcurrencyChange={setConcurrency}
          onStart={() => void dashboard.startSimulation(virtualUserCount, concurrency)}
        />
        {dashboard.snapshot ? (
          <>
            <SeatMap seats={dashboard.snapshot.seats} selectedSeatLabel={selectedUser?.selectedSeatLabel ?? null} />
            <InsightPanel snapshot={dashboard.snapshot} />
          </>
        ) : (
          <section className="panel empty-state">
            <h2>실시간 좌석표</h2>
            <p>시뮬레이션을 시작하면 좌석, 대기열, 서버 분산 지표가 표시됩니다.</p>
          </section>
        )}
      </div>
      {dashboard.snapshot ? (
        <UserPanel
          users={dashboard.snapshot.users}
          selectedUserId={dashboard.selectedUserId}
          onSelectUser={dashboard.setSelectedUserId}
        />
      ) : null}
    </main>
  );
}
