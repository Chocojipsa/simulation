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
  paymentAttemptCount?: number;
  reservationId?: number | null;
  seatHoldExpiresAt?: string | null;
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
