import type { SeatStatus, SeatView, SimulationSnapshot, VirtualUserStatus } from '../api/simulationApi';

export function isSimulationTerminal(snapshot: SimulationSnapshot): boolean {
  return snapshot.users.length > 0 && snapshot.users.every((user) => user.status === 'RESERVED' || user.status === 'FAILED');
}

export function getDefaultSelectedUserId(snapshot: SimulationSnapshot): string | null {
  if (snapshot.users.length === 0) {
    return null;
  }
  const sorted = [...snapshot.users].sort((left, right) => {
    if (right.conflictCount !== left.conflictCount) {
      return right.conflictCount - left.conflictCount;
    }
    if (left.status === 'FAILED' && right.status !== 'FAILED') {
      return -1;
    }
    if (right.status === 'FAILED' && left.status !== 'FAILED') {
      return 1;
    }
    return left.displayName.localeCompare(right.displayName, 'ko');
  });
  return sorted[0].id;
}

export function countSeatsByStatus(seats: SeatView[]): Record<SeatStatus, number> {
  return seats.reduce<Record<SeatStatus, number>>(
    (counts, seat) => {
      counts[seat.status] += 1;
      return counts;
    },
    { AVAILABLE: 0, HELD: 0, PAYMENT_IN_PROGRESS: 0, RESERVED: 0 },
  );
}

export function getUserStatusLabel(status: VirtualUserStatus): string {
  const labels: Record<VirtualUserStatus, string> = {
    QUEUED: '대기 중',
    SELECTING_SEAT: '좌석 선택',
    PAYMENT_IN_PROGRESS: '결제 중',
    RESERVED: '예약 완료',
    FAILED: '실패',
  };
  return labels[status];
}

export function getSeatClassName(status: SeatStatus, highlighted = false): string {
  const classes: Record<SeatStatus, string> = {
    AVAILABLE: 'seat seat-available',
    HELD: 'seat seat-held',
    PAYMENT_IN_PROGRESS: 'seat seat-payment',
    RESERVED: 'seat seat-reserved',
  };
  return highlighted ? `${classes[status]} seat-highlighted` : classes[status];
}

export function shortenId(id: string | null): string {
  return id ? id.slice(0, 8) : '-';
}
