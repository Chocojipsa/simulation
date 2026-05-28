import { afterEach, describe, expect, it, vi } from 'vitest';
import { confirmPayment, fetchActiveEvent, holdSeat, joinEvent, queueParticipant, startAiParticipants } from './liveEventApi';

describe('liveEventApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls event endpoints with participant identity', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ ok: true })));

    await fetchActiveEvent('');
    await joinEvent('', 'event-1', '권');
    await queueParticipant('', 'event-1', 'participant-1');
    await holdSeat('', 'event-1', 'participant-1', 7);
    await confirmPayment('', 'event-1', 'participant-1');
    await startAiParticipants('', 'event-1', 150, 50);

    const calls = fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET']);
    expect(calls).toEqual([
      ['/api/events/active', 'GET'],
      ['/api/events/event-1/participants', 'POST'],
      ['/api/events/event-1/participants/participant-1/queue', 'POST'],
      ['/api/events/event-1/participants/participant-1/seats/7/hold', 'POST'],
      ['/api/events/event-1/participants/participant-1/payment-confirm', 'POST'],
      ['/api/events/event-1/ai/start', 'POST'],
    ]);
  });
});
