import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { EventParticipantView } from '../api/liveEventApi';
import { MyTicketPanel } from './MyTicketPanel';

describe('MyTicketPanel', () => {
  it('shows readable participant state and hold expiration', () => {
    const participant: EventParticipantView = {
      id: 'me',
      displayName: '게스트-1234',
      type: 'HUMAN',
      status: 'SEAT_HELD',
      selectedSeatLabel: 'A-1',
      timeline: [],
      seatAttemptCount: 1,
      conflictCount: 0,
      paymentAttemptCount: 0,
      reservationId: 101,
      seatHoldExpiresAt: '2026-05-28T12:01:00Z',
    };

    render(
      <MyTicketPanel
        status="OPEN"
        participant={participant}
        loading={false}
        now={new Date('2026-05-28T12:00:15Z')}
        onJoin={vi.fn()}
        onReserve={vi.fn()}
        onPay={vi.fn()}
      />,
    );

    expect(screen.getByText('좌석 선점')).toBeInTheDocument();
    expect(screen.getByText('45초 남음')).toBeInTheDocument();
    expect(screen.getByText('A-1')).toBeInTheDocument();
  });
});
