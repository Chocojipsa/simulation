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
  const [snapshot, setSnapshot] = useState<LiveEventSnapshot | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
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
        setError('대기열 진입에 실패했습니다. 서버 상태를 확인하세요.');
      } finally {
        setLoading(false);
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
      if (err instanceof ApiError && (err.status === 400 || err.status === 404)) {
        await autoJoinAndQueue();
        if (!isMountedRef.current) return;
      } else {
        setError('서버와 통신할 수 없습니다. 다시 시도해 주세요.');
      }
    } finally {
      setLoading(false);
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
            setStep(3);
            if (eventSource) {
              eventSource.close();
            }
          }
        } catch (e) {
          console.error('Failed to parse SSE activity event data:', e);
        }
      });

      eventSource.onerror = (err) => {
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
          padding: 32px 16px;
          background: linear-gradient(rgba(23, 23, 23, 0.02) 1px, transparent 1px),
                      linear-gradient(90deg, rgba(23, 23, 23, 0.02) 1px, transparent 1px),
                      #f7f5ef;
          background-size: 24px 24px;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .ticketing-card {
          width: 100%;
          max-width: 680px;
          border: 2px solid var(--line);
          background: var(--paper);
          box-shadow: 6px 6px 0 var(--line);
          padding: 32px;
          position: relative;
        }
        .ticketing-header {
          text-align: center;
          margin-bottom: 24px;
          border-bottom: 2px dashed rgba(23, 23, 23, 0.2);
          padding-bottom: 20px;
        }
        .ticketing-header h1 {
          font-size: 28px;
          margin: 8px 0;
          font-weight: 900;
          color: var(--ink);
        }
        .step-indicator {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 32px;
          padding: 0 8px;
        }
        .step-node {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 6px;
          opacity: 0.35;
          transition: opacity 0.3s ease;
        }
        .step-node.active {
          opacity: 1;
        }
        .step-num {
          width: 28px;
          height: 28px;
          border-radius: 50%;
          border: 2px solid var(--line);
          background: var(--paper);
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 900;
          font-size: 14px;
          font-family: "Courier New", monospace;
        }
        .step-node.active .step-num {
          background: var(--green);
        }
        .step-label {
          font-size: 11px;
          font-weight: 800;
          white-space: nowrap;
          color: var(--ink);
        }
        .step-line {
          flex-grow: 1;
          height: 2px;
          background: rgba(23, 23, 23, 0.2);
          margin: 0 8px;
          margin-top: -18px;
        }
        .step-line.active {
          background: var(--line);
        }
        .warning-box {
          background: #fffdf5;
          border: 2px solid var(--yellow);
          padding: 14px;
          margin-bottom: 20px;
          font-size: 12px;
          font-weight: 800;
          color: #7d6013;
          line-height: 1.6;
          display: flex;
          gap: 8px;
          align-items: flex-start;
        }
        .warning-box p {
          margin: 0;
        }
        .warning-box strong {
          color: var(--red);
        }
        .form-group {
          display: flex;
          flex-direction: column;
          gap: 8px;
          margin-bottom: 24px;
        }
        .form-group label {
          font-weight: 800;
          font-size: 14px;
          color: var(--ink);
        }
        .form-input {
          padding: 12px;
          border: 2px solid var(--line);
          font-size: 16px;
          background: var(--paper);
          box-shadow: inset 2px 2px 0 rgba(23, 23, 23, 0.05);
        }
        .form-input:focus {
          outline: none;
          border-color: var(--mint);
          background: #fbfdf9;
        }
        .progress-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 32px 0;
          text-align: center;
        }
        .queue-badge {
          font-size: 44px;
          font-weight: 900;
          font-family: "Courier New", monospace;
          background: var(--yellow);
          border: 2px solid var(--line);
          padding: 12px 24px;
          box-shadow: 4px 4px 0 var(--line);
          margin-bottom: 20px;
        }
        .wait-time {
          font-size: 16px;
          font-weight: 800;
          color: var(--muted);
        }
        .spinner {
          width: 36px;
          height: 36px;
          border: 4px solid var(--paper-2);
          border-top: 4px solid var(--mint);
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
          margin-top: 20px;
        }
        .map-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 16px;
          gap: 12px;
        }
        .toggle-switch {
          display: flex;
          align-items: center;
          gap: 8px;
          font-weight: 800;
          font-size: 13px;
          cursor: pointer;
        }
        .toggle-input {
          cursor: pointer;
          width: 36px;
          height: 20px;
          appearance: none;
          background: var(--gray);
          border-radius: 10px;
          position: relative;
          outline: none;
          border: 2px solid var(--line);
          transition: background 0.3s;
        }
        .toggle-input:checked {
          background: var(--mint);
        }
        .toggle-input::before {
          content: '';
          position: absolute;
          width: 12px;
          height: 12px;
          border-radius: 50%;
          background: white;
          border: 1px solid var(--line);
          top: 2px;
          left: 2px;
          transition: transform 0.3s;
        }
        .toggle-input:checked::before {
          transform: translateX(16px);
        }
        .timer-display {
          font-size: 28px;
          font-weight: 900;
          color: var(--red);
          font-family: "Courier New", monospace;
          text-align: center;
          margin: 16px 0;
          padding: 8px;
          border: 2px solid var(--line);
          background: #fff5f5;
          box-shadow: 3px 3px 0 var(--line);
        }
        .timer-display.normal {
          color: var(--ink);
          background: #fcfcfc;
        }
        .payment-summary {
          margin-bottom: 24px;
          background: var(--paper-2);
          border: 2px solid var(--line);
          padding: 16px;
        }
        .payment-row {
          display: flex;
          justify-content: space-between;
          padding: 10px 0;
          border-bottom: 1px dashed rgba(23, 23, 23, 0.2);
        }
        .payment-row:last-child {
          border-bottom: none;
        }
        .payment-row span {
          font-weight: 800;
          color: var(--muted);
          font-family: "Courier New", monospace;
        }
        .payment-row strong {
          font-weight: 900;
          color: var(--ink);
        }
        .button-group {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 12px;
          margin-top: 16px;
        }
        .ticket-receipt {
          background: var(--paper);
          border: 2px solid var(--line);
          position: relative;
          margin-top: 16px;
          box-shadow: 5px 5px 0 var(--line);
        }
        .ticket-receipt-top {
          padding: 24px;
          background: var(--green);
          border-bottom: 2px dashed var(--line);
          text-align: center;
        }
        .ticket-receipt-top h3 {
          margin: 0;
          font-size: 13px;
          color: var(--ink);
          letter-spacing: 2px;
          font-family: "Courier New", monospace;
          font-weight: 800;
        }
        .ticket-receipt-top h2 {
          margin: 10px 0 0;
          font-size: 24px;
          font-weight: 900;
        }
        .ticket-receipt-body {
          padding: 24px;
          display: grid;
          gap: 16px;
        }
        .ticket-receipt-row {
          display: flex;
          justify-content: space-between;
          font-size: 14px;
        }
        .ticket-receipt-row span {
          color: var(--muted);
          font-family: "Courier New", monospace;
          font-weight: 800;
        }
        .ticket-receipt-row strong {
          font-weight: 900;
        }
        .ticket-receipt-footer {
          padding: 16px 24px;
          background: #fcfcfc;
          border-top: 2px dashed var(--line);
          text-align: center;
          font-family: "Courier New", monospace;
          font-size: 12px;
          color: var(--muted);
          font-weight: 800;
        }
        .ticket-cutout-left,
        .ticket-cutout-right {
          position: absolute;
          width: 16px;
          height: 16px;
          background: #f7f5ef;
          border: 2px solid var(--line);
          border-radius: 50%;
          top: 67px;
        }
        .ticket-cutout-left {
          left: -10px;
        }
        .ticket-cutout-right {
          right: -10px;
        }
        .success-box {
          text-align: center;
          padding: 16px 0;
        }
        .success-title {
          font-size: 20px;
          font-weight: 900;
          margin-bottom: 12px;
          color: var(--mint);
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
        }
        .error-banner, .info-banner {
          padding: 10px;
          border: 2px solid var(--line);
          margin-bottom: 16px;
          font-size: 13px;
          font-weight: 800;
        }
        .error-banner {
          background: #ffebe9;
          color: var(--red);
        }
        .info-banner {
          background: #e6fffa;
          color: var(--mint);
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
                className="primary-action"
                style={{ minHeight: '44px', width: '100%' }}
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
                className="secondary-action compact"
                style={{ minHeight: '32px' }}
                onClick={handleManualRefresh}
                disabled={loading}
              >
                <RefreshCw size={14} style={{ marginRight: '4px', verticalAlign: 'middle' }} />
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
                    padding: '10px 12px',
                    border: '2px solid var(--line)',
                    backgroundColor: '#fff',
                    fontFamily: 'inherit',
                    fontSize: '14px',
                    fontWeight: '800',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>
            </div>

            <div className="button-group">
              <button
                type="button"
                className="secondary-action icon-action"
                onClick={handleCancelPayment}
                disabled={loading}
              >
                이전 단계로
              </button>
              <button
                type="button"
                className="primary-action icon-action"
                onClick={handleConfirmPayment}
                disabled={loading || holdRemaining <= 0}
              >
                <CreditCard size={18} /> 결제하기
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
            <p style={{ margin: '0 0 16px', color: 'var(--muted)', fontWeight: 800 }}>
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
              className="secondary-action icon-action"
              style={{ marginTop: '24px', width: '100%', minHeight: '44px' }}
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
