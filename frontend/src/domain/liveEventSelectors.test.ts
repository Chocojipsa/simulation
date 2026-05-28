import { describe, expect, it } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { canConfirmPayment, canReserve, formatEventStatus, getMyParticipant } from './liveEventSelectors';

describe('liveEventSelectors', () => {
  it('finds my participant and derives actions', () => {
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      opensAt: '2026-05-28T12:00:00Z',
      seats: [],
      participants: [
        {
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
        },
      ],
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
    };

    const me = getMyParticipant(snapshot, 'me');

    expect(me?.displayName).toBe('나');
    expect(canReserve(me)).toBe(true);
    expect(canConfirmPayment(me)).toBe(false);
    expect(formatEventStatus('OPEN')).toBe('예매 진행 중');
  });
});
