import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  confirmPayment,
  fetchActiveEvent,
  fetchEventSnapshot,
  holdSeat,
  joinEvent,
  queueParticipant,
  resetEvent,
  startAiParticipants,
  startEvent,
  type CommandResponse,
  type LiveEventSnapshot,
} from '../api/liveEventApi';
import { getMyParticipant } from '../domain/liveEventSelectors';

const participantStorageKey = 'timedeal.participantId';

export function useLiveEventRoom(apiBaseUrl: string) {
  const [eventId, setEventId] = useState<string | null>(null);
  const [participantId, setParticipantId] = useState<string | null>(() => window.localStorage.getItem(participantStorageKey));
  const [snapshot, setSnapshot] = useState<LiveEventSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [sseActive, setSseActive] = useState(true);

  const refreshingRef = useRef(false);

  const refresh = useCallback(async () => {
    if (!eventId || refreshingRef.current) return;
    refreshingRef.current = true;
    try {
      const next = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
      setSnapshot(next);
    } finally {
      refreshingRef.current = false;
    }
  }, [apiBaseUrl, eventId, participantId]);

  useEffect(() => {
    void fetchActiveEvent(apiBaseUrl).then((event) => {
      setEventId(event.eventId);
    });
  }, [apiBaseUrl]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!eventId) return undefined;
    let intervalMs = sseActive ? 10000 : 500;
    if (snapshot?.status === 'ENDED') {
      intervalMs = 30000;
    }
    const timer = window.setInterval(() => {
      void refresh();
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [eventId, refresh, sseActive, snapshot?.status]);

  useEffect(() => {
    if (error) {
      const timer = window.setTimeout(() => setError(null), 4000);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [error]);

  useEffect(() => {
    if (message) {
      const timer = window.setTimeout(() => setMessage(null), 4000);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [message]);

  useEffect(() => {
    if (!eventId) return undefined;
    if (typeof EventSource === 'undefined') {
      setSseActive(false);
      return undefined;
    }
    
    const streamUrl = `${apiBaseUrl}/api/events/${eventId}/stream`;
    const eventSource = new EventSource(streamUrl);

    eventSource.onopen = () => {
      setSseActive(true);
    };

    eventSource.onerror = () => {
      setSseActive(false);
    };

    eventSource.addEventListener('snapshot', (event) => {
      try {
        const nextSnapshot = JSON.parse(event.data) as LiveEventSnapshot;
        setSnapshot(nextSnapshot);
        setError(null);
        setSseActive(true);
      } catch (err) {
        console.error('Failed to parse snapshot event', err);
      }
    });

    return () => {
      eventSource.close();
    };
  }, [apiBaseUrl, eventId]);

  const myParticipant = useMemo(() => getMyParticipant(snapshot, participantId), [snapshot, participantId]);

  useEffect(() => {
    if (!eventId || !participantId || snapshot?.status !== 'OPEN' || myParticipant?.status !== 'QUEUED') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void queueParticipant(apiBaseUrl, eventId, participantId).then((res) => {
        if (res.status === 'ADMITTED') {
          void refresh();
        }
      });
    }, 1500);
    return () => window.clearInterval(timer);
  }, [apiBaseUrl, eventId, participantId, snapshot?.status, myParticipant?.status, refresh]);

  const join = useCallback(async (displayName: string) => {
    if (!eventId) return;
    setLoading(true);
    try {
      const res = await joinEvent(apiBaseUrl, eventId, displayName);
      window.localStorage.setItem(participantStorageKey, res.participantId);
      setParticipantId(res.participantId);
      await refresh();
    } catch (err) {
      setError('입장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl, eventId, refresh]);

  const wrapCommand = useCallback(async (command: () => Promise<CommandResponse>) => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const res = await command();
      setMessage(res.message);
      await refresh();
    } catch (err) {
      setError('요청 처리에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  const reserve = useCallback(() => {
    if (!eventId || !participantId) return;
    void wrapCommand(() => queueParticipant(apiBaseUrl, eventId, participantId));
  }, [apiBaseUrl, eventId, participantId, wrapCommand]);

  const selectSeat = useCallback((seatId: number) => {
    if (!eventId || !participantId) return;
    void wrapCommand(() => holdSeat(apiBaseUrl, eventId, participantId, seatId));
  }, [apiBaseUrl, eventId, participantId, wrapCommand]);

  const pay = useCallback(() => {
    if (!eventId || !participantId) return;
    void wrapCommand(() => confirmPayment(apiBaseUrl, eventId, participantId));
  }, [apiBaseUrl, eventId, participantId, wrapCommand]);

  const start = useCallback(async () => {
    if (!eventId) return;
    await startEvent(apiBaseUrl, eventId);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  const reset = useCallback(async () => {
    if (!eventId) return;
    await resetEvent(apiBaseUrl, eventId);
    window.localStorage.removeItem(participantStorageKey);
    setParticipantId(null);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  const startAi = useCallback(async (count: number, concurrency: number) => {
    if (!eventId) return;
    await startAiParticipants(apiBaseUrl, eventId, count, concurrency);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  return { eventId, participantId, snapshot, myParticipant, loading, error, message, join, reserve, selectSeat, pay, start, reset, startAi, refresh };
}
