# Distributed Simulation Flow Design

## Goal

Build the next backend milestone for the portfolio demo: after a simulation run starts, virtual users should enter a Redis-backed waiting queue, compete for randomly selected seats through both API instances, retry on seat conflicts, and send successful holds into the Kafka payment flow.

This milestone turns the current distributed traffic foundation into a visible reservation simulation. It must keep the traffic path realistic:

```text
client -> nginx -> api-a/api-b -> traffic-generator -> nginx -> api-a/api-b -> Redis/PostgreSQL/Kafka/worker
```

## Scope

In scope:

- Redis-backed queue entry for generated virtual users.
- Simulation-scoped virtual user state in Redis snapshots.
- Random seat selection from remaining simulation seats.
- PostgreSQL-backed seat hold attempts through `SeatReservationService`.
- Conflict timeline entries such as `이미 선택된 좌석입니다`.
- Retry behavior for users that fail to hold a seat.
- Kafka `PaymentRequestedEvent` publication after a successful hold.
- Worker-produced payment result handling that updates user/seat state.
- Snapshot data that the future React UI can render in Korean.

Out of scope:

- React/Vite frontend separation.
- Production AWS provisioning.
- Real payment integration.
- Advanced fairness algorithms beyond a simple Redis waiting queue and admission limit.
- Long-term analytics storage.

## Architecture

The traffic generator remains the source of virtual-user HTTP traffic. It should not directly mutate Redis, PostgreSQL, or Kafka. Each generated user calls public API endpoints through nginx so the work is naturally distributed across `api-a` and `api-b`.

The API owns user commands:

- `POST /api/simulations/{simulationId}/users/{userId}/queue`
- `POST /api/simulations/{simulationId}/users/{userId}/seat-attempt`

The first command registers the user in Redis and places them in the waiting queue. The second command represents a real user attempting to select a seat after admission. The generator can call these commands in sequence with bounded retry loops.

Redis is the live simulation read model. PostgreSQL remains the concurrency authority for seat holds. Kafka connects successful holds to asynchronous payment processing.

## Data Flow

1. `POST /api/simulations` creates a simulation snapshot, initializes simulation-scoped seats, and marks the simulation as not running.
2. `POST /api/simulations/{id}/run` marks the simulation running and asks `traffic-generator` to start virtual users.
3. Each virtual user calls `/queue` through nginx.
4. The API records the user as queued in Redis and increments server request stats.
5. The virtual user calls `/seat-attempt` through nginx.
6. The API checks whether the user may enter from the Redis queue. If not, the response is `WAITING`.
7. If admitted, the API chooses a random available seat from the simulation snapshot.
8. The API calls `SeatReservationService.holdSeat(simulationId, userId, seatId, idempotencyKey)`.
9. If the hold fails because the seat is already held, the API records a conflict timeline entry and returns `RETRY`.
10. If the hold succeeds, the API records the selected seat, publishes `PaymentRequestedEvent`, and returns `PAYMENT_REQUESTED`.
11. The worker consumes `payment.events`, produces `payment.results`, and includes `simulationId`, `virtualUserId`, `seatId`, and `handledBy`.
12. The API-side payment result listener applies the result to Redis snapshot state.

## State Model

The snapshot returned by `GET /api/simulations/{simulationId}` should remain the UI contract. It should include:

- seats with `AVAILABLE`, `HELD`, `PAYMENT_IN_PROGRESS`, or `RESERVED` status.
- users with current status, selected seat, timeline, payment attempts, and seat selection attempts.
- metrics including queue size, selecting count, payment count, reserved count, failed count, and conflict count.
- server stats for `api-a`, `api-b`, and `worker`.

Redis keys should stay simulation-scoped through `SimulationRedisKeys`.

## Random Seat Selection

Seat selection must be random enough to create visible contention. The API should choose from seats that the Redis snapshot still sees as available. Because two API servers may choose the same seat concurrently, PostgreSQL remains the final authority. If PostgreSQL rejects or the update count is zero, that attempt is a conflict.

Conflict attempts count as seat selection attempts. If no seat is available, that also counts as an attempt and the user timeline should explain that there are no selectable seats.

## Payment Flow

After a successful seat hold, the API publishes `PaymentRequestedEvent` to `payment.events`.

The existing deterministic worker rule can stay for this milestone:

- reservation id divisible by 5: payment failure
- otherwise: payment success

A new payment result listener applies `PaymentResultEvent` to the Redis snapshot:

- success: user `COMPLETED`, seat `RESERVED`
- failure: user `PAYMENT_FAILED`, seat released or marked available for retry, timeline records `결제 실패`

Payment failure is not the main demonstration path, but it should remain visible because Kafka is part of the portfolio goal.

## Error Handling

User command endpoints should return domain statuses rather than raw exceptions:

- `QUEUED`: user entered the waiting queue.
- `WAITING`: user is still waiting for admission.
- `RETRY`: user attempted a seat but hit a conflict or no available seat.
- `PAYMENT_REQUESTED`: user held a seat and payment was published.
- `COMPLETED`: user already completed.
- `FAILED`: command could not proceed because the simulation is missing or stopped.

Unexpected infrastructure exceptions may still surface as 5xx during local development, but domain conflicts should be captured in the snapshot and timeline.

## Testing

Unit tests should cover:

- queue command mutates Redis-backed simulation state.
- seat attempt increments selection attempt count.
- random seat selection uses remaining available seats.
- conflicts record `이미 선택된 좌석입니다`.
- successful holds publish `PaymentRequestedEvent`.
- payment result listener updates user, seat, metrics, and server stats.
- traffic generator calls queue and seat attempt endpoints through the configured nginx base URL.

Local runtime verification should cover:

- `docker compose up -d --build`
- `POST /api/simulations`
- `POST /api/simulations/{id}/run`
- `GET /api/simulations/{id}` showing queue, seats, users, and server stats changing.
- nginx logs showing generated `/queue` and `/seat-attempt` requests.

## Acceptance Criteria

- A run with at least 20 virtual users produces queue entries and seat attempts.
- Both `api-a` and `api-b` handle generated user commands.
- The snapshot shows random seat selection attempts, including conflicts.
- Users that fail a seat attempt can retry until they hold a seat, complete payment, or no seats remain.
- Successful holds publish Kafka payment requests.
- Payment results are reflected in the snapshot.
- The current static dashboard may remain temporary, but the API contract should be ready for the later React dashboard.
