import { afterEach, describe, expect, it, vi } from 'vitest';
import { createSimulation, fetchSimulation, runSimulation } from './simulationApi';

describe('simulationApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates a simulation with selected virtual user count', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', message: 'ok', virtualUserCount: 150, handledBy: 'api-a' })),
    );

    const response = await createSimulation('http://localhost:8080', 150);

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ virtualUserCount: 150 }),
    });
    expect(response.handledBy).toBe('api-a');
  });

  it('uses same-origin api paths when base url is empty', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', message: 'ok', virtualUserCount: 30, handledBy: 'api-a' })),
    );

    await createSimulation('', 30);

    expect(fetchMock).toHaveBeenCalledWith('/api/simulations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ virtualUserCount: 30 }),
    });
  });

  it('starts a simulation run with count and concurrency', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', virtualUserCount: 150, status: 'RUNNING', handledBy: 'api-b' })),
    );

    const response = await runSimulation('http://localhost:8080', 'sim-1', 150, 50);

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations/sim-1/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ virtualUserCount: 150, concurrency: 50 }),
    });
    expect(response.status).toBe('RUNNING');
  });

  it('fetches a simulation snapshot', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ simulationId: 'sim-1', seats: [], users: [], metrics: {}, serverStats: [], running: true })),
    );

    const snapshot = await fetchSimulation('http://localhost:8080', 'sim-1');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/simulations/sim-1');
    expect(snapshot.simulationId).toBe('sim-1');
  });
});
