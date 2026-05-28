import { describe, expect, it } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { canConfirmPayment, canReserve, formatEventStatus, getMyParticipant } from './liveEventSelectors';

describe('liveEventSelectors', () => {
  it('finds my participant and derives actions', () => {
    const snapshot = {
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
    } as LiveEventSnapshot;

    const me = getMyParticipant(snapshot, 'me');

    expect(me?.displayName).toBe('나');
    expect(canReserve(me)).toBe(true);
    expect(canConfirmPayment(me)).toBe(false);
    expect(formatEventStatus('OPEN')).toBe('예매 진행 중');
  });
});
