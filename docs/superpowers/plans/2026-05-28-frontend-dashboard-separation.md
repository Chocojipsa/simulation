# Frontend Dashboard Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separated Korean React dashboard that visualizes the distributed seat reservation simulation through the existing Spring Boot API.

**Architecture:** Add a new `frontend/` Vite React TypeScript app. Keep the backend contract unchanged and call nginx/API through `VITE_API_BASE_URL`. Split frontend code into API client, domain selectors, Korean labels, and focused dashboard components.

**Tech Stack:** React 18, Vite, TypeScript, Vitest, Testing Library, CSS, existing Spring Boot API at `http://localhost:8080`.

---

## Current Contract

The frontend uses these endpoints:

```text
POST /api/simulations
POST /api/simulations/{simulationId}/run
GET  /api/simulations/{simulationId}
```

The main read model is:

```ts
export interface SimulationSnapshot {
  simulationId: string;
  seats: SeatView[];
  users: VirtualUserView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
}
```

## File Structure

- Create `frontend/package.json`: npm scripts and dependencies.
- Create `frontend/tsconfig.json`: TypeScript config.
- Create `frontend/tsconfig.node.json`: Vite config TypeScript support.
- Create `frontend/vite.config.ts`: React/Vitest configuration.
- Create `frontend/index.html`: app mount point.
- Create `frontend/.env.example`: local API base URL example.
- Create `frontend/src/main.tsx`: React entrypoint.
- Create `frontend/src/App.tsx`: page composition.
- Create `frontend/src/styles.css`: dashboard styling.
- Create `frontend/src/api/simulationApi.ts`: backend API client and DTO types.
- Create `frontend/src/api/simulationApi.test.ts`: API client tests.
- Create `frontend/src/domain/simulationSelectors.ts`: terminal detection, seat grouping, default selected user, status labels/colors.
- Create `frontend/src/domain/simulationSelectors.test.ts`: selector tests.
- Create `frontend/src/hooks/useSimulationDashboard.ts`: start/run/poll state hook.
- Create `frontend/src/hooks/useSimulationDashboard.test.tsx`: hook tests.
- Create `frontend/src/components/ControlPanel.tsx`: scenario controls.
- Create `frontend/src/components/SeatMap.tsx`: 10x12 seat grid.
- Create `frontend/src/components/InsightPanel.tsx`: server, Redis, Kafka, PostgreSQL metrics.
- Create `frontend/src/components/UserPanel.tsx`: user list and selected timeline.
- Create `frontend/src/App.test.tsx`: Korean dashboard smoke test.
- Modify `.gitignore`: ignore `frontend/node_modules` and `frontend/dist`.
- Create `docs/frontend-local-development.md`: local usage notes.

---

### Task 1: Scaffold React/Vite Frontend

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/.env.example`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/styles.css`
- Modify: `.gitignore`

- [ ] **Step 1: Create frontend package files**

Create `frontend/package.json`:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@vitejs/plugin-react": "^4.3.4",
    "vite": "^5.4.11",
    "typescript": "^5.6.3",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "lucide-react": "^0.468.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "jsdom": "^25.0.1",
    "vitest": "^2.1.5"
  }
}
```

Create `frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2020"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

Create `frontend/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 2: Create Vite config**

Create `frontend/vite.config.ts`:

```ts
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
```

- [ ] **Step 3: Create app entrypoint**

Create `frontend/index.html`:

```html
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>분산 좌석 예매 시뮬레이터</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `frontend/.env.example`:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Create `frontend/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

Create a minimal `frontend/src/App.tsx`:

```tsx
export default function App() {
  return (
    <main className="dashboard">
      <header className="top-bar">
        <div>
          <h1>분산 좌석 예매 시뮬레이터</h1>
          <p>nginx · api-a/api-b · Redis · PostgreSQL · Kafka · worker</p>
        </div>
      </header>
    </main>
  );
}
```

Create `frontend/src/styles.css`:

```css
:root {
  color: #1f2937;
  background: #eef2f6;
  font-family: Inter, Pretendard, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

button,
input {
  font: inherit;
}

.dashboard {
  min-height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  background: #263238;
  color: white;
}

.top-bar h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0;
}

.top-bar p {
  margin: 4px 0 0;
  color: #cfd8dc;
}
```

- [ ] **Step 4: Ignore frontend build artifacts**

Append to `.gitignore` if not already present:

```text
/frontend/node_modules/
/frontend/dist/
```

- [ ] **Step 5: Install dependencies and verify build**

Run:

```powershell
cd frontend
npm install
npm run build
```

Expected: `npm run build` succeeds and creates `frontend/dist`.

- [ ] **Step 6: Commit**

```powershell
git add .gitignore frontend/package.json frontend/package-lock.json frontend/tsconfig.json frontend/tsconfig.node.json frontend/vite.config.ts frontend/index.html frontend/.env.example frontend/src
git commit -m "feat: scaffold separated react frontend"
```

---

### Task 2: Add API Client And DTO Types

**Files:**
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/api/simulationApi.ts`
- Create: `frontend/src/api/simulationApi.test.ts`

- [ ] **Step 1: Add test setup**

Create `frontend/src/test/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 2: Write failing API client tests**

Create `frontend/src/api/simulationApi.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createSimulation, fetchSimulation, runSimulation } from './simulationApi';

describe('simulationApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates a simulation with selected virtual user count', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', message: 'ok', virtualUserCount: 150, handledBy: 'api-a' })),
    );

    const response = await createSimulation('http://localhost:8080', 150);

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ virtualUserCount: 150 }),
    });
    expect(response.handledBy).toBe('api-a');
  });

  it('starts a simulation run with count and concurrency', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', virtualUserCount: 150, status: 'RUNNING', handledBy: 'api-b' })),
    );

    const response = await runSimulation('http://localhost:8080', 'sim-1', 150, 50);

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations/sim-1/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ virtualUserCount: 150, concurrency: 50 }),
    });
    expect(response.status).toBe('RUNNING');
  });

  it('fetches a simulation snapshot', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', seats: [], users: [], metrics: {}, serverStats: [], running: true })),
    );

    const snapshot = await fetchSimulation('http://localhost:8080', 'sim-1');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations/sim-1');
    expect(snapshot.simulationId).toBe('sim-1');
  });
});
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
cd frontend
npm test -- simulationApi.test.ts
```

Expected: FAIL because `simulationApi.ts` does not exist.

- [ ] **Step 4: Implement API client**

Create `frontend/src/api/simulationApi.ts`:

```ts
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'PAYMENT_IN_PROGRESS' | 'RESERVED';
export type VirtualUserStatus = 'QUEUED' | 'SELECTING_SEAT' | 'PAYMENT_IN_PROGRESS' | 'RESERVED' | 'FAILED';

export interface SeatView {
  id: number;
  label: string;
  status: SeatStatus;
}

export interface TimelineEntry {
  label: string;
  message: string;
}

export interface VirtualUserView {
  id: string;
  displayName: string;
  status: VirtualUserStatus;
  selectedSeatLabel: string | null;
  timeline: TimelineEntry[];
  seatAttemptCount: number;
  conflictCount: number;
}

export interface SimulationMetrics {
  queueSize: number;
  admittedCount: number;
  heldCount: number;
  paymentInProgressCount: number;
  reservedCount: number;
  failedCount: number;
}

export interface ServerStatsView {
  serverId: string;
  requestCount: number;
  conflictCount: number;
  successCount: number;
}

export interface SimulationSnapshot {
  simulationId: string;
  seats: SeatView[];
  users: VirtualUserView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
}

export interface SimulationResponse {
  simulationId: string;
  message: string;
  virtualUserCount: number;
  handledBy: string;
}

export interface RunSimulationResponse {
  simulationId: string;
  virtualUserCount: number;
  status: string;
  handledBy: string;
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function createSimulation(apiBaseUrl: string, virtualUserCount: number): Promise<SimulationResponse> {
  const response = await fetch(`${apiBaseUrl}/api/simulations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ virtualUserCount }),
  });
  return readJson<SimulationResponse>(response);
}

export async function runSimulation(
  apiBaseUrl: string,
  simulationId: string,
  virtualUserCount: number,
  concurrency: number,
): Promise<RunSimulationResponse> {
  const response = await fetch(`${apiBaseUrl}/api/simulations/${simulationId}/run`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ virtualUserCount, concurrency }),
  });
  return readJson<RunSimulationResponse>(response);
}

export async function fetchSimulation(apiBaseUrl: string, simulationId: string): Promise<SimulationSnapshot> {
  const response = await fetch(`${apiBaseUrl}/api/simulations/${simulationId}`);
  return readJson<SimulationSnapshot>(response);
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
cd frontend
npm test -- simulationApi.test.ts
```

Expected: PASS.

Commit:

```powershell
git add frontend/src/test/setup.ts frontend/src/api
git commit -m "feat: add simulation api client"
```

---

### Task 3: Add Domain Selectors And Korean Labels

**Files:**
- Create: `frontend/src/domain/simulationSelectors.ts`
- Create: `frontend/src/domain/simulationSelectors.test.ts`

- [ ] **Step 1: Write failing selector tests**

Create `frontend/src/domain/simulationSelectors.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import type { SimulationSnapshot } from '../api/simulationApi';
import {
  countSeatsByStatus,
  getDefaultSelectedUserId,
  getSeatClassName,
  getUserStatusLabel,
  isSimulationTerminal,
  shortenId,
} from './simulationSelectors';

const snapshot: SimulationSnapshot = {
  simulationId: 'e318821a-8281-4820-b8cb-34c3c03c4e07',
  seats: [
    { id: 1, label: 'A-1', status: 'AVAILABLE' },
    { id: 2, label: 'A-2', status: 'RESERVED' },
    { id: 3, label: 'A-3', status: 'PAYMENT_IN_PROGRESS' },
  ],
  users: [
    { id: 'u1', displayName: '사용자 1', status: 'RESERVED', selectedSeatLabel: 'A-2', timeline: [], seatAttemptCount: 1, conflictCount: 0 },
    { id: 'u2', displayName: '사용자 2', status: 'FAILED', selectedSeatLabel: 'A-1', timeline: [], seatAttemptCount: 30, conflictCount: 30 },
  ],
  metrics: { queueSize: 0, admittedCount: 1, heldCount: 0, paymentInProgressCount: 1, reservedCount: 1, failedCount: 30 },
  serverStats: [],
  running: true,
};

describe('simulationSelectors', () => {
  it('detects terminal simulations', () => {
    expect(isSimulationTerminal(snapshot)).toBe(true);
    expect(isSimulationTerminal({ ...snapshot, users: [{ ...snapshot.users[0], status: 'QUEUED' }] })).toBe(false);
  });

  it('selects the user with highest conflict count first', () => {
    expect(getDefaultSelectedUserId(snapshot)).toBe('u2');
  });

  it('counts seats by status', () => {
    expect(countSeatsByStatus(snapshot.seats)).toEqual({
      AVAILABLE: 1,
      HELD: 0,
      PAYMENT_IN_PROGRESS: 1,
      RESERVED: 1,
    });
  });

  it('maps statuses to Korean labels and class names', () => {
    expect(getUserStatusLabel('FAILED')).toBe('실패');
    expect(getSeatClassName('PAYMENT_IN_PROGRESS')).toBe('seat seat-payment');
  });

  it('shortens simulation ids', () => {
    expect(shortenId(snapshot.simulationId)).toBe('e318821a');
  });
});
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
cd frontend
npm test -- simulationSelectors.test.ts
```

Expected: FAIL because selectors do not exist.

- [ ] **Step 3: Implement selectors**

Create `frontend/src/domain/simulationSelectors.ts`:

```ts
import type { SeatStatus, SeatView, SimulationSnapshot, VirtualUserStatus } from '../api/simulationApi';

export function isSimulationTerminal(snapshot: SimulationSnapshot): boolean {
  return snapshot.users.length > 0 && snapshot.users.every((user) => user.status === 'RESERVED' || user.status === 'FAILED');
}

export function getDefaultSelectedUserId(snapshot: SimulationSnapshot): string | null {
  if (snapshot.users.length === 0) {
    return null;
  }
  const sorted = [...snapshot.users].sort((left, right) => {
    if (right.conflictCount !== left.conflictCount) {
      return right.conflictCount - left.conflictCount;
    }
    if (left.status === 'FAILED' && right.status !== 'FAILED') {
      return -1;
    }
    if (right.status === 'FAILED' && left.status !== 'FAILED') {
      return 1;
    }
    return left.displayName.localeCompare(right.displayName, 'ko');
  });
  return sorted[0].id;
}

export function countSeatsByStatus(seats: SeatView[]): Record<SeatStatus, number> {
  return seats.reduce<Record<SeatStatus, number>>(
    (counts, seat) => {
      counts[seat.status] += 1;
      return counts;
    },
    { AVAILABLE: 0, HELD: 0, PAYMENT_IN_PROGRESS: 0, RESERVED: 0 },
  );
}

export function getUserStatusLabel(status: VirtualUserStatus): string {
  const labels: Record<VirtualUserStatus, string> = {
    QUEUED: '대기 중',
    SELECTING_SEAT: '좌석 선택',
    PAYMENT_IN_PROGRESS: '결제 중',
    RESERVED: '예약 완료',
    FAILED: '실패',
  };
  return labels[status];
}

export function getSeatClassName(status: SeatStatus, highlighted = false): string {
  const classes: Record<SeatStatus, string> = {
    AVAILABLE: 'seat seat-available',
    HELD: 'seat seat-held',
    PAYMENT_IN_PROGRESS: 'seat seat-payment',
    RESERVED: 'seat seat-reserved',
  };
  return highlighted ? `${classes[status]} seat-highlighted` : classes[status];
}

export function shortenId(id: string | null): string {
  return id ? id.slice(0, 8) : '-';
}
```

- [ ] **Step 4: Run tests and commit**

Run:

```powershell
cd frontend
npm test -- simulationSelectors.test.ts
```

Expected: PASS.

Commit:

```powershell
git add frontend/src/domain
git commit -m "feat: add simulation dashboard selectors"
```

---

### Task 4: Build Dashboard Components With Static Fixture

**Files:**
- Create: `frontend/src/components/ControlPanel.tsx`
- Create: `frontend/src/components/SeatMap.tsx`
- Create: `frontend/src/components/InsightPanel.tsx`
- Create: `frontend/src/components/UserPanel.tsx`
- Create: `frontend/src/App.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing smoke test**

Create `frontend/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the Korean operations dashboard', () => {
    render(<App />);

    expect(screen.getByText('분산 좌석 예매 시뮬레이터')).toBeInTheDocument();
    expect(screen.getByText('시뮬레이션 시작')).toBeInTheDocument();
    expect(screen.getByText('실시간 좌석표')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
    expect(screen.getByText('Redis 대기열')).toBeInTheDocument();
    expect(screen.getByText('Kafka 결제')).toBeInTheDocument();
    expect(screen.getByText('가상 사용자')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
cd frontend
npm test -- App.test.tsx
```

Expected: FAIL because components do not exist and current app only has a header.

- [ ] **Step 3: Create component interfaces**

Create `frontend/src/components/ControlPanel.tsx`:

```tsx
interface ControlPanelProps {
  virtualUserCount: number;
  concurrency: number;
  running: boolean;
  onVirtualUserCountChange: (value: number) => void;
  onConcurrencyChange: (value: number) => void;
  onStart: () => void;
}

export function ControlPanel({
  virtualUserCount,
  concurrency,
  running,
  onVirtualUserCountChange,
  onConcurrencyChange,
  onStart,
}: ControlPanelProps) {
  return (
    <section className="panel control-panel">
      <h2>시뮬레이션 제어</h2>
      <div className="segmented">
        {[30, 150, 300].map((value) => (
          <button key={value} className={virtualUserCount === value ? 'active' : ''} onClick={() => onVirtualUserCountChange(value)}>
            {value}명
          </button>
        ))}
      </div>
      <div className="segmented">
        {[10, 50, 100].map((value) => (
          <button key={value} className={concurrency === value ? 'active' : ''} onClick={() => onConcurrencyChange(value)}>
            동시성 {value}
          </button>
        ))}
      </div>
      <button className="primary-action" disabled={running} onClick={onStart}>
        시뮬레이션 시작
      </button>
    </section>
  );
}
```

Create `frontend/src/components/SeatMap.tsx`:

```tsx
import type { SeatView } from '../api/simulationApi';
import { getSeatClassName } from '../domain/simulationSelectors';

interface SeatMapProps {
  seats: SeatView[];
  selectedSeatLabel: string | null;
}

export function SeatMap({ seats, selectedSeatLabel }: SeatMapProps) {
  return (
    <section className="panel seat-map-panel">
      <div className="panel-heading">
        <h2>실시간 좌석표</h2>
        <span>STAGE</span>
      </div>
      <div className="seat-grid" aria-label="좌석표">
        {seats.map((seat) => (
          <button
            key={seat.id}
            className={getSeatClassName(seat.status, seat.label === selectedSeatLabel)}
            title={`${seat.label} ${seat.status}`}
          >
            {seat.label}
          </button>
        ))}
      </div>
    </section>
  );
}
```

Create `frontend/src/components/InsightPanel.tsx`:

```tsx
import type { SimulationSnapshot } from '../api/simulationApi';
import { countSeatsByStatus } from '../domain/simulationSelectors';

interface InsightPanelProps {
  snapshot: SimulationSnapshot;
}

export function InsightPanel({ snapshot }: InsightPanelProps) {
  const seatCounts = countSeatsByStatus(snapshot.seats);

  return (
    <aside className="insight-column">
      <section className="panel">
        <h2>서버 분산</h2>
        {snapshot.serverStats.map((stats) => (
          <div className="metric-row" key={stats.serverId}>
            <span>{stats.serverId}</span>
            <strong>{stats.requestCount}</strong>
            <small>충돌 {stats.conflictCount} · 성공 {stats.successCount}</small>
          </div>
        ))}
      </section>
      <section className="panel">
        <h2>Redis 대기열</h2>
        <div className="metric-row"><span>대기</span><strong>{snapshot.metrics.queueSize}</strong></div>
        <div className="metric-row"><span>입장</span><strong>{snapshot.metrics.admittedCount}</strong></div>
      </section>
      <section className="panel">
        <h2>Kafka 결제</h2>
        <div className="metric-row"><span>결제 중</span><strong>{snapshot.metrics.paymentInProgressCount}</strong></div>
        <div className="metric-row"><span>예약 완료</span><strong>{snapshot.metrics.reservedCount}</strong></div>
        <div className="metric-row"><span>실패</span><strong>{snapshot.metrics.failedCount}</strong></div>
      </section>
      <section className="panel">
        <h2>PostgreSQL 좌석 선점</h2>
        <div className="metric-row"><span>가능</span><strong>{seatCounts.AVAILABLE}</strong></div>
        <div className="metric-row"><span>예약</span><strong>{seatCounts.RESERVED}</strong></div>
      </section>
    </aside>
  );
}
```

Create `frontend/src/components/UserPanel.tsx`:

```tsx
import type { VirtualUserView } from '../api/simulationApi';
import { getUserStatusLabel } from '../domain/simulationSelectors';

interface UserPanelProps {
  users: VirtualUserView[];
  selectedUserId: string | null;
  onSelectUser: (userId: string) => void;
}

export function UserPanel({ users, selectedUserId, onSelectUser }: UserPanelProps) {
  const selectedUser = users.find((user) => user.id === selectedUserId) ?? users[0] ?? null;

  return (
    <section className="panel user-panel">
      <div className="panel-heading">
        <h2>가상 사용자</h2>
        <span>{users.length}명</span>
      </div>
      <div className="user-layout">
        <div className="user-list">
          {users.map((user) => (
            <button key={user.id} className={user.id === selectedUser?.id ? 'user-row active' : 'user-row'} onClick={() => onSelectUser(user.id)}>
              <span>{user.displayName}</span>
              <strong>{getUserStatusLabel(user.status)}</strong>
              <small>시도 {user.seatAttemptCount} · 충돌 {user.conflictCount}</small>
            </button>
          ))}
        </div>
        <div className="timeline">
          <h3>{selectedUser?.displayName ?? '선택된 사용자 없음'}</h3>
          {selectedUser?.timeline.map((entry, index) => (
            <div className="timeline-entry" key={`${entry.label}-${index}`}>
              <strong>{entry.label}</strong>
              <p>{entry.message}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Compose static dashboard**

Modify `frontend/src/App.tsx` to use a fixture snapshot:

```tsx
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
    { id: 'u1', displayName: '사용자 1', status: 'RESERVED', selectedSeatLabel: 'A-1', timeline: [{ label: '예약 완료', message: 'A-1 좌석 예약이 완료되었습니다.' }], seatAttemptCount: 1, conflictCount: 0 },
    { id: 'u2', displayName: '사용자 2', status: 'FAILED', selectedSeatLabel: 'H-9', timeline: [{ label: '좌석 선택 실패', message: '이미 선택된 좌석입니다: H-9' }], seatAttemptCount: 30, conflictCount: 30 },
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
```

- [ ] **Step 5: Add component CSS**

Append to `frontend/src/styles.css`:

```css
.dashboard-grid {
  display: grid;
  grid-template-columns: 280px minmax(420px, 1fr) 320px;
  gap: 12px;
  padding: 12px;
}

.panel {
  background: #ffffff;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  padding: 14px;
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-bottom: 10px;
}

.segmented button,
.primary-action,
.user-row {
  border: 1px solid #cfd8dc;
  background: #f7f9fb;
  border-radius: 6px;
  padding: 9px;
  cursor: pointer;
}

.segmented button.active,
.user-row.active {
  border-color: #2563eb;
  background: #e8f0ff;
}

.primary-action {
  width: 100%;
  background: #2563eb;
  color: white;
  border-color: #2563eb;
}

.seat-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(30px, 1fr));
  gap: 6px;
}

.seat {
  min-height: 30px;
  border: 0;
  border-radius: 5px;
  color: #111827;
  font-size: 11px;
}

.seat-available { background: #51c878; }
.seat-held { background: #60a5fa; }
.seat-payment { background: #facc15; }
.seat-reserved { background: #9ca3af; }
.seat-highlighted {
  outline: 3px solid #ef4444;
  outline-offset: 1px;
}

.insight-column {
  display: grid;
  gap: 12px;
}

.metric-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px;
  padding: 7px 0;
  border-top: 1px solid #edf1f5;
}

.metric-row small {
  grid-column: 1 / -1;
  color: #6b7280;
}

.user-panel {
  margin: 0 12px 12px;
}

.user-layout {
  display: grid;
  grid-template-columns: minmax(280px, 420px) 1fr;
  gap: 12px;
}

.user-list {
  display: grid;
  max-height: 260px;
  overflow: auto;
  gap: 6px;
}

.user-row {
  display: grid;
  grid-template-columns: 1fr auto;
  text-align: left;
}

.user-row small {
  grid-column: 1 / -1;
  color: #6b7280;
}

.timeline-entry {
  border-left: 3px solid #2563eb;
  padding: 4px 0 8px 10px;
}

.timeline-entry p {
  margin: 4px 0 0;
  color: #4b5563;
}

@media (max-width: 1100px) {
  .dashboard-grid,
  .user-layout {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
cd frontend
npm test -- App.test.tsx
npm run build
```

Expected: PASS and build succeeds.

Commit:

```powershell
git add frontend/src
git commit -m "feat: build static simulation dashboard"
```

---

### Task 5: Wire Live Simulation Start And Polling

**Files:**
- Create: `frontend/src/hooks/useSimulationDashboard.ts`
- Create: `frontend/src/hooks/useSimulationDashboard.test.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Write failing hook tests**

Create `frontend/src/hooks/useSimulationDashboard.test.tsx`:

```tsx
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/simulationApi';
import { useSimulationDashboard } from './useSimulationDashboard';

describe('useSimulationDashboard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates, runs, and loads the first snapshot', async () => {
    vi.spyOn(api, 'createSimulation').mockResolvedValue({ simulationId: 'sim-1', message: 'created', virtualUserCount: 150, handledBy: 'api-a' });
    vi.spyOn(api, 'runSimulation').mockResolvedValue({ simulationId: 'sim-1', virtualUserCount: 150, status: 'RUNNING', handledBy: 'api-b' });
    vi.spyOn(api, 'fetchSimulation').mockResolvedValue({
      simulationId: 'sim-1',
      seats: [],
      users: [],
      metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [],
      running: true,
    });

    const { result } = renderHook(() => useSimulationDashboard('http://localhost:8080'));

    await act(async () => {
      await result.current.startSimulation(150, 50);
    });

    await waitFor(() => expect(result.current.snapshot?.simulationId).toBe('sim-1'));
    expect(result.current.lastCommandServer).toBe('api-b');
  });

  it('keeps last snapshot when polling fails', async () => {
    vi.spyOn(api, 'createSimulation').mockResolvedValue({ simulationId: 'sim-1', message: 'created', virtualUserCount: 30, handledBy: 'api-a' });
    vi.spyOn(api, 'runSimulation').mockResolvedValue({ simulationId: 'sim-1', virtualUserCount: 30, status: 'RUNNING', handledBy: 'api-b' });
    vi.spyOn(api, 'fetchSimulation')
      .mockResolvedValueOnce({
        simulationId: 'sim-1',
        seats: [],
        users: [],
        metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
        serverStats: [],
        running: true,
      })
      .mockRejectedValueOnce(new Error('network'));

    const { result } = renderHook(() => useSimulationDashboard('http://localhost:8080'));

    await act(async () => {
      await result.current.startSimulation(30, 10);
    });
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.snapshot?.simulationId).toBe('sim-1');
    expect(result.current.error).toBe('스냅샷 조회에 실패했습니다.');
  });
});
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
cd frontend
npm test -- useSimulationDashboard.test.tsx
```

Expected: FAIL because hook does not exist.

- [ ] **Step 3: Implement hook**

Create `frontend/src/hooks/useSimulationDashboard.ts`:

```ts
import { useCallback, useEffect, useState } from 'react';
import { createSimulation, fetchSimulation, runSimulation, type SimulationSnapshot } from '../api/simulationApi';
import { getDefaultSelectedUserId, isSimulationTerminal } from '../domain/simulationSelectors';

export function useSimulationDashboard(apiBaseUrl: string) {
  const [snapshot, setSnapshot] = useState<SimulationSnapshot | null>(null);
  const [simulationId, setSimulationId] = useState<string | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [lastCommandServer, setLastCommandServer] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!simulationId) {
      return;
    }
    try {
      const nextSnapshot = await fetchSimulation(apiBaseUrl, simulationId);
      setSnapshot(nextSnapshot);
      setSelectedUserId((current) => current ?? getDefaultSelectedUserId(nextSnapshot));
      setError(null);
    } catch {
      setError('스냅샷 조회에 실패했습니다.');
    }
  }, [apiBaseUrl, simulationId]);

  const startSimulation = useCallback(async (virtualUserCount: number, concurrency: number) => {
    setLoading(true);
    setError(null);
    try {
      const created = await createSimulation(apiBaseUrl, virtualUserCount);
      setSimulationId(created.simulationId);
      setLastCommandServer(created.handledBy);
      const run = await runSimulation(apiBaseUrl, created.simulationId, virtualUserCount, concurrency);
      setLastCommandServer(run.handledBy);
      const firstSnapshot = await fetchSimulation(apiBaseUrl, created.simulationId);
      setSnapshot(firstSnapshot);
      setSelectedUserId(getDefaultSelectedUserId(firstSnapshot));
    } catch {
      setError('시뮬레이션 시작에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl]);

  useEffect(() => {
    if (!simulationId || !snapshot) {
      return;
    }
    const intervalMs = isSimulationTerminal(snapshot) ? 2000 : 500;
    const timer = window.setInterval(() => {
      void refresh();
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [refresh, simulationId, snapshot]);

  return {
    snapshot,
    selectedUserId,
    setSelectedUserId,
    lastCommandServer,
    loading,
    error,
    startSimulation,
    refresh,
  };
}
```

- [ ] **Step 4: Wire hook into App**

Modify `frontend/src/App.tsx`:

```tsx
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
```

Append CSS:

```css
.error-banner {
  margin: 12px 12px 0;
  padding: 10px 12px;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  background: #fff1f2;
  color: #991b1b;
}

.empty-state {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
```

- [ ] **Step 5: Update App smoke test to use a mocked live snapshot**

Replace `frontend/src/App.test.tsx` with:

```tsx
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SimulationSnapshot } from './api/simulationApi';
import App from './App';

const mockSnapshot: SimulationSnapshot = {
  simulationId: 'sim-1',
  seats: Array.from({ length: 120 }, (_, index) => ({
    id: index + 1,
    label: `${String.fromCharCode(65 + Math.floor(index / 12))}-${(index % 12) + 1}`,
    status: index % 3 === 0 ? 'RESERVED' : 'AVAILABLE',
  })),
  users: [
    {
      id: 'u1',
      displayName: '사용자 1',
      status: 'FAILED',
      selectedSeatLabel: 'A-1',
      timeline: [{ label: '좌석 선택 실패', message: '이미 선택된 좌석입니다: A-1' }],
      seatAttemptCount: 30,
      conflictCount: 30,
    },
  ],
  metrics: { queueSize: 0, admittedCount: 96, heldCount: 0, paymentInProgressCount: 0, reservedCount: 96, failedCount: 54 },
  serverStats: [
    { serverId: 'api-a', requestCount: 595, conflictCount: 448, successCount: 70 },
    { serverId: 'api-b', requestCount: 595, conflictCount: 471, successCount: 50 },
  ],
  running: false,
};

const startSimulation = vi.fn();
const setSelectedUserId = vi.fn();

vi.mock('./hooks/useSimulationDashboard', () => ({
  useSimulationDashboard: () => ({
    snapshot: mockSnapshot,
    selectedUserId: 'u1',
    setSelectedUserId,
    lastCommandServer: 'api-b',
    loading: false,
    error: null,
    startSimulation,
    refresh: vi.fn(),
  }),
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the Korean operations dashboard', () => {
    render(<App />);

    expect(screen.getByText('분산 좌석 예매 시뮬레이터')).toBeInTheDocument();
    expect(screen.getByText('시뮬레이션 시작')).toBeInTheDocument();
    expect(screen.getByText('실시간 좌석표')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
    expect(screen.getByText('Redis 대기열')).toBeInTheDocument();
    expect(screen.getByText('Kafka 결제')).toBeInTheDocument();
    expect(screen.getByText('가상 사용자')).toBeInTheDocument();
  });

});
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: PASS and build succeeds.

Commit:

```powershell
git add frontend/src
git commit -m "feat: connect dashboard to simulation api"
```

---

### Task 6: Polish Dashboard Usability And Responsive Layout

**Files:**
- Modify: `frontend/src/components/ControlPanel.tsx`
- Modify: `frontend/src/components/SeatMap.tsx`
- Modify: `frontend/src/components/UserPanel.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Add test for visible scenario labels**

Modify `frontend/src/App.test.tsx`:

```tsx
it('shows scenario labels for portfolio demos', () => {
  render(<App />);

  expect(screen.getByText('가벼운 테스트')).toBeInTheDocument();
  expect(screen.getByText('충돌 확인')).toBeInTheDocument();
  expect(screen.getByText('고부하 데모')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
cd frontend
npm test -- App.test.tsx
```

Expected: FAIL because scenario labels do not exist.

- [ ] **Step 3: Add scenario labels and accessible seat legend**

Update `ControlPanel` count buttons:

```tsx
const scenarios = [
  { count: 30, label: '가벼운 테스트' },
  { count: 150, label: '충돌 확인' },
  { count: 300, label: '고부하 데모' },
];
```

Render:

```tsx
{scenarios.map((scenario) => (
  <button
    key={scenario.count}
    className={virtualUserCount === scenario.count ? 'active' : ''}
    onClick={() => onVirtualUserCountChange(scenario.count)}
  >
    <strong>{scenario.count}명</strong>
    <span>{scenario.label}</span>
  </button>
))}
```

Update `SeatMap` above the grid:

```tsx
<div className="seat-legend">
  <span><i className="legend-available" /> 선택 가능</span>
  <span><i className="legend-payment" /> 결제 중</span>
  <span><i className="legend-reserved" /> 예약 완료</span>
</div>
```

- [ ] **Step 4: Add CSS for polish**

Append:

```css
.segmented button {
  display: grid;
  gap: 2px;
}

.segmented button span {
  font-size: 11px;
  color: #6b7280;
}

.seat-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 10px;
  color: #4b5563;
  font-size: 12px;
}

.seat-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.seat-legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.legend-available { background: #51c878; }
.legend-payment { background: #facc15; }
.legend-reserved { background: #9ca3af; }
```

- [ ] **Step 5: Run build and visual check**

Run:

```powershell
cd frontend
npm test
npm run build
npm run dev
```

Open `http://localhost:5173`.

Expected:

- no text overlap at desktop width
- seat grid is visible
- Korean labels render correctly
- buttons fit their containers

- [ ] **Step 6: Commit**

```powershell
git add frontend/src
git commit -m "feat: polish dashboard presentation"
```

---

### Task 7: Add Local Development Documentation

**Files:**
- Create: `docs/frontend-local-development.md`

- [ ] **Step 1: Create docs**

Create `docs/frontend-local-development.md`:

```md
# Frontend Local Development

## Purpose

The React frontend is the primary portfolio dashboard for the distributed seat reservation simulation.

## Start Backend Infrastructure

```powershell
cd infra
docker compose up -d --build
docker compose restart nginx
```

The API is available through nginx:

```text
http://localhost:8080
```

## Start Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

## Environment

Create `frontend/.env.local` when needed:

```text
VITE_API_BASE_URL=http://localhost:8080
```

## Demo Scenario

Use:

- 150 virtual users
- concurrency 50

Expected dashboard result:

- `api-a` and `api-b` both show request counts
- Redis queue reaches 0 when completed
- Kafka payment metrics move through payment/reserved states
- PostgreSQL reservation outcome is visible through seat counts
- users end as `예약 완료` or `실패`
```

- [ ] **Step 2: Commit docs**

```powershell
git add docs/frontend-local-development.md
git commit -m "docs: add frontend local development guide"
```

---

### Task 8: Final Verification

**Files:**
- No new files unless verification finds defects.

- [ ] **Step 1: Run frontend checks**

```powershell
cd frontend
npm test
npm run build
```

Expected: both pass.

- [ ] **Step 2: Run backend smoke checks**

```powershell
cd backend
.\gradlew.bat test --tests "*SimulationControllerTest" --tests "*SimulationServiceTest"
```

Expected: tests pass.

- [ ] **Step 3: Run local stack**

```powershell
cd infra
docker compose up -d --build
docker compose restart nginx
```

Expected: `api-a`, `api-b`, `worker`, `traffic-generator`, `postgres`, `redis`, `kafka`, and `nginx` are running or healthy.

- [ ] **Step 4: Run dashboard manually**

```powershell
cd frontend
npm run dev
```

Open `http://localhost:5173` and start:

- virtual users: `150`
- concurrency: `50`

Expected:

- dashboard starts the simulation without Postman or PowerShell API calls
- seat map changes
- `api-a` and `api-b` request counts both increase
- user panel shows attempt and conflict counts
- final user statuses are `예약 완료` or `실패`
- no browser console errors

- [ ] **Step 5: Commit any final fixes**

If fixes were needed:

```powershell
git add frontend docs
git commit -m "fix: stabilize frontend dashboard"
```

If no fixes were needed, do not create an empty commit.
