import type { EventParticipantView, LiveEventSnapshot } from '../api/liveEventApi';

export function getMyParticipant(snapshot: LiveEventSnapshot | null, participantId: string | null): EventParticipantView | null {
  if (!snapshot || !participantId) {
    return null;
  }
  return snapshot.participants.find((participant) => participant.id === participantId) ?? null;
}

export function canReserve(participant: EventParticipantView | null): boolean {
  return !participant || participant.status === 'WAITING_ROOM' || participant.status === 'PAYMENT_FAILED';
}

export function canConfirmPayment(participant: EventParticipantView | null): boolean {
  return participant?.status === 'PAYMENT_IN_PROGRESS' || participant?.status === 'SEAT_HELD';
}

export function formatEventStatus(status: string): string {
  if (status === 'OPEN') return '예매 진행 중';
  if (status === 'COUNTDOWN') return '오픈 대기';
  if (status === 'ENDED') return '종료';
  return '준비 중';
}
