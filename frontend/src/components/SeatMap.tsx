import { Fragment } from 'react';
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

export function SeatMap({ status, seats, participant, selectedSeatLabel, onSelectSeat, readOnly = false }: SeatMapProps) {
  const selection = canSelectSeat(status, participant);

  // Extract unique rows and columns dynamically from seats labels (e.g. 'A-1', 'B-2')
  const rows = Array.from(new Set(seats.map(s => s.label.split('-')[0]))).sort();
  const cols = Array.from(new Set(seats.map(s => {
    const parts = s.label.split('-');
    return parts.length > 1 ? parseInt(parts[1], 10) : 0;
  }))).filter(c => c > 0).sort((a, b) => a - b);

  return (
    <section className="seat-map-container" style={{ padding: '24px', background: '#F8FAFC', borderRadius: 'var(--radius-lg)' }}>
      {/* Screen Area (seatdesign.PNG Reference) */}
      <div style={{ textAlign: 'center', margin: '0 auto 24px', maxWidth: '360px' }}>
        <div style={{ fontSize: '11px', fontWeight: '800', color: 'var(--teal-accent)', letterSpacing: '4px', marginBottom: '8px' }}>SCREEN</div>
        <div style={{ height: '4px', background: 'var(--teal-accent)', borderRadius: '9999px' }}></div>
      </div>

      {/* Seat Legend (seatdesign.PNG Reference) */}
      <div style={{ display: 'flex', gap: '16px', justifyContent: 'center', marginBottom: '24px', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
          <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-available)', borderRadius: 'var(--radius-sm)' }}></div>
          <span>예매 가능</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
          <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-selected)', borderRadius: 'var(--radius-sm)' }}></div>
          <span>선점 (내 선택)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
          <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--warning-amber)', borderRadius: 'var(--radius-sm)' }}></div>
          <span>결제 중</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
          <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-booked)', borderRadius: 'var(--radius-sm)' }}></div>
          <span>매진</span>
        </div>
      </div>

      {selection.message ? (
        <div className="info-banner" style={{ marginBottom: '16px', fontSize: '13px', textAlign: 'center' }}>
          {selection.message}
        </div>
      ) : null}

      {/* Dynamic Grid Map (seatdesign.PNG Reference) */}
      {seats.length > 0 && cols.length > 0 ? (
        <div 
          style={{ 
            display: 'grid', 
            gridTemplateColumns: `30px repeat(${cols.length}, minmax(24px, 1fr))`, 
            gap: '8px', 
            alignItems: 'center',
            maxWidth: '100%',
            overflowX: 'auto',
            padding: '4px'
          }}
          aria-label="좌석표"
        >
          {/* Header Column Labels */}
          <div></div>
          {cols.map(c => (
            <div key={`header-col-${c}`} style={{ textAlign: 'center', fontSize: '11px', fontWeight: '800', color: 'var(--teal-accent)' }}>
              {c}
            </div>
          ))}

          {/* Rows */}
          {rows.map(r => (
            <Fragment key={`row-${r}`}>
              {/* Row Label (left side) */}
              <div style={{ fontWeight: '800', color: 'var(--teal-accent)', fontSize: '11px', textAlign: 'center' }}>
                {r}
              </div>

              {/* Seats in Row */}
              {cols.map(c => {
                const seatLabel = `${r}-${c}`;
                const seat = seats.find(s => s.label === seatLabel);

                if (!seat) {
                  return <div key={`empty-${seatLabel}`} style={{ aspectRatio: '1/1' }}></div>;
                }

                const mine = seat.label === selectedSeatLabel;
                const disabled = !readOnly && (seat.status !== 'AVAILABLE' || !selection.allowed);

                // Select background color based on status and ownership
                let bg = 'var(--seat-available)';
                if (seat.status === 'RESERVED') {
                  bg = 'var(--seat-booked)';
                } else if (seat.status === 'PAYMENT_IN_PROGRESS') {
                  bg = 'var(--warning-amber)';
                } else if (seat.status === 'HELD' || mine) {
                  bg = 'var(--seat-selected)';
                }

                return (
                  <button
                    key={seat.id}
                    type="button"
                    disabled={disabled}
                    tabIndex={readOnly ? -1 : undefined}
                    title={`${seat.label} - ${seat.status}${mine ? ' (내 좌석)' : ''}`}
                    onClick={readOnly ? undefined : () => onSelectSeat?.(seat.id)}
                    style={{
                      aspectRatio: '1/1',
                      width: '100%',
                      maxWidth: '36px',
                      minWidth: '24px',
                      backgroundColor: bg,
                      border: mine ? '2px solid var(--primary-indigo)' : '1px solid transparent',
                      borderRadius: 'var(--radius-sm)',
                      cursor: readOnly || disabled ? 'default' : 'pointer',
                      transition: 'all 0.15s ease',
                      outline: 'none',
                      boxShadow: mine ? '0 0 6px var(--primary-indigo)' : 'none'
                    }}
                  />
                );
              })}
            </Fragment>
          ))}
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)' }}>
          등록된 좌석이 없습니다.
        </div>
      )}
    </section>
  );
}
