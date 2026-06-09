import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { InsightPanel } from './InsightPanel';

describe('InsightPanel', () => {
  it('renders server stats and metrics from LiveEventSnapshot', () => {
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: 'Test Event',
      status: 'OPEN',
      generation: 1,
      opensAt: null,
      endsAt: null,
      seats: [
        { id: 1, label: 'A-1', status: 'AVAILABLE' },
        { id: 2, label: 'A-2', status: 'RESERVED' },
      ],
      participants: [],
      metrics: { queueSize: 10, admittedCount: 5, heldCount: 0, paymentInProgressCount: 2, reservedCount: 1, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 100, conflictCount: 5, successCount: 95 }],
      running: true,
      myParticipantId: null,
      myQueuePosition: null,
    };

    render(<InsightPanel snapshot={snapshot} metrics={null} />);

    expect(screen.getByText('api-a')).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
    expect(screen.getByText(/충돌 5/)).toBeInTheDocument();
    expect(screen.getByText(/성공 95/)).toBeInTheDocument();
  });
});
