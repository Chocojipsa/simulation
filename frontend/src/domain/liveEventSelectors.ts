import type { EventParticipantView, LiveEventSnapshot, LiveEventStatus, ParticipantStatus } from '../api/liveEventApi';

export function getMyParticipant(snapshot: LiveEventSnapshot | null, participantId: string | null): EventParticipantView | null {
  if (!snapshot || !participantId) {
    return null;
  }
  return snapshot.participants.find((participant) => participant.id === participantId) ?? null;
}

export function canReserve(participant: EventParticipantView | null): boolean {
  return !participant
    || participant.status === 'WAITING_ROOM'
    || participant.status === 'PAYMENT_FAILED'
    || participant.status === 'EXPIRED';
}

export function canConfirmPayment(participant: EventParticipantView | null): boolean {
  return participant?.status === 'PAYMENT_IN_PROGRESS' || participant?.status === 'SEAT_HELD';
}

export function formatEventStatus(status: string): string {
  if (status === 'READY') return '시작 전';
  if (status === 'COUNTDOWN') return '오픈 대기';
  if (status === 'OPEN') return '예매 진행 중';
  if (status === 'ENDED') return '종료';
  return '준비 중';
}

export function formatParticipantStatus(status: ParticipantStatus | null | undefined): string {
  if (status === 'CREATED') return '생성됨';
  if (status === 'WAITING_ROOM') return '입장 완료';
  if (status === 'QUEUED') return '대기열 대기';
  if (status === 'ADMITTED' || status === 'SELECTING_SEAT') return '좌석 선택 가능';
  if (status === 'SEAT_HELD') return '좌석 선점';
  if (status === 'PAYMENT_IN_PROGRESS') return '결제 확인 중';
  if (status === 'PAYMENT_FAILED') return '결제 실패';
  if (status === 'RESERVED') return '예매 완료';
  if (status === 'FAILED') return '예매 실패';
  if (status === 'EXPIRED') return '만료';
  return '입장 전';
}

export function getSeatHoldRemainingLabel(expiresAt: string | null | undefined, now = new Date()): string {
  if (!expiresAt) {
    return '-';
  }
  const remainingSeconds = secondsUntil(expiresAt, now);
  return remainingSeconds > 0 ? `${remainingSeconds}초 남음` : '만료됨';
}

export function getTimeLabel(status: LiveEventStatus, opensAt: string | null, endsAt: string | null, now = new Date()): string {
  if (status === 'COUNTDOWN' && opensAt) {
    return `오픈까지 ${secondsUntil(opensAt, now)}초`;
  }
  if (status === 'OPEN' && endsAt) {
    return `종료까지 ${secondsUntil(endsAt, now)}초`;
  }
  if (status === 'ENDED') {
    return '이벤트 종료';
  }
  return '시작 대기 중';
}

export function getQueuePosition(snapshot: LiveEventSnapshot | null, participantId: string | null): number | null {
  if (!snapshot || !participantId) {
    return null;
  }
  const queued = snapshot.participants.filter((participant) => participant.status === 'QUEUED');
  const index = queued.findIndex((participant) => participant.id === participantId);
  return index >= 0 ? index + 1 : null;
}

export function canSelectSeat(
  status: LiveEventStatus,
  participant: EventParticipantView | null,
): { allowed: boolean; message: string | null } {
  if (status === 'READY' || status === 'COUNTDOWN') {
    return { allowed: false, message: '예매가 아직 시작되지 않았습니다.' };
  }
  if (status === 'ENDED') {
    return { allowed: false, message: '이벤트가 종료되었습니다.' };
  }
  if (!participant) {
    return { allowed: false, message: '이벤트에 입장해 주세요.' };
  }
  if (participant.status === 'SEAT_HELD' || participant.status === 'PAYMENT_IN_PROGRESS' || participant.status === 'RESERVED') {
    return { allowed: false, message: '이미 선점한 좌석이 있습니다.' };
  }
  if (participant.status === 'QUEUED') {
    return { allowed: false, message: '대기열 대기 중입니다. 통과 후 좌석을 선택할 수 있습니다.' };
  }
  if (participant.status !== 'SELECTING_SEAT') {
    return { allowed: false, message: '대기열 통과 후 좌석을 선택할 수 있습니다.' };
  }
  return { allowed: true, message: null };
}

function secondsUntil(target: string, now: Date): number {
  return Math.max(0, Math.ceil((new Date(target).getTime() - now.getTime()) / 1000));
}
