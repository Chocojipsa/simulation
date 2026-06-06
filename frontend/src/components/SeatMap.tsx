import type { EventParticipantView, LiveEventStatus } from '../api/liveEventApi';
import type { SeatView } from '../api/simulationApi';
import { canSelectSeat } from '../domain/liveEventSelectors';

interface SeatMapProps {
  status: LiveEventStatus;
  seats: SeatView[];
  participant: EventParticipantView | null;
  selectedSeatLabel: string | null;
  onSelectSeat?: (seatId: number) => void;
  readOnly?: boolean;
}

function seatClassName(status: SeatView['status'], mine: boolean) {
  const statusClass = status.toLowerCase().replace(/_/g, '-');
  return `seat seat-${statusClass}${mine ? ' seat-mine' : ''}`;
}

export function SeatMap({ status, seats, participant, selectedSeatLabel, onSelectSeat, readOnly = false }: SeatMapProps) {
  const selection = canSelectSeat(status, participant);

  return (
    <section className="panel seat-map-panel">
      <div className="panel-heading">
        <span className="eyebrow">SEAT MAP</span>
        <h2>좌석 현황</h2>
      </div>
      <div className="stage-bar">STAGE</div>
      <div className="seat-legend">
        <span><i className="legend-available" /> 선택 가능</span>
        <span><i className="legend-held" /> 선점</span>
        <span><i className="legend-payment" /> 결제 중</span>
        <span><i className="legend-reserved" /> 예약 완료</span>
      </div>
      {selection.message ? <p className="seat-map-message">{selection.message}</p> : null}
      <div className="seat-grid" aria-label="좌석표" style={readOnly ? { pointerEvents: 'none' } : undefined}>
        {seats.map((seat) => {
          const mine = seat.label === selectedSeatLabel;
          const disabled = !readOnly && (seat.status !== 'AVAILABLE' || !selection.allowed);
          return (
            <button
              key={seat.id}
              type="button"
              className={seatClassName(seat.status, mine)}
              disabled={disabled}
              tabIndex={readOnly ? -1 : undefined}
              title={`${seat.label} ${seat.status}`}
              onClick={readOnly ? undefined : () => onSelectSeat?.(seat.id)}
            >
              {seat.label}
            </button>
          );
        })}
      </div>
    </section>
  );
}

