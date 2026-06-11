import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import type { LiveEventSnapshot } from '../api/liveEventApi';

const mockSnapshot = (status: 'READY' | 'OPEN' | 'ENDED'): LiveEventSnapshot => ({
  eventId: 'event-1',
  title: '시뮬레이션',
  status,
  generation: 1,
  opensAt: null,
  endsAt: null,
  seats: [],
  participants: [],
  metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
  serverStats: [],
  running: false,
  myParticipantId: null,
  myQueuePosition: null,
});

describe('Sidebar', () => {
  it('renders branding and nav links', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={null} />
      </MemoryRouter>
    );
    expect(screen.getByText('TIMEDEAL')).toBeInTheDocument();
    expect(screen.getByText('대시보드')).toBeInTheDocument();
    expect(screen.getByText('모니터링 콘솔')).toBeInTheDocument();
  });

  it('renders simulation form when status is READY', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={mockSnapshot('READY')} />
      </MemoryRouter>
    );
    expect(screen.getByLabelText('AI 유저 수')).toBeInTheDocument();
    expect(screen.getByLabelText('동시 인입 수')).toBeInTheDocument();
    expect(screen.getByLabelText('행동 속도')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이벤트 시작하기' })).toBeInTheDocument();
  });

  it('renders reset button when status is ENDED', () => {
    render(
      <MemoryRouter>
        <Sidebar activeTab="dashboard" snapshot={mockSnapshot('ENDED')} />
      </MemoryRouter>
    );
    expect(screen.getByRole('button', { name: '새 이벤트 시작' })).toBeInTheDocument();
  });
});
