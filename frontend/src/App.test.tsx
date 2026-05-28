import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./hooks/useLiveEventRoom', () => ({
  useLiveEventRoom: () => ({
    snapshot: {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      opensAt: '2026-05-28T12:00:00Z',
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
      myParticipantId: null,
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
    join: vi.fn(),
    reserve: vi.fn(),
    selectSeat: vi.fn(),
    pay: vi.fn(),
    startAi: vi.fn(),
  }),
}));

describe('App', () => {
  it('renders Korean live event room', () => {
    render(<App />);

    expect(screen.getByText('부산 콘서트 티켓팅')).toBeInTheDocument();
    expect(screen.getByText('예매 진행 중')).toBeInTheDocument();
    expect(screen.getByText('예약하기')).toBeInTheDocument();
    expect(screen.getByText('AI 참가자 시작')).toBeInTheDocument();
  });
});
