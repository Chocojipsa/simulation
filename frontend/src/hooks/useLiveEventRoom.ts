import { useCallback, useEffect, useMemo, useState } from 'react';
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

  const refresh = useCallback(async () => {
    if (!eventId) return;
    const next = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
    setSnapshot(next);
  }, [apiBaseUrl, eventId, participantId]);

  const applyCommandMessage = useCallback((response: CommandResponse) => {
    setMessage(response.message);
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function boot() {
      try {
        const active = await fetchActiveEvent(apiBaseUrl);
        if (cancelled) return;
        setEventId(active.eventId);
        const next = await fetchEventSnapshot(apiBaseUrl, active.eventId, participantId);
        if (!cancelled) {
          setSnapshot(next);
          setError(null);
        }
      } catch {
        if (!cancelled) setError('이벤트 정보를 불러오지 못했습니다.');
      }
    }
    void boot();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, participantId]);

  useEffect(() => {
    if (!eventId) return undefined;
    let busy = false;
    const timer = window.setInterval(() => {
      if (busy) return;
      busy = true;
      void refresh()
        .catch(() => setError('이벤트 상태 갱신에 실패했습니다.'))
        .finally(() => {
          busy = false;
        });
    }, 500);
    return () => window.clearInterval(timer);
  }, [eventId, refresh]);

  const myParticipant = useMemo(() => getMyParticipant(snapshot, participantId), [snapshot, participantId]);

  useEffect(() => {
    if (!eventId || !participantId || snapshot?.status !== 'OPEN' || myParticipant?.status !== 'QUEUED') {
      return undefined;
    }
    let busy = false;
    const timer = window.setInterval(() => {
      if (busy) return;
      busy = true;
      void queueParticipant(apiBaseUrl, eventId, participantId)
        .then((response) => {
          if (response.status === 'ADMITTED') {
            applyCommandMessage(response);
          }
          return refresh();
        })
        .catch(() => setError('대기열 상태 확인에 실패했습니다.'))
        .finally(() => {
          busy = false;
        });
    }, 500);
    return () => window.clearInterval(timer);
  }, [apiBaseUrl, applyCommandMessage, eventId, myParticipant?.status, participantId, refresh, snapshot?.status]);

  const join = useCallback(async (displayName: string) => {
    if (!eventId) return;
    setLoading(true);
    try {
      const joined = await joinEvent(apiBaseUrl, eventId, displayName);
      window.localStorage.setItem(participantStorageKey, joined.participantId);
      setParticipantId(joined.participantId);
      setSnapshot(await fetchEventSnapshot(apiBaseUrl, eventId, joined.participantId));
      setError(null);
    } catch {
      setError('이벤트 입장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl, eventId]);

  const reserve = useCallback(async () => {
    if (!eventId || !participantId) return;
    applyCommandMessage(await queueParticipant(apiBaseUrl, eventId, participantId));
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const selectSeat = useCallback(async (seatId: number) => {
    if (!eventId || !participantId) return;
    applyCommandMessage(await holdSeat(apiBaseUrl, eventId, participantId, seatId));
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const pay = useCallback(async () => {
    if (!eventId || !participantId) return;
    applyCommandMessage(await confirmPayment(apiBaseUrl, eventId, participantId));
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const start = useCallback(async () => {
    if (!eventId) return;
    await startEvent(apiBaseUrl, eventId);
    setMessage('이벤트 카운트다운이 시작되었습니다.');
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  const reset = useCallback(async () => {
    if (!eventId) return;
    const resetResponse = await resetEvent(apiBaseUrl, eventId);
    window.localStorage.removeItem(participantStorageKey);
    setParticipantId(null);
    setMessage('새 이벤트가 준비되었습니다.');
    setSnapshot(await fetchEventSnapshot(apiBaseUrl, resetResponse.eventId, null));
  }, [apiBaseUrl, eventId]);

  const startAi = useCallback(async (count: number, concurrency: number) => {
    if (!eventId) return;
    await startAiParticipants(apiBaseUrl, eventId, count, concurrency);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  return { eventId, participantId, snapshot, myParticipant, loading, error, message, join, reserve, selectSeat, pay, start, reset, startAi, refresh };
}
