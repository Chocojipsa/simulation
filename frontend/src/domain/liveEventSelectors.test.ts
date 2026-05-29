import { describe, expect, it } from 'vitest';
import type { EventParticipantView, LiveEventSnapshot } from '../api/liveEventApi';
import {
  canConfirmPayment,
  canReserve,
  canSelectSeat,
  formatParticipantStatus,
  getSeatHoldRemainingLabel,
  formatEventStatus,
  getMyParticipant,
  getQueuePosition,
  getTimeLabel,
} from './liveEventSelectors';

describe('liveEventSelectors', () => {
  it('finds my participant and derives actions', () => {
    const me: EventParticipantView = {
      id: 'me',
      displayName: '나',
      type: 'HUMAN',
      status: 'WAITING_ROOM',
      selectedSeatLabel: null,
      timeline: [],
      seatAttemptCount: 0,
      conflictCount: 0,
      paymentAttemptCount: 0,
      reservationId: null,
    };
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      generation: 1,
      opensAt: '2026-05-28T12:00:00Z',
      endsAt: '2026-05-28T12:05:00Z',
      seats: [],
      participants: [me],
      metrics: {
        queueSize: 0,
        admittedCount: 0,
        heldCount: 0,
        paymentInProgressCount: 0,
        reservedCount: 0,
        failedCount: 0,
      },
      serverStats: [],
      running: false,
      myParticipantId: 'me',
      myQueuePosition: null,
    };

    expect(getMyParticipant(snapshot, 'me')?.displayName).toBe('나');
    expect(canReserve(me)).toBe(true);
    expect(canConfirmPayment(me)).toBe(false);
    expect(formatEventStatus('READY')).toBe('시작 전');
    expect(formatEventStatus('COUNTDOWN')).toBe('오픈 대기');
    expect(formatEventStatus('OPEN')).toBe('예매 진행 중');
    expect(formatEventStatus('ENDED')).toBe('종료');
  });

  it('derives timers, queue position, and seat click rules', () => {
    const me: EventParticipantView = {
      id: 'me',
      displayName: '나',
      type: 'HUMAN',
      status: 'QUEUED',
      selectedSeatLabel: null,
      timeline: [],
      seatAttemptCount: 0,
      conflictCount: 0,
      paymentAttemptCount: 0,
      reservationId: null,
    };
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'COUNTDOWN',
      generation: 1,
      opensAt: '2026-05-28T12:01:00Z',
      endsAt: '2026-05-28T12:06:00Z',
      seats: [],
      participants: [me, { ...me, id: 'ai-1', displayName: 'AI-1', type: 'AI' }],
      metrics: {
        queueSize: 2,
        admittedCount: 0,
        heldCount: 0,
        paymentInProgressCount: 0,
        reservedCount: 0,
        failedCount: 0,
      },
      serverStats: [],
      running: false,
      myParticipantId: 'me',
      myQueuePosition: null,
    };

    expect(getTimeLabel('COUNTDOWN', snapshot.opensAt, snapshot.endsAt, new Date('2026-05-28T12:00:30Z'))).toBe('오픈까지 30초');
    expect(getTimeLabel('OPEN', snapshot.opensAt, snapshot.endsAt, new Date('2026-05-28T12:05:00Z'))).toBe('종료까지 60초');
    expect(getQueuePosition(snapshot, 'me')).toBe(1);
    expect(canSelectSeat('COUNTDOWN', me)).toEqual({ allowed: false, message: '예매가 아직 시작되지 않았습니다.' });
    expect(canSelectSeat('OPEN', me)).toEqual({ allowed: false, message: '대기열 대기 중입니다. 통과 후 좌석을 선택할 수 있습니다.' });
    expect(canSelectSeat('OPEN', { ...me, status: 'SELECTING_SEAT' })).toEqual({ allowed: true, message: null });
  });

  it('formats participant status and seat hold countdown for ticket panels', () => {
    expect(formatParticipantStatus('WAITING_ROOM')).toBe('입장 완료');
    expect(formatParticipantStatus('QUEUED')).toBe('대기열 대기');
    expect(formatParticipantStatus('SELECTING_SEAT')).toBe('좌석 선택 가능');
    expect(formatParticipantStatus('SEAT_HELD')).toBe('좌석 선점');
    expect(formatParticipantStatus('PAYMENT_IN_PROGRESS')).toBe('결제 확인 중');
    expect(formatParticipantStatus('RESERVED')).toBe('예매 완료');
    expect(getSeatHoldRemainingLabel('2026-05-28T12:01:00Z', new Date('2026-05-28T12:00:15Z'))).toBe('45초 남음');
    expect(getSeatHoldRemainingLabel('2026-05-28T12:00:00Z', new Date('2026-05-28T12:00:15Z'))).toBe('만료됨');
    expect(getSeatHoldRemainingLabel(null, new Date('2026-05-28T12:00:15Z'))).toBe('-');
  });
});
