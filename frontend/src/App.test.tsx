import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./hooks/useLiveEventRoom', () => ({
  useLiveEventRoom: () => ({
    eventId: 'event-1',
    participantId: 'participant-1',
    snapshot: {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'READY',
      generation: 1,
      opensAt: null,
      endsAt: null,
      seats: [{ id: 1, label: 'A-1', status: 'AVAILABLE' }],
      participants: [{
        id: 'participant-1',
        displayName: '나',
        type: 'HUMAN',
        status: 'WAITING_ROOM',
        selectedSeatLabel: null,
        timeline: [],
        seatAttemptCount: 0,
        conflictCount: 0,
        paymentAttemptCount: 0,
        reservationId: null,
      }],
      metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 1, conflictCount: 0, successCount: 0 }],
      running: false,
      myParticipantId: 'participant-1',
    },
    myParticipant: {
      id: 'participant-1',
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
    loading: false,
    error: null,
    message: null,
    join: vi.fn(),
    reserve: vi.fn(),
    selectSeat: vi.fn(),
    pay: vi.fn(),
    start: vi.fn(),
    reset: vi.fn(),
    startAi: vi.fn(),
  }),
}));

describe('App', () => {
  it('renders user-started live event room', () => {
    render(<App />);

    expect(screen.getByText('부산 콘서트 티켓팅')).toBeInTheDocument();
    expect(screen.getByText('시작 전')).toBeInTheDocument();
    expect(screen.getByText('이벤트 시작하기')).toBeInTheDocument();
    expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
    expect(screen.getByText('대기열')).toBeInTheDocument();
    expect(screen.getByText('예매가 아직 시작되지 않았습니다.')).toBeInTheDocument();
  });
});
