import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/liveEventApi';
import { useLiveEventRoom } from './useLiveEventRoom';

const emptySnapshot = {
  eventId: 'event-1',
  title: '부산 콘서트 티켓팅',
  status: 'OPEN',
  generation: 1,
  opensAt: '2026-05-28T12:00:00Z',
  endsAt: '2026-05-28T12:05:00Z',
  seats: [],
  participants: [],
  metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
  serverStats: [],
  running: false,
  myParticipantId: null,
} satisfies api.LiveEventSnapshot;

describe('useLiveEventRoom', () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads active event and stores joined participant identity', async () => {
    vi.spyOn(api, 'fetchActiveEvent').mockResolvedValue({
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      generation: 1,
      opensAt: '2026-05-28T12:00:00Z',
      endsAt: '2026-05-28T12:05:00Z',
      seatCount: 120,
    });
    vi.spyOn(api, 'fetchEventSnapshot')
      .mockResolvedValueOnce(emptySnapshot)
      .mockResolvedValueOnce({
        ...emptySnapshot,
        myParticipantId: 'participant-1',
        participants: [{
          id: 'participant-1',
          displayName: '권',
          type: 'HUMAN',
          status: 'WAITING_ROOM',
          selectedSeatLabel: null,
          timeline: [],
          seatAttemptCount: 0,
          conflictCount: 0,
          paymentAttemptCount: 0,
          reservationId: null,
        }],
      });
    vi.spyOn(api, 'joinEvent').mockResolvedValue({
      eventId: 'event-1',
      participantId: 'participant-1',
      displayName: '권',
      status: 'WAITING_ROOM',
      handledBy: 'api-a',
    });

    const { result } = renderHook(() => useLiveEventRoom(''));

    await waitFor(() => expect(result.current.eventId).toBe('event-1'));
    await act(async () => {
      await result.current.join('권');
    });

    expect(result.current.participantId).toBe('participant-1');
    expect(result.current.myParticipant?.displayName).toBe('권');
    expect(window.localStorage.getItem('timedeal.participantId')).toBe('participant-1');
  });
});
