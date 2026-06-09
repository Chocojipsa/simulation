import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { EventActivityPanel } from './EventActivityPanel';

vi.mock('../api/liveEventApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/liveEventApi')>();
  return {
    ...actual,
    fetchParticipantTimeline: vi.fn().mockResolvedValue([
      { label: '대기열 통과', message: '좌석을 선택해 주세요.' }
    ]),
  };
});

describe('EventActivityPanel', () => {
  it('separates my progress from the full event log', async () => {
    const snapshot: LiveEventSnapshot = {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      generation: 1,
      opensAt: '2026-05-28T12:00:00Z',
      endsAt: '2026-05-28T12:05:00Z',
      seats: [],
      metrics: { queueSize: 0, admittedCount: 2, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 3, conflictCount: 0, successCount: 2 }],
      running: true,
      myParticipantId: 'me',
      myQueuePosition: null,
      participants: [
        {
          id: 'me',
          displayName: '나',
          type: 'HUMAN',
          status: 'SELECTING_SEAT',
          selectedSeatLabel: null,
          timeline: [{ label: '대기열 통과', message: '좌석을 선택해 주세요.' }],
          seatAttemptCount: 0,
          conflictCount: 0,
          paymentAttemptCount: 0,
          reservationId: null,
        },
        {
          id: 'ai-1',
          displayName: 'AI-1',
          type: 'AI',
          status: 'RESERVED',
          selectedSeatLabel: 'B-2',
          timeline: [{ label: '결제 성공', message: '예매 완료' }],
          seatAttemptCount: 1,
          conflictCount: 0,
          paymentAttemptCount: 1,
          reservationId: 102,
        },
      ],
    };

    render(<EventActivityPanel snapshot={snapshot} participantId="me" />);

    expect(screen.getByText('내 진행')).toBeInTheDocument();
    expect(screen.getByText('시스템 알림')).toBeInTheDocument();
    expect(screen.getByText('네트워크 최적화 활성화됨')).toBeInTheDocument();
    expect(await screen.findAllByText('좌석을 선택해 주세요.')).toHaveLength(1);
  });
});


