import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SimulationSnapshot } from './api/simulationApi';
import App from './App';

const mockSnapshot: SimulationSnapshot = {
  simulationId: 'sim-1',
  seats: Array.from({ length: 120 }, (_, index) => ({
    id: index + 1,
    label: `${String.fromCharCode(65 + Math.floor(index / 12))}-${(index % 12) + 1}`,
    status: index % 3 === 0 ? 'RESERVED' : 'AVAILABLE',
  })),
  users: [
    {
      id: 'u1',
      displayName: '사용자 1',
      status: 'FAILED',
      selectedSeatLabel: 'A-1',
      timeline: [{ label: '좌석 선택 실패', message: '이미 선택된 좌석입니다: A-1' }],
      seatAttemptCount: 30,
      conflictCount: 30,
    },
  ],
  metrics: { queueSize: 0, admittedCount: 96, heldCount: 0, paymentInProgressCount: 0, reservedCount: 96, failedCount: 54 },
  serverStats: [
    { serverId: 'api-a', requestCount: 595, conflictCount: 448, successCount: 70 },
    { serverId: 'api-b', requestCount: 595, conflictCount: 471, successCount: 50 },
  ],
  running: false,
};

const startSimulation = vi.fn();
const setSelectedUserId = vi.fn();

vi.mock('./hooks/useSimulationDashboard', () => ({
  useSimulationDashboard: () => ({
    snapshot: mockSnapshot,
    selectedUserId: 'u1',
    setSelectedUserId,
    lastCommandServer: 'api-b',
    loading: false,
    error: null,
    startSimulation,
    refresh: vi.fn(),
  }),
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the Korean operations dashboard', () => {
    render(<App />);

    expect(screen.getByText('분산 좌석 예매 시뮬레이터')).toBeInTheDocument();
    expect(screen.getByText('시뮬레이션 시작')).toBeInTheDocument();
    expect(screen.getByText('실시간 좌석표')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
    expect(screen.getByText('Redis 대기열')).toBeInTheDocument();
    expect(screen.getByText('Kafka 결제')).toBeInTheDocument();
    expect(screen.getByText('가상 사용자')).toBeInTheDocument();
  });

  it('shows scenario labels for portfolio demos', () => {
    render(<App />);

    expect(screen.getByText('가벼운 테스트')).toBeInTheDocument();
    expect(screen.getByText('충돌 확인')).toBeInTheDocument();
    expect(screen.getByText('고부하 데모')).toBeInTheDocument();
  });
});
