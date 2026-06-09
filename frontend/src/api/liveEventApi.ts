import type { SeatView, ServerStatsView, SimulationMetrics, TimelineEntry } from './simulationApi';

export type ParticipantType = 'HUMAN' | 'AI';
export type LiveEventStatus = 'READY' | 'COUNTDOWN' | 'OPEN' | 'ENDED';
export type ParticipantStatus =
  | 'CREATED'
  | 'WAITING_ROOM'
  | 'QUEUED'
  | 'ADMITTED'
  | 'SELECTING_SEAT'
  | 'SEAT_HELD'
  | 'PAYMENT_IN_PROGRESS'
  | 'PAYMENT_FAILED'
  | 'RESERVED'
  | 'FAILED'
  | 'EXPIRED';

export interface EventParticipantView {
  id: string;
  displayName: string;
  type: ParticipantType;
  status: ParticipantStatus;
  selectedSeatLabel: string | null;
  timeline: TimelineEntry[];
  seatAttemptCount: number;
  conflictCount: number;
  paymentAttemptCount: number;
  reservationId: number | null;
  seatHoldExpiresAt?: string | null;
}

export interface LiveEventResponse {
  eventId: string;
  title: string;
  status: LiveEventStatus;
  generation: number;
  opensAt: string | null;
  endsAt: string | null;
  seatCount: number;
}

export interface LiveEventSnapshot {
  eventId: string;
  title: string;
  status: LiveEventStatus;
  generation: number;
  opensAt: string | null;
  endsAt: string | null;
  seats: SeatView[];
  participants: EventParticipantView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
  myParticipantId: string | null;
  myQueuePosition: number | null;
  activeConnections?: number;
  admissionsAvailable?: number;
}

export interface SystemMetrics {
  kafkaLag: number;
  redisLockCount: number;
  tps: number;
  avgResponseTimeMs: number;
  serverStats: ServerStatsView[];
}

export interface JoinEventResponse {
  eventId: string;
  participantId: string;
  displayName: string;
  status: string;
  handledBy: string;
}

export interface CommandResponse {
  eventId?: string;
  simulationId?: string;
  participantId?: string;
  virtualUserId?: string;
  status: string;
  message: string;
  selectedSeatLabel?: string | null;
  handledBy: string;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = 'ApiError';
  }
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError(response.status, `API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function fetchActiveEvent(apiBaseUrl: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/active`));
}

export interface StartEventRequest {
  aiUserCount?: number;
  aiConcurrency?: number;
  aiSpeed?: 'SLOW' | 'NORMAL' | 'FAST';
}

export async function startEvent(
  apiBaseUrl: string,
  eventId: string,
  request?: StartEventRequest
): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request || {}),
  }));
}

export async function resetEvent(apiBaseUrl: string, eventId: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/reset`, { method: 'POST' }));
}

export async function fetchEventSnapshot(apiBaseUrl: string, eventId: string, participantId: string | null): Promise<LiveEventSnapshot> {
  const query = participantId ? `?participantId=${participantId}` : '';
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/snapshot${query}`));
}

export async function joinEvent(apiBaseUrl: string, eventId: string, displayName: string): Promise<JoinEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  }));
}

export async function queueParticipant(apiBaseUrl: string, eventId: string, participantId: string): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/queue`, { method: 'POST' }));
}

export async function holdSeat(apiBaseUrl: string, eventId: string, participantId: string, seatId: number): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/seats/${seatId}/hold`, { method: 'POST' }));
}

export async function confirmPayment(apiBaseUrl: string, eventId: string, participantId: string): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/payment-confirm`, { method: 'POST' }));
}

export async function startAiParticipants(
  apiBaseUrl: string,
  eventId: string,
  participantCount: number,
  concurrency: number,
): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/ai/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ participantCount, concurrency }),
  }));
}

export async function releaseSeat(apiBaseUrl: string, eventId: string, participantId: string): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/seats/release`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  if (!response.ok) {
    throw new ApiError(response.status, `API request failed: ${response.status}`);
  }
}

export async function fetchSystemMetrics(apiBaseUrl: string): Promise<SystemMetrics> {
  return readJson(await fetch(`${apiBaseUrl}/api/system/metrics`));
}

export async function updateParticipantName(
  apiBaseUrl: string,
  eventId: string,
  participantId: string,
  displayName: string,
): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/name`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  }));
}

export function normalizeSnapshot(snapshot: any, prevSnapshot?: LiveEventSnapshot | null): LiveEventSnapshot {
  if (!snapshot) return null as any;

  const isSimulation = 'simulationId' in snapshot && 'users' in snapshot;

  const eventId = isSimulation ? snapshot.simulationId : (snapshot.eventId || '');
  const title = snapshot.title || prevSnapshot?.title || '콘서트 예매';
  const status = snapshot.status || prevSnapshot?.status || (snapshot.running ? 'OPEN' : 'READY');
  const generation = snapshot.generation !== undefined ? snapshot.generation : (prevSnapshot?.generation ?? 0);
  const opensAt = snapshot.opensAt || prevSnapshot?.opensAt || null;
  const endsAt = snapshot.endsAt || prevSnapshot?.endsAt || null;
  
  const seats = snapshot.seats || prevSnapshot?.seats || [];
  const participants = isSimulation ? (snapshot.users || []) : (snapshot.participants || prevSnapshot?.participants || []);
  
  const metrics = snapshot.metrics || prevSnapshot?.metrics || {
    queueSize: 0,
    admittedCount: 0,
    heldCount: 0,
    paymentInProgressCount: 0,
    reservedCount: 0,
    failedCount: 0
  };
  
  const serverStats = snapshot.serverStats || prevSnapshot?.serverStats || [];
  const running = typeof snapshot.running === 'boolean' ? snapshot.running : (prevSnapshot?.running ?? false);
  const myParticipantId = snapshot.myParticipantId !== undefined ? snapshot.myParticipantId : (prevSnapshot?.myParticipantId ?? null);
  const myQueuePosition = snapshot.myQueuePosition !== undefined ? snapshot.myQueuePosition : (prevSnapshot?.myQueuePosition ?? null);
  const activeConnections = snapshot.activeConnections !== undefined ? snapshot.activeConnections : (prevSnapshot?.activeConnections ?? 0);
  const admissionsAvailable = snapshot.admissionsAvailable !== undefined ? snapshot.admissionsAvailable : (prevSnapshot?.admissionsAvailable ?? 0);

  return {
    eventId,
    title,
    status,
    generation,
    opensAt,
    endsAt,
    seats,
    participants,
    metrics,
    serverStats,
    running,
    myParticipantId,
    myQueuePosition,
    activeConnections,
    admissionsAvailable
  };
}

