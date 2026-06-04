import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { QueuePanel } from './QueuePanel';

describe('QueuePanel', () => {
  it('shows queue size and my approximate position', () => {
    const snapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'COUNTDOWN',
      generation: 1,
      opensAt: '2026-05-28T12:01:00Z',
      endsAt: '2026-05-28T12:06:00Z',
      seats: [],
      metrics: { queueSize: 2, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [],
      running: false,
      myParticipantId: 'me',
      myQueuePosition: 42,
      participants: [
        { id: 'me', displayName: '나', type: 'HUMAN', status: 'QUEUED', selectedSeatLabel: null, timeline: [], seatAttemptCount: 0, conflictCount: 0, paymentAttemptCount: 0, reservationId: null },
        { id: 'ai-1', displayName: 'AI-1', type: 'AI', status: 'QUEUED', selectedSeatLabel: null, timeline: [], seatAttemptCount: 0, conflictCount: 0, paymentAttemptCount: 0, reservationId: null },
      ],
    } satisfies LiveEventSnapshot;

    render(
      <QueuePanel
        snapshot={snapshot}
        participantId="me"
        selectedParticipantId={null}
        onSelectParticipant={() => {}}
      />
    );

    expect(screen.getByText('대기열')).toBeInTheDocument();
    expect(screen.getByText('2명')).toBeInTheDocument();
    expect(screen.getByText('42번째')).toBeInTheDocument();
    expect(screen.getAllByText('대기열 대기').length).toBeGreaterThan(0);
  });
});
