import type { SeatView } from '../api/simulationApi';
import { getSeatClassName } from '../domain/simulationSelectors';

interface SeatMapProps {
  seats: SeatView[];
  selectedSeatLabel: string | null;
}

export function SeatMap({ seats, selectedSeatLabel }: SeatMapProps) {
  return (
    <section className="panel seat-map-panel">
      <div className="panel-heading">
        <h2>실시간 좌석표</h2>
        <span>STAGE</span>
      </div>
      <div className="seat-grid" aria-label="좌석표">
        {seats.map((seat) => (
          <button
            key={seat.id}
            className={getSeatClassName(seat.status, seat.label === selectedSeatLabel)}
            title={`${seat.label} ${seat.status}`}
          >
            {seat.label}
          </button>
        ))}
      </div>
    </section>
  );
}
