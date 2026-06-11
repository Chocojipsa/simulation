import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { LogIn, RefreshCw, CreditCard, CheckCircle, AlertTriangle } from 'lucide-react';
import { SeatMap } from './SeatMap';
import {
  fetchEventSnapshot,
  joinEvent,
  queueParticipant,
  holdSeat,
  confirmPayment,
  releaseSeat,
  updateParticipantName,
  type LiveEventSnapshot,
  ApiError,
  normalizeSnapshot,
} from '../api/liveEventApi';
import { getQueuePosition } from '../domain/liveEventSelectors';

const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.startsWith('192.168.')) {
      return 'https://ticket-api.chocojipsa.blog';
    }
  }
  return '';
};
const apiBaseUrl = getApiBaseUrl();

export function TicketingWindow() {
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const { eventId } = useParams<{ eventId: string }>();
  const [step, setStep] = useState<number>(1);
  const [payeeName, setPayeeName] = useState('');
  const [participantId, setParticipantId] = useState<string | null>(null);
  const [snapshot, setRawSnapshot] = useState<LiveEventSnapshot | null>(null);
  const setSnapshot = useCallback((val: any) => {
    setRawSnapshot((prev) => {
      const nextVal = typeof val === 'function' ? val(prev) : val;
      return normalizeSnapshot(nextVal, prev);
    });
  }, []);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [now, setNow] = useState(new Date());
  const [bookingTime, setBookingTime] = useState<string>('');
  const [sseQueuePos, setSseQueuePos] = useState<number | null>(null);
  const [sseEstimatedSeconds, setSseEstimatedSeconds] = useState<number | null>(null);

  const resetSession = useCallback(() => {
    localStorage.removeItem('timedeal.participantId');
    setParticipantId(null);
    setStep(1);
  }, []);

  const initSession = useCallback(async () => {
    if (!eventId) return;

    const autoJoinAndQueue = async () => {
      try {
        setLoading(true);
        setError(null);
        const guestName = `게스트-${Math.floor(1000 + Math.random() * 9000)}`;
        const joinRes = await joinEvent(apiBaseUrl, eventId, guestName);
        if (!isMountedRef.current) return;
        localStorage.setItem('timedeal.participantId', joinRes.participantId);
        setParticipantId(joinRes.participantId);
        await queueParticipant(apiBaseUrl, eventId, joinRes.participantId);
        if (!isMountedRef.current) return;
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, joinRes.participantId);
        if (!isMountedRef.current) return;
        setSnapshot(snap);
        setSseQueuePos(null);
        setSseEstimatedSeconds(null);
        setStep(2);
      } catch (err) {
        if (!isMountedRef.current) return;
        setError('대기열 진입에 실패했습니다. 서버 상태를 확인하세요.');
      } finally {
        if (isMountedRef.current) {
          setLoading(false);
        }
      }
    };

    const storedId = localStorage.getItem('timedeal.participantId');
    if (!storedId) {
      await autoJoinAndQueue();
      if (!isMountedRef.current) return;
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const snap = await fetchEventSnapshot(apiBaseUrl, eventId, storedId);
      if (!isMountedRef.current) return;
      setSnapshot(snap);
      const p = snap.participants.find((u) => u.id === storedId);
      if (p) {
        if (p.status === 'CREATED' || p.status === 'WAITING_ROOM') {
          setParticipantId(storedId);
          await queueParticipant(apiBaseUrl, eventId, storedId);
          if (!isMountedRef.current) return;
          const nextSnap = await fetchEventSnapshot(apiBaseUrl, eventId, storedId);
          if (!isMountedRef.current) return;
          setSnapshot(nextSnap);
          setSseQueuePos(null);
          setSseEstimatedSeconds(null);
          setStep(2);
        } else if (p.status === 'QUEUED') {
          setParticipantId(storedId);
          setSseQueuePos(null);
          setSseEstimatedSeconds(null);
          setStep(2);
        } else if (p.status === 'SELECTING_SEAT' || p.status === 'ADMITTED') {
          setParticipantId(storedId);
          setStep(3);
        } else if (p.status === 'SEAT_HELD' || p.status === 'PAYMENT_IN_PROGRESS') {
          setParticipantId(storedId);
          setStep(4);
        } else if (p.status === 'RESERVED') {
          setParticipantId(storedId);
          setBookingTime(new Date().toLocaleString());
          setStep(5);
        } else {
          await autoJoinAndQueue();
          if (!isMountedRef.current) return;
        }
      } else {
        await autoJoinAndQueue();
        if (!isMountedRef.current) return;
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      if (err instanceof ApiError && (err.status === 400 || err.status === 404)) {
        await autoJoinAndQueue();
        if (!isMountedRef.current) return;
      } else {
        setError('서버와 통신할 수 없습니다. 다시 시도해 주세요.');
      }
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, [eventId]);

  // 1. Session Recovery on mount or reset
  useEffect(() => {
    let active = true;
    const run = async () => {
      if (active && step === 1) {
        await initSession();
      }
    };
    void run();
    return () => {
      active = false;
    };
  }, [eventId, step, initSession]);

  // 2. Queue Progress SSE Connection (Step 2) with status reconciliation poll
  useEffect(() => {
    if (step !== 2 || !eventId || !participantId) return;
    let active = true;
    let eventSource: EventSource | null = null;
    let pollTimer: any = null;
    let checkStatusInProgress = false;

    const checkStatus = async () => {
      if (checkStatusInProgress) return;
      checkStatusInProgress = true;
      try {
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        if (!active) return;
        setSnapshot(snap);
        const p = snap.participants.find((u) => u.id === participantId);
        if (p) {
          if (snap.status === 'OPEN' && (p.status === 'WAITING_ROOM' || p.status === 'CREATED')) {
            console.log('Event is OPEN. Attempting to enter queue...');
            await queueParticipant(apiBaseUrl, eventId, participantId);
            if (!active) return;
            const nextSnap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
            if (!active) return;
            setSnapshot(nextSnap);

            const nextP = nextSnap.participants.find((u) => u.id === participantId);
            if (nextP && nextP.status !== 'QUEUED') {
              if (nextP.status === 'SELECTING_SEAT' || nextP.status === 'ADMITTED') {
                setStep(3);
              } else if (nextP.status === 'SEAT_HELD' || nextP.status === 'PAYMENT_IN_PROGRESS') {
                setStep(4);
              } else if (nextP.status === 'RESERVED') {
                setBookingTime(new Date().toLocaleString());
                setStep(5);
              }
            }
            return;
          }

          if (p.status !== 'QUEUED') {
            if (p.status === 'SELECTING_SEAT' || p.status === 'ADMITTED') {
              setStep(3);
            } else if (p.status === 'SEAT_HELD' || p.status === 'PAYMENT_IN_PROGRESS') {
              setStep(4);
            } else if (p.status === 'RESERVED') {
              setBookingTime(new Date().toLocaleString());
              setStep(5);
            } else if (p.status === 'WAITING_ROOM' || p.status === 'CREATED') {
              // Stay in Step 2, just wait
            } else {
              resetSession();
            }
          }
        } else {
          resetSession();
        }
      } catch (err) {
        if (!active) return;
        console.error('Queue status check failed:', err);
        if (err instanceof ApiError && (err.status === 400 || err.status === 404)) {
          resetSession();
        }
      } finally {
        checkStatusInProgress = false;
      }
    };

    // Run status check every 3 seconds to guarantee progress even if SSE drops
    pollTimer = setInterval(checkStatus, 3000);

    const sseUrl = `${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/stream`;
    console.log(`Connecting to queue SSE stream: ${sseUrl}`);
    try {
      eventSource = new EventSource(sseUrl);

      eventSource.addEventListener('activity', (event) => {
        if (!active) return;
        try {
          const data = JSON.parse(event.data);
          if (data.label === 'queue_position_update') {
            const payload = JSON.parse(data.message);
            setSseQueuePos(payload.position);
            setSseEstimatedSeconds(payload.estimatedWaitSeconds);
          } else if (data.label === 'queue_admitted') {
            fetchEventSnapshot(apiBaseUrl, eventId, participantId).then((snap) => {
              if (active) {
                setSnapshot(snap);
                setStep(3);
              }
            }).catch((err) => {
              console.error('Failed to fetch snapshot on queue admission:', err);
              if (active) setStep(3);
            });
            if (eventSource) {
              eventSource.close();
            }
          }
        } catch (e) {
          console.error('Failed to parse SSE activity event data:', e);
        }
      });

      eventSource.onerror = (err) => {
        if (!active) return;
        console.error('SSE connection error:', err);
        setSseQueuePos(null);
        setSseEstimatedSeconds(null);
      };
    } catch (err) {
      console.error('Failed to create EventSource:', err);
    }

    return () => {
      active = false;
      if (eventSource) {
        eventSource.close();
      }
      if (pollTimer) {
        clearInterval(pollTimer);
      }
    };
  }, [step, eventId, participantId, resetSession]);

  // 3. Auto-Refresh Timer (Step 3)
  useEffect(() => {
    if (step !== 3 || !autoRefresh || !eventId || !participantId) return;
    let active = true;
    const timer = setInterval(async () => {
      try {
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        if (!active) return;
        setSnapshot(snap);
      } catch (err) {
        if (!active) return;
        console.error('Auto refresh failed:', err);
        if (err instanceof ApiError && (err.status === 400 || err.status === 404)) {
          resetSession();
        }
      }
    }, 4000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [step, autoRefresh, eventId, participantId, resetSession]);

  // 4. Payment Page Countdown & Status Polling (Step 4)
  useEffect(() => {
    if (step !== 4) return;
    let active = true;
    const timeTimer = setInterval(() => {
      if (active) setNow(new Date());
    }, 1000);

    const pollTimer = setInterval(async () => {
      if (!eventId || !participantId) return;
      try {
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        if (!active) return;
        setSnapshot(snap);
        const p = snap.participants.find((u) => u.id === participantId);
        if (p) {
          if (p.status === 'RESERVED') {
            setBookingTime(new Date().toLocaleString());
            setStep(5);
          } else if (p.status === 'SELECTING_SEAT' || p.status === 'ADMITTED') {
            setStep(3);
          } else if (!['SEAT_HELD', 'PAYMENT_IN_PROGRESS'].includes(p.status)) {
            resetSession();
          }
        } else {
          resetSession();
        }
      } catch (err) {
        if (!active) return;
        console.error('Payment polling failed:', err);
        if (err instanceof ApiError && (err.status === 400 || err.status === 404)) {
          resetSession();
        }
      }
    }, 3000);

    return () => {
      active = false;
      clearInterval(timeTimer);
      clearInterval(pollTimer);
    };
  }, [step, eventId, participantId, resetSession]);

  // 5. Release Beacon on beforeunload
  useEffect(() => {
    const handleBeforeUnload = () => {
      if (eventId && participantId && (step === 3 || step === 4)) {
        navigator.sendBeacon(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/seats/release`);
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [eventId, participantId, step]);

  // Temporary message/error auto-clear
  useEffect(() => {
    if (error) {
      const t = setTimeout(() => setError(null), 5000);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [error]);

  useEffect(() => {
    if (message) {
      const t = setTimeout(() => setMessage(null), 5000);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [message]);

  // Event Handlers
  const handleManualRefresh = async () => {
    if (!eventId) return;
    try {
      setLoading(true);
      setError(null);
      const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
      setSnapshot(snap);
      setMessage('좌석 정보가 갱신되었습니다.');
    } catch (err) {
      setError('정보 갱신에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectSeat = async (seatId: number) => {
    if (!eventId || !participantId) return;
    try {
      setLoading(true);
      setError(null);
      const res = await holdSeat(apiBaseUrl, eventId, participantId, seatId);
      if (res.status === 'SEAT_HELD' || res.status === 'PAYMENT_IN_PROGRESS') {
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        setSnapshot(snap);
        setStep(4);
      } else {
        setError(res.message || '이미 선택된 좌석입니다.');
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        setSnapshot(snap);
      }
    } catch (err) {
      setError('좌석 선점에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelPayment = async () => {
    if (!eventId || !participantId) return;
    try {
      setLoading(true);
      setError(null);
      await releaseSeat(apiBaseUrl, eventId, participantId);
      const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
      setSnapshot(snap);
      setStep(3);
    } catch (err) {
      setError('결제 취소에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmPayment = async () => {
    if (!eventId || !participantId) return;
    if (!payeeName.trim()) {
      setError('예매자 이름을 입력해 주세요.');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      await updateParticipantName(apiBaseUrl, eventId, participantId, payeeName.trim());
      const res = await confirmPayment(apiBaseUrl, eventId, participantId);
      if (res.status === 'RESERVED') {
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        setSnapshot(snap);
        setBookingTime(new Date().toLocaleString());
        setStep(5);
      } else {
        setError(res.message || '결제 승인에 실패했습니다.');
        const snap = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
        setSnapshot(snap);
      }
    } catch (err) {
      setError('결제 승인 처리 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const activeParticipant = snapshot?.participants.find((p) => p.id === participantId) ?? null;
  const queuePos = sseQueuePos !== null
    ? sseQueuePos
    : (snapshot ? (snapshot.myQueuePosition ?? getQueuePosition(snapshot, participantId)) : null);
  const estimatedSeconds = sseEstimatedSeconds !== null
    ? sseEstimatedSeconds
    : (queuePos ? Math.ceil(queuePos * 0.5) : 0);
  const holdRemaining = activeParticipant?.seatHoldExpiresAt
    ? Math.max(0, Math.ceil((new Date(activeParticipant.seatHoldExpiresAt).getTime() - now.getTime()) / 1000))
    : 0;

  // Render receipt completion timestamp fallback
  const displayBookingTime = bookingTime || new Date().toLocaleString();

  return (
    <main className="ticketing-window">
      {/* Scope-scoped Premium CSS Styling */}
      <style>{`
        .ticketing-window {
          min-height: 100vh;
          padding: 40px 16px;
          background-color: #F8FAFC;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .ticketing-card {
          width: 100%;
          max-width: 720px;
          border: 1px solid var(--border-line);
          background: #FFFFFF;
          box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05);
          border-radius: var(--radius-lg);
          padding: 40px;
          position: relative;
        }
        .ticketing-header {
          text-align: center;
          margin-bottom: 32px;
          border-bottom: 1px solid var(--border-line);
          padding-bottom: 24px;
        }
        .ticketing-header h1 {
          font-size: 24px;
          margin: 8px 0 0;
          font-weight: 700;
          color: var(--text-primary);
        }
        .step-indicator {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 40px;
          padding: 0 8px;
          position: relative;
        }
        .step-node {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 8px;
          opacity: 0.35;
          transition: all 0.3s ease;
          z-index: 2;
        }
        .step-node.active {
          opacity: 1;
        }
        .step-num {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          border: 2px solid var(--border-line);
          background: #FFFFFF;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 700;
          font-size: 14px;
          color: var(--text-secondary);
          transition: all 0.3s ease;
        }
        .step-node.active .step-num {
          background: var(--primary-indigo);
          color: #FFFFFF;
          border-color: var(--primary-indigo);
        }
        .step-label {
          font-size: 12px;
          font-weight: 600;
          white-space: nowrap;
          color: var(--text-secondary);
        }
        .step-line {
          flex-grow: 1;
          height: 2px;
          background: var(--border-line);
          margin: 0 8px;
          margin-top: -22px;
          z-index: 1;
          transition: all 0.3s ease;
        }
        .step-line.active {
          background: var(--primary-indigo);
        }
        .warning-box {
          background: #FFFBEB;
          border: 1px solid #FCD34D;
          padding: 16px;
          border-radius: var(--radius-md);
          margin-bottom: 24px;
          font-size: 13px;
          color: #92400E;
          line-height: 1.6;
          display: flex;
          gap: 12px;
          align-items: flex-start;
        }
        .warning-box p {
          margin: 0;
        }
        .warning-box strong {
          color: var(--danger-red);
          font-weight: 700;
        }
        .form-group {
          display: flex;
          flex-direction: column;
          gap: 8px;
          margin-bottom: 24px;
        }
        .form-group label {
          font-weight: 600;
          font-size: 14px;
          color: var(--text-primary);
        }
        .form-input {
          padding: 10px 14px;
          border: 1px solid var(--border-line);
          border-radius: var(--radius-md);
          font-size: 14px;
          background: #FFFFFF;
          outline: none;
          transition: border-color 0.2s ease;
        }
        .form-input:focus {
          border-color: var(--primary-indigo);
        }
        .progress-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 48px 0;
          text-align: center;
        }
        .queue-badge {
          font-size: 40px;
          font-weight: 700;
          background: rgba(245, 158, 11, 0.08);
          color: var(--warning-amber);
          border: 1px solid rgba(245, 158, 11, 0.2);
          padding: 12px 28px;
          border-radius: 9999px;
          margin-bottom: 24px;
        }
        .wait-time {
          font-size: 15px;
          font-weight: 500;
          color: var(--text-secondary);
        }
        .spinner {
          width: 40px;
          height: 40px;
          border: 3px solid var(--border-line);
          border-top: 3px solid var(--primary-indigo);
          border-radius: 50%;
          animation: spin 1s linear infinite;
          margin-top: 24px;
        }
        .map-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 20px;
          gap: 12px;
        }
        .toggle-switch {
          display: flex;
          align-items: center;
          gap: 10px;
          font-weight: 600;
          font-size: 13px;
          cursor: pointer;
          color: var(--text-secondary);
        }
        .toggle-input {
          cursor: pointer;
          width: 36px;
          height: 20px;
          appearance: none;
          background: #E2E8F0;
          border-radius: 9999px;
          position: relative;
          outline: none;
          border: 1px solid transparent;
          transition: background 0.3s;
        }
        .toggle-input:checked {
          background: var(--success-mint);
        }
        .toggle-input::before {
          content: '';
          position: absolute;
          width: 14px;
          height: 14px;
          border-radius: 50%;
          background: white;
          top: 2px;
          left: 2px;
          transition: transform 0.3s;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
        }
        .toggle-input:checked::before {
          transform: translateX(16px);
        }
        .timer-display {
          font-size: 20px;
          font-weight: 700;
          color: var(--danger-red);
          text-align: center;
          margin-bottom: 24px;
          padding: 12px;
          border: 1px solid #FEE2E2;
          border-radius: var(--radius-md);
          background: #FEF2F2;
        }
        .timer-display.normal {
          color: var(--text-primary);
          background: var(--bg-main);
          border-color: var(--border-line);
        }
        .payment-summary {
          margin-bottom: 28px;
          background: var(--bg-main);
          border: 1px solid var(--border-line);
          border-radius: var(--radius-md);
          padding: 20px;
        }
        .payment-row {
          display: flex;
          justify-content: space-between;
          padding: 12px 0;
          border-bottom: 1px dashed var(--border-line);
        }
        .payment-row:last-child {
          border-bottom: none;
        }
        .payment-row span {
          font-weight: 500;
          color: var(--text-secondary);
        }
        .payment-row strong {
          font-weight: 600;
          color: var(--text-primary);
        }
        .button-group {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 16px;
          margin-top: 24px;
        }
        .ticket-receipt {
          background: #FFFFFF;
          border: 1px solid var(--border-line);
          border-radius: var(--radius-lg);
          position: relative;
          margin: 24px auto 0;
          box-shadow: var(--card-shadow);
          overflow: hidden;
          max-width: 420px;
        }
        .ticket-receipt-top {
          padding: 24px;
          background: rgba(79, 70, 229, 0.05);
          border-bottom: 1px dashed var(--border-line);
          text-align: center;
        }
        .ticket-receipt-top h3 {
          margin: 0;
          font-size: 11px;
          color: var(--primary-indigo);
          letter-spacing: 2px;
          font-weight: 800;
        }
        .ticket-receipt-top h2 {
          margin: 8px 0 0;
          font-size: 20px;
          font-weight: 700;
          color: var(--text-primary);
        }
        .ticket-receipt-body {
          padding: 24px;
          display: grid;
          gap: 14px;
        }
        .ticket-receipt-row {
          display: flex;
          justify-content: space-between;
          font-size: 13px;
        }
        .ticket-receipt-row span {
          color: var(--text-secondary);
        }
        .ticket-receipt-row strong {
          font-weight: 600;
          color: var(--text-primary);
        }
        .ticket-receipt-footer {
          padding: 16px 24px;
          background: var(--bg-main);
          border-top: 1px dashed var(--border-line);
          text-align: center;
          font-size: 11px;
          color: var(--text-tertiary);
          font-weight: 600;
          letter-spacing: 1px;
        }
        .ticket-cutout-left,
        .ticket-cutout-right {
          position: absolute;
          width: 14px;
          height: 14px;
          background: #FFFFFF;
          border: 1px solid var(--border-line);
          border-radius: 50%;
          top: 67px;
          z-index: 10;
        }
        .ticket-cutout-left {
          left: -8px;
        }
        .ticket-cutout-right {
          right: -8px;
        }
        .success-box {
          text-align: center;
          padding: 24px 0;
        }
        .success-title {
          font-size: 22px;
          font-weight: 700;
          margin-bottom: 12px;
          color: var(--success-mint);
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
        }
        .error-banner, .info-banner {
          padding: 12px 16px;
          border-radius: var(--radius-md);
          margin-bottom: 20px;
          font-size: 14px;
          font-weight: 500;
        }
        .error-banner {
          background: #FEE2E2;
          color: var(--danger-red);
        }
        .info-banner {
          background: #EFF6FF;
          color: #1E40AF;
        }
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>

      <div className="ticketing-card">
        {/* Header Block */}
        <header className="ticketing-header">
          <span className="eyebrow">TICKET PORTAL</span>
          <h1>{snapshot?.title || '콘서트 예매 대기'}</h1>
        </header>

        {/* Step Indicator */}
        <div className="step-indicator">
          <div className={`step-node ${step >= 1 ? 'active' : ''}`}>
            <span className="step-num">1</span>
            <span className="step-label">대기열 진입</span>
          </div>
          <div className={`step-line ${step > 1 ? 'active' : ''}`} />
          <div className={`step-node ${step >= 2 ? 'active' : ''}`}>
            <span className="step-num">2</span>
            <span className="step-label">입장 대기</span>
          </div>
          <div className={`step-line ${step > 2 ? 'active' : ''}`} />
          <div className={`step-node ${step >= 3 ? 'active' : ''}`}>
            <span className="step-num">3</span>
            <span className="step-label">좌석 선택</span>
          </div>
          <div className={`step-line ${step > 3 ? 'active' : ''}`} />
          <div className={`step-node ${step >= 4 ? 'active' : ''}`}>
            <span className="step-num">4</span>
            <span className="step-label">결제</span>
          </div>
          <div className={`step-line ${step > 4 ? 'active' : ''}`} />
          <div className={`step-node ${step >= 5 ? 'active' : ''}`}>
            <span className="step-num">5</span>
            <span className="step-label">예매 완료</span>
          </div>
        </div>

        {/* Error and Message Banners */}
        {error && <div className="error-banner">{error}</div>}
        {message && <div className="info-banner">{message}</div>}

        {/* Step 1: Preparing Entry */}
        {step === 1 && (
          <div className="progress-container">
            {error ? (
              <button
                type="button"
                className="btn btn-primary"
                style={{ width: '100%' }}
                onClick={() => {
                  setError(null);
                  void initSession();
                }}
              >
                다시 시도하기
              </button>
            ) : (
              <>
                <p className="wait-time">대기열 진입을 준비 중입니다...</p>
                <div className="spinner" />
              </>
            )}
          </div>
        )}

        {/* Step 2: Queue Progress */}
        {step === 2 && (
          <div className="progress-container">
            <div className="queue-badge">
              {queuePos !== null ? `${queuePos}번` : '대기 중'}
            </div>
            <p className="wait-time">
              {queuePos !== null
                ? `예상 대기 시간: 약 ${estimatedSeconds}초`
                : '대기열에 순차적으로 진입하고 있습니다...'}
            </p>
            <div className="spinner" />
          </div>
        )}

        {/* Step 3: Seat Selection */}
        {step === 3 && snapshot && (
          <div>
            <div className="warning-box">
              <AlertTriangle size={24} style={{ flexShrink: 0 }} />
              <div>
                <p>좌석 현황은 조회 시점 기준입니다. 좌석 선택 시 서버에서 최종 예약 가능 여부를 다시 확인합니다.</p>
                <p style={{ marginTop: '4px' }}>
                  좌석 선택 후 <strong>3분 동안</strong> 결제가 가능합니다.
                </p>
              </div>
            </div>

            <div className="map-toolbar">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  className="toggle-input"
                  checked={autoRefresh}
                  onChange={(e) => setAutoRefresh(e.target.checked)}
                />
                자동 새로고침 (4초)
              </label>
              <button
                type="button"
                className="btn btn-secondary compact"
                onClick={handleManualRefresh}
                disabled={loading}
              >
                <RefreshCw size={14} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
                새로고침
              </button>
            </div>

            <SeatMap
              status={snapshot.status}
              seats={snapshot.seats}
              participant={activeParticipant}
              selectedSeatLabel={activeParticipant?.selectedSeatLabel ?? null}
              onSelectSeat={handleSelectSeat}
              readOnly={false}
            />
          </div>
        )}

        {/* Step 4: Payment */}
        {step === 4 && (
          <div>
            <div className={`timer-display ${holdRemaining <= 30 ? 'critical' : 'normal'}`}>
              남은 결제 시간: {Math.floor(holdRemaining / 60)}분 {holdRemaining % 60}초
            </div>

            <div className="payment-summary">
              <div className="payment-row">
                <span>공연명</span>
                <strong>{snapshot?.title || '콘서트'}</strong>
              </div>
              <div className="payment-row">
                <span>선택한 좌석</span>
                <strong>{activeParticipant?.selectedSeatLabel}</strong>
              </div>
              <div className="payment-row" style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', gap: '8px', padding: '12px 0' }}>
                <span>예매자 이름</span>
                <input
                  type="text"
                  placeholder="예매자 이름을 입력하세요"
                  value={payeeName}
                  onChange={(e) => setPayeeName(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    border: '1px solid var(--border-line)',
                    borderRadius: 'var(--radius-md)',
                    backgroundColor: '#fff',
                    fontFamily: 'inherit',
                    fontSize: '14px',
                    fontWeight: '500',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>
            </div>

            <div className="button-group">
              <button
                type="button"
                className="btn btn-secondary icon-action"
                onClick={handleCancelPayment}
                disabled={loading}
              >
                이전 단계로
              </button>
              <button
                type="button"
                className="btn btn-primary icon-action"
                onClick={handleConfirmPayment}
                disabled={loading || holdRemaining <= 0}
              >
                <CreditCard size={18} style={{ marginRight: '6px' }} /> 결제하기
              </button>
            </div>
          </div>
        )}

        {/* Step 5: Success Receipt */}
        {step === 5 && (
          <div className="success-box">
            <h2 className="success-title">
              <CheckCircle size={24} /> 예매가 완료되었습니다!
            </h2>
            <p style={{ margin: '0 0 16px', color: 'var(--text-secondary)', fontWeight: 500 }}>
              예매가 성공적으로 처리되었습니다. 아래 영수증을 확인해 주세요.
            </p>

            <div className="ticket-receipt">
              <div className="ticket-cutout-left" />
              <div className="ticket-cutout-right" />
              <div className="ticket-receipt-top">
                <h3>CONCERT TICKET</h3>
                <h2>{snapshot?.title || '콘서트'}</h2>
              </div>
              <div className="ticket-receipt-body">
                <div className="ticket-receipt-row">
                  <span>RESERVATION ID</span>
                  <strong>#{activeParticipant?.reservationId}</strong>
                </div>
                <div className="ticket-receipt-row">
                  <span>SEAT NUMBER</span>
                  <strong>{activeParticipant?.selectedSeatLabel}</strong>
                </div>
                <div className="ticket-receipt-row">
                  <span>BOOKER NAME</span>
                  <strong>{activeParticipant?.displayName}</strong>
                </div>
                <div className="ticket-receipt-row">
                  <span>BOOKING TIME</span>
                  <strong>{displayBookingTime}</strong>
                </div>
              </div>
              <div className="ticket-receipt-footer">
                THANK YOU FOR BOOKING WITH TIMEDEAL
              </div>
            </div>

            <button
              type="button"
              className="btn btn-secondary icon-action"
              style={{ marginTop: '24px', width: '100%' }}
              onClick={() => window.close()}
            >
              닫기
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
