import { useCallback, useEffect, useState } from 'react';
import { createSimulation, fetchSimulation, runSimulation, type SimulationSnapshot } from '../api/simulationApi';
import { getDefaultSelectedUserId, isSimulationTerminal } from '../domain/simulationSelectors';

export function useSimulationDashboard(apiBaseUrl: string) {
  const [snapshot, setSnapshot] = useState<SimulationSnapshot | null>(null);
  const [simulationId, setSimulationId] = useState<string | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [lastCommandServer, setLastCommandServer] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!simulationId) {
      return;
    }
    try {
      const nextSnapshot = await fetchSimulation(apiBaseUrl, simulationId);
      setSnapshot(nextSnapshot);
      setSelectedUserId((current) => current ?? getDefaultSelectedUserId(nextSnapshot));
      setError(null);
    } catch {
      setError('스냅샷 조회에 실패했습니다.');
    }
  }, [apiBaseUrl, simulationId]);

  const startSimulation = useCallback(async (virtualUserCount: number, concurrency: number) => {
    setLoading(true);
    setError(null);
    try {
      const created = await createSimulation(apiBaseUrl, virtualUserCount);
      setSimulationId(created.simulationId);
      setLastCommandServer(created.handledBy);
      const run = await runSimulation(apiBaseUrl, created.simulationId, virtualUserCount, concurrency);
      setLastCommandServer(run.handledBy);
      const firstSnapshot = await fetchSimulation(apiBaseUrl, created.simulationId);
      setSnapshot(firstSnapshot);
      setSelectedUserId(getDefaultSelectedUserId(firstSnapshot));
    } catch {
      setError('시뮬레이션 시작에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl]);

  useEffect(() => {
    if (!simulationId || !snapshot) {
      return undefined;
    }
    const intervalMs = isSimulationTerminal(snapshot) ? 2000 : 500;
    const timer = window.setInterval(() => {
      void refresh();
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [refresh, simulationId, snapshot]);

  return {
    snapshot,
    selectedUserId,
    setSelectedUserId,
    lastCommandServer,
    loading,
    error,
    startSimulation,
    refresh,
  };
}
