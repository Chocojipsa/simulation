# User-Started Live Event Design

## Goal

Convert the current participatory ticketing demo from an always-open event into a user-started live event. Any visitor can start the event, everyone sees the same countdown, AI participants enter after the event opens, and the event ends after a fixed demo window so a new event can be started.

The portfolio value is to make the flow feel like a real ticketing opening while still being easy to demonstrate locally:

- shared event state across multiple API servers
- Redis-backed queue/read model visible in the UI
- PostgreSQL-backed seat hold authority
- Kafka-backed payment confirmation
- AI traffic entering through the same nginx/api-a/api-b path as human users

## Event Lifecycle

The live event has four states:

1. `READY`
   - No ticketing has started.
   - Any visitor can enter the event room.
   - `이벤트 시작하기` is visible.
   - Seat selection and payment confirmation are disabled.

2. `COUNTDOWN`
   - Started by a visitor pressing `이벤트 시작하기`.
   - Countdown duration is 60 seconds.
   - All browsers see the same `opensAt`.
   - Users can enter and join the waiting queue.
   - Seat selection remains disabled with a clear message.

3. `OPEN`
   - Starts automatically when `opensAt <= now`.
   - Ticketing remains open for 5 minutes.
   - Users can select seats and confirm payment.
   - AI participants begin entering in staggered batches.

4. `ENDED`
   - Starts automatically when `endsAt <= now`.
   - Seat selection, queue entry, payment confirmation, and AI start are disabled.
   - Results remain visible.
   - `새 이벤트 시작` is visible and creates the next event generation.

## Start Semantics

The button is not an admin-only control. It is a visitor-facing demo control.

Only one visitor needs to press `이벤트 시작하기`. The backend must make the state transition atomically so duplicate clicks from multiple browsers do not create conflicting countdowns.

If an event is already `COUNTDOWN` or `OPEN`, the start button is hidden or disabled and start requests return the current event state instead of restarting the timer.

If the event is `ENDED`, the visible command is `새 이벤트 시작`. It resets the seat inventory, participant read model, queue state, server stats, and event timings for a new generation while preserving the configured shared event id.

## Timing

Default timing:

- countdown: 60 seconds
- open window: 5 minutes
- end time: `opensAt + 5 minutes`

These values should be configurable from local/prod properties:

- `live-event.countdown-seconds`
- `live-event.open-window-seconds`

## Backend State

The event metadata should be stored outside API process memory because api-a and api-b must agree on the same state.

Recommended storage for this slice:

- Redis key for live event metadata/read model
- PostgreSQL remains the authoritative store for seat holds and reservation/payment state

Event metadata fields:

- `eventId`
- `generation`
- `status`
- `createdAt`
- `opensAt`
- `endsAt`
- `aiStarted`

`generation` lets the UI and tests distinguish a new run even when using the same configured shared `eventId`.

Status derivation should be server-side. If Redis metadata says `COUNTDOWN` but `opensAt <= now`, snapshot/start commands should return `OPEN`. If `endsAt <= now`, commands should return `ENDED`.

## API Shape

Keep existing event APIs and add lifecycle commands:

- `GET /api/events/active`
  - Returns active event metadata including `status`, `opensAt`, `endsAt`, and `generation`.

- `POST /api/events/{eventId}/start`
  - Allowed in `READY`.
  - Sets `COUNTDOWN`, `opensAt = now + 60s`, `endsAt = opensAt + 5m`.
  - Returns the current event metadata.

- `POST /api/events/{eventId}/reset`
  - Allowed in `ENDED`.
  - Clears queue/read model/inventory for the next generation.
  - Returns `READY`.

Existing commands become state-aware:

- join participant: allowed in `READY`, `COUNTDOWN`, `OPEN`
- enter queue: allowed in `COUNTDOWN`, `OPEN`
- hold seat: allowed only in `OPEN`
- confirm payment: allowed only in `OPEN`
- start AI: internal or hidden command; only runs once per generation after `OPEN`

## AI Staggering

AI participants should not all enter at exactly 0.00 seconds after opening.

When the event transitions to `OPEN`, AI traffic is started once and distributed across small batches. A default 150-user run can be split like:

- +0ms: 10 users
- +100ms: 20 users
- +300ms: 30 users
- +700ms: 40 users
- +1200ms: 50 users

The exact distribution can be implemented with a small schedule generator so different participant counts still spread naturally. The key behavior is that humans have a realistic chance to act during the opening burst.

AI must continue using the same event APIs:

1. join event
2. enter queue
3. select seat
4. confirm payment

## Queue UI

Add a visible waiting queue panel.

Minimum first version:

- current queue size
- my queue state
- approximate number ahead of me
- recent queue admissions or waiting events

For the first implementation, rank can be derived from snapshot participant order among `QUEUED` users. A later refinement can use Redis sorted-set rank if exact queue position is needed.

## Seat Selection Rules

The multiple-seat hold bug must be prevented on the backend.

If a participant already has one of these statuses:

- `SEAT_HELD`
- `PAYMENT_IN_PROGRESS`
- `RESERVED`

then an additional hold request must not create a second seat hold. The response should keep the existing seat and return a clear message.

Frontend should also disable other available seats while the participant already has a held/payment seat.

Seat click outcomes:

- success: seat is held and the payment confirmation area is emphasized
- conflict: show `이미 선점된 좌석입니다`
- before opening: show `티켓팅 시작 전입니다`
- ended: show `이벤트가 종료되었습니다`
- already holding: show `이미 선점한 좌석이 있습니다`

## Frontend Behavior

The main page remains the actual live event room, not a landing page.

Header:

- event title
- status label
- countdown timer or remaining open time
- reserved count

My ticket panel:

- before join: `이벤트 입장`
- joined and `READY`: waiting for start
- joined and `COUNTDOWN`: `예약하기` can enter queue
- queued and `OPEN`: seat selection prompt
- held/payment: selected seat and `결제 확인`
- reserved: reservation complete
- ended: final result

Activity panel:

- hides `AI 참가자 시작` during `COUNTDOWN` and `OPEN`
- shows `이벤트 시작하기` only in `READY`
- shows `새 이벤트 시작` only in `ENDED`

Seat map:

- available seats remain green
- held seats remain gray
- payment seats show a distinct in-progress state
- reserved seats show reserved state
- disabled states should still be visually readable

## Error Handling

Backend command responses should avoid 500 for expected user-flow conditions:

- seat unavailable
- event not open
- event ended
- participant already holds a seat
- participant not queued/admitted yet

These should return command responses with a status/message that the frontend can display.

Unexpected infrastructure failures can still return server errors, but normal ticketing race conditions should be visible as domain messages.

## Testing

Backend tests:

- event start moves `READY -> COUNTDOWN` with 60 second countdown
- derived status moves `COUNTDOWN -> OPEN -> ENDED`
- duplicate start requests do not reset countdown
- reset after ended increments generation and clears active state
- hold seat is rejected before `OPEN`
- participant cannot hold multiple seats
- AI start is triggered once per event generation

Frontend tests:

- start button only appears in `READY`
- countdown UI appears in `COUNTDOWN`
- seat map is disabled before `OPEN`
- queue panel shows queue size and my state
- held participant cannot click additional seats
- ended state shows `새 이벤트 시작`

Docker verification:

- repeated `/api/events/active` through nginx returns the same event id/status
- one browser/user can start countdown
- another participant sees the same countdown
- after open, human and AI participants share queue/seat/payment state
- after 5 minutes, commands are blocked and reset becomes available

## Self-Review

- No incomplete requirements remain.
- The design keeps scope focused on event lifecycle, AI staggering, queue visibility, and seat hold correctness.
- The button semantics are explicit: visitor-facing, not admin-only.
- The event timing is explicit: 60 second countdown and 5 minute open window.
- Multi-server consistency is addressed by moving lifecycle state out of API process memory.
