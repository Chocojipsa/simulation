import { describe, expect, it } from 'vitest';
import type { SimulationSnapshot } from '../api/simulationApi';
import {
  countSeatsByStatus,
  getDefaultSelectedUserId,
  getSeatClassName,
  getUserStatusLabel,
  isSimulationTerminal,
  shortenId,
} from './simulationSelectors';

const snapshot: SimulationSnapshot = {
  simulationId: 'e318821a-8281-4820-b8cb-34c3c03c4e07',
  seats: [
    { id: 1, label: 'A-1', status: 'AVAILABLE' },
    { id: 2, label: 'A-2', status: 'RESERVED' },
    { id: 3, label: 'A-3', status: 'PAYMENT_IN_PROGRESS' },
  ],
  users: [
    {
      id: 'u1',
      displayName: '사용자 1',
      status: 'RESERVED',
      selectedSeatLabel: 'A-2',
      timeline: [],
      seatAttemptCount: 1,
      conflictCount: 0,
    },
    {
      id: 'u2',
      displayName: '사용자 2',
      status: 'FAILED',
      selectedSeatLabel: 'A-1',
      timeline: [],
      seatAttemptCount: 30,
      conflictCount: 30,
    },
  ],
  metrics: { queueSize: 0, admittedCount: 1, heldCount: 0, paymentInProgressCount: 1, reservedCount: 1, failedCount: 30 },
  serverStats: [],
  running: true,
};

describe('simulationSelectors', () => {
  it('detects terminal simulations', () => {
    expect(isSimulationTerminal(snapshot)).toBe(true);
    expect(isSimulationTerminal({ ...snapshot, users: [{ ...snapshot.users[0], status: 'QUEUED' }] })).toBe(false);
  });

  it('selects the user with highest conflict count first', () => {
    expect(getDefaultSelectedUserId(snapshot)).toBe('u2');
  });

  it('counts seats by status', () => {
    expect(countSeatsByStatus(snapshot.seats)).toEqual({
      AVAILABLE: 1,
      HELD: 0,
      PAYMENT_IN_PROGRESS: 1,
      RESERVED: 1,
    });
  });

  it('maps statuses to Korean labels and class names', () => {
    expect(getUserStatusLabel('FAILED')).toBe('실패');
    expect(getSeatClassName('PAYMENT_IN_PROGRESS')).toBe('seat seat-payment');
  });

  it('shortens simulation ids', () => {
    expect(shortenId(snapshot.simulationId)).toBe('e318821a');
  });
});
