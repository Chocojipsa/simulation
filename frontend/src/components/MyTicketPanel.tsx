import { CreditCard, LogIn, Ticket } from 'lucide-react';
import type { EventParticipantView, LiveEventStatus } from '../api/liveEventApi';
import { canConfirmPayment, canReserve, formatParticipantStatus, getSeatHoldRemainingLabel } from '../domain/liveEventSelectors';

interface MyTicketPanelProps {
  status: LiveEventStatus;
  participant: EventParticipantView | null;
  loading: boolean;
  onJoin: () => void;
  onReserve: () => void;
  onPay: () => void;
  now?: Date;
}

export function MyTicketPanel({ status, participant, loading, onJoin, onReserve, onPay, now = new Date() }: MyTicketPanelProps) {
  const joinDisabled = loading || status === 'READY' || status === 'ENDED';
  const reserveDisabled = !participant || status !== 'OPEN' || !canReserve(participant);
  const holdRemaining = getSeatHoldRemainingLabel(participant?.seatHoldExpiresAt, now);

  return (
    <section className="panel my-ticket-panel">
      <div className="panel-heading">
        <span className="eyebrow">MY TICKET</span>
        <h2>내 예매</h2>
      </div>
      <div className="status-line">
        <span>참가자</span>
        <strong>{participant?.displayName ?? '입장 전'}</strong>
      </div>
      <div className="status-line">
        <span>상태</span>
        <strong className="status-chip">{formatParticipantStatus(participant?.status)}</strong>
      </div>
      <div className="status-line">
        <span>선택 좌석</span>
        <strong>{participant?.selectedSeatLabel ?? '-'}</strong>
      </div>
      <div className="status-line">
        <span>선점 만료</span>
        <strong>{holdRemaining}</strong>
      </div>
      <TicketHint participant={participant} />
      {(!participant || canReserve(participant)) ? (
        <button className="primary-action icon-action" disabled={status !== 'OPEN'} onClick={onReserve}>
          <Ticket size={18} /> 예약하기
        </button>
      ) : (
        <button className="primary-action icon-action" disabled={true} onClick={onReserve}>
          <Ticket size={18} /> 예약하기
        </button>
      )}
      <button className="secondary-action icon-action" disabled={!canConfirmPayment(participant)} onClick={onPay}>
        <CreditCard size={18} /> 결제 확인
      </button>
    </section>
  );
}

function TicketHint({ participant }: { participant: EventParticipantView | null }) {
  if (participant?.status === 'QUEUED') {
    return <p className="ticket-hint">대기열 통과 후 좌석 선택</p>;
  }
  if (participant?.status === 'SELECTING_SEAT') {
    return <p className="ticket-hint ticket-hint-ready">좌석 선택 가능</p>;
  }
  if (participant?.status === 'SEAT_HELD' || participant?.status === 'PAYMENT_IN_PROGRESS') {
    return <p className="ticket-hint ticket-hint-ready">결제 확인 필요</p>;
  }
  return null;
}
