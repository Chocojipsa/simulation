import type { SeatView, ServerStatsView, SimulationMetrics, TimelineEntry } from './simulationApi';

export type ParticipantType = 'HUMAN' | 'AI';
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
}

export interface LiveEventResponse {
  eventId: string;
  title: string;
  status: string;
  opensAt: string;
  seatCount: number;
}

export interface LiveEventSnapshot {
  eventId: string;
  title: string;
  status: string;
  opensAt: string;
  seats: SeatView[];
  participants: EventParticipantView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
  myParticipantId: string | null;
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

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function fetchActiveEvent(apiBaseUrl: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/active`));
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
