import type { SeatView } from '../api/simulationApi';

interface SeatMapProps {
  seats: SeatView[];
  selectedSeatLabel: string | null;
  onSelectSeat?: (seatId: number) => void;
}

function seatClassName(status: SeatView['status'], mine: boolean) {
  const statusClass = status.toLowerCase().replace(/_/g, '-');
  return `seat seat-${statusClass}${mine ? ' seat-mine' : ''}`;
}

export function SeatMap({ seats, selectedSeatLabel, onSelectSeat }: SeatMapProps) {
  return (
    <section className="panel seat-map-panel">
      <div className="panel-heading">
        <h2>좌석 현황</h2>
        <span>STAGE</span>
      </div>
      <div className="seat-legend">
        <span><i className="legend-available" /> 선택 가능</span>
        <span><i className="legend-held" /> 선점</span>
        <span><i className="legend-payment" /> 결제 중</span>
        <span><i className="legend-reserved" /> 예약 완료</span>
      </div>
      <div className="seat-grid" aria-label="좌석표">
        {seats.map((seat) => {
          const mine = seat.label === selectedSeatLabel;
          const disabled = seat.status !== 'AVAILABLE';
          return (
            <button
              key={seat.id}
              type="button"
              className={seatClassName(seat.status, mine)}
              disabled={disabled}
              title={`${seat.label} ${seat.status}`}
              onClick={() => onSelectSeat?.(seat.id)}
            >
              {seat.label}
            </button>
          );
        })}
      </div>
    </section>
  );
}
