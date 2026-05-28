import { CreditCard, LogIn, Ticket } from 'lucide-react';
import type { EventParticipantView } from '../api/liveEventApi';
import { canConfirmPayment, canReserve } from '../domain/liveEventSelectors';

interface MyTicketPanelProps {
  participant: EventParticipantView | null;
  loading: boolean;
  onJoin: () => void;
  onReserve: () => void;
  onPay: () => void;
}

export function MyTicketPanel({ participant, loading, onJoin, onReserve, onPay }: MyTicketPanelProps) {
  return (
    <section className="panel my-ticket-panel">
      <h2>내 예매 상태</h2>
      <div className="status-line">
        <span>참가자</span>
        <strong>{participant?.displayName ?? '입장 전'}</strong>
      </div>
      <div className="status-line">
        <span>상태</span>
        <strong>{participant?.status ?? 'WAITING_ROOM'}</strong>
      </div>
      <div className="status-line">
        <span>선택 좌석</span>
        <strong>{participant?.selectedSeatLabel ?? '-'}</strong>
      </div>
      {!participant ? (
        <button className="primary-action icon-action" disabled={loading} onClick={onJoin}>
          <LogIn size={18} /> 이벤트 입장
        </button>
      ) : (
        <button className="primary-action icon-action" disabled={!canReserve(participant)} onClick={onReserve}>
          <Ticket size={18} /> 예약하기
        </button>
      )}
      <button className="secondary-action icon-action" disabled={!canConfirmPayment(participant)} onClick={onPay}>
        <CreditCard size={18} /> 결제 확인
      </button>
    </section>
  );
}
