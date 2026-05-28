import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./hooks/useLiveEventRoom', () => ({
  useLiveEventRoom: () => ({
    eventId: 'event-1',
    participantId: null,
    snapshot: {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'READY',
      generation: 1,
      opensAt: null,
      endsAt: null,
      seats: [{ id: 1, label: 'A-1', status: 'AVAILABLE' }],
      participants: [],
      metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 1, conflictCount: 0, successCount: 0 }],
      running: false,
      myParticipantId: null,
    },
    myParticipant: null,
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
  it('shows the start action prominently and disables entry before the event starts', () => {
    render(<App />);

    expect(screen.getByText('부산 콘서트 티켓팅')).toBeInTheDocument();
    expect(screen.getByText('시작 전')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이벤트 시작하기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /이벤트 입장/ })).toBeDisabled();
    expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
    expect(screen.getByText('대기열')).toBeInTheDocument();
    expect(screen.getByText('예매가 아직 시작되지 않았습니다.')).toBeInTheDocument();
  });
});
