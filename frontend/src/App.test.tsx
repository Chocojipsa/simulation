import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

vi.mock('./hooks/useLiveEventRoom', () => ({
  useLiveEventRoom: () => ({
    eventId: 'event-1',
    participantId: null,
    snapshot: {
      eventId: 'event-1',
      title: '티켓팅 시뮬레이터',
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
      myQueuePosition: null,
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
  it('shows the start action prominently on the dashboard before the event starts', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /티켓팅/ })).toBeInTheDocument();
    expect(screen.getByText('LIVE CONSOLE')).toBeInTheDocument();
    expect(screen.getByText('SEATS RESERVED')).toBeInTheDocument();
    expect(screen.getAllByText('WAITING QUEUE').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('시작 전')[0]).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /이벤트 시작하기/ })[0]).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /대시보드/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /모니터링/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '예약하기' })).toBeDisabled();
    expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
    expect(screen.queryByText('예매가 아직 시작되지 않았습니다.')).not.toBeInTheDocument();
    expect(screen.queryByText('서버 분산')).not.toBeInTheDocument();
  });

  it('shows the monitoring console route with active participant panels', () => {
    render(
      <MemoryRouter initialEntries={['/monitoring']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /티켓팅/ })).toBeInTheDocument();
    expect(screen.getByText('LIVE CONSOLE')).toBeInTheDocument();
    expect(screen.getByText('시작 전')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /이벤트 시작하기/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /대시보드/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /모니터링/ })).toBeInTheDocument();
    expect(screen.getByText('참가자 현황')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
  });
});

