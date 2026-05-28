import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/simulationApi';
import { useSimulationDashboard } from './useSimulationDashboard';

describe('useSimulationDashboard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates, runs, and loads the first snapshot', async () => {
    vi.spyOn(api, 'createSimulation').mockResolvedValue({ simulationId: 'sim-1', message: 'created', virtualUserCount: 150, handledBy: 'api-a' });
    vi.spyOn(api, 'runSimulation').mockResolvedValue({ simulationId: 'sim-1', virtualUserCount: 150, status: 'RUNNING', handledBy: 'api-b' });
    vi.spyOn(api, 'fetchSimulation').mockResolvedValue({
      simulationId: 'sim-1',
      seats: [],
      users: [],
      metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [],
      running: true,
    });

    const { result } = renderHook(() => useSimulationDashboard('http://localhost:8080'));

    await act(async () => {
      await result.current.startSimulation(150, 50);
    });

    await waitFor(() => expect(result.current.snapshot?.simulationId).toBe('sim-1'));
    expect(result.current.lastCommandServer).toBe('api-b');
  });

  it('keeps last snapshot when polling fails', async () => {
    vi.spyOn(api, 'createSimulation').mockResolvedValue({ simulationId: 'sim-1', message: 'created', virtualUserCount: 30, handledBy: 'api-a' });
    vi.spyOn(api, 'runSimulation').mockResolvedValue({ simulationId: 'sim-1', virtualUserCount: 30, status: 'RUNNING', handledBy: 'api-b' });
    vi.spyOn(api, 'fetchSimulation')
      .mockResolvedValueOnce({
        simulationId: 'sim-1',
        seats: [],
        users: [],
        metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
        serverStats: [],
        running: true,
      })
      .mockRejectedValueOnce(new Error('network'));

    const { result } = renderHook(() => useSimulationDashboard('http://localhost:8080'));

    await act(async () => {
      await result.current.startSimulation(30, 10);
    });
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.snapshot?.simulationId).toBe('sim-1');
    expect(result.current.error).toBe('스냅샷 조회에 실패했습니다.');
  });
});
