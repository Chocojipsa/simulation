# Concert Seat Reservation Simulation Design

## Purpose

Build a backend-focused simulation platform for a Java backend developer portfolio. The service models a concert ticket opening event where many virtual users enter a waiting queue, select seats, temporarily hold seats, and complete simulated payment.

The goal is not real user acquisition. The goal is to demonstrate backend and infrastructure capability through behavior that is visible, testable, and explainable in interviews.

The project must demonstrate:

- multi-instance backend request handling
- Redis-based waiting queue and temporary state
- PostgreSQL-based reservation consistency
- Kafka-based event processing
- failure handling, retry, idempotency, and observability
- separate local and production deployment topologies

## Language And Output Requirements

Design documents, implementation plans, code identifiers, database identifiers, API paths, topic names, and internal technical documentation should be written in English.

User-facing output must be Korean.

This includes:

- frontend UI labels
- dashboard headings
- seat status labels
- timeline messages
- error messages shown to visitors
- README sections that explain how to run and view the demo for Korean reviewers
- portfolio-facing screenshots or demo descriptions

The implementation should keep internal names in English and map them to Korean labels at the presentation layer. For example, internal state `PAYMENT_IN_PROGRESS` should be shown as `결제 진행 중` in the UI.

## Product Concept

The product is a concert seat reservation simulator.

A visitor opens the web dashboard and starts a simulation with a selected number of virtual users, such as 100, 500, or 1,000 users. The backend creates the simulation session and virtual users, then progresses each user through the ticketing flow. The visitor can select one virtual user and watch that user's timeline from queue entry to final reservation result.

The frontend also shows a live seat map. Seat colors represent current state:

- green: available, shown as `선택 가능`
- yellow: temporarily held, shown as `임시 선점`
- red: payment in progress, shown as `결제 진행 중`
- gray: reserved, shown as `예약 완료`

This live seat map makes backend state changes visible while the simulation is running.

## Core User Flow

1. Visitor starts a simulation from the dashboard.
2. Backend creates a simulation session and N virtual users.
3. Virtual users enter a Redis-backed waiting queue.
4. The system grants admission tokens to a limited number of users.
5. Admitted virtual users attempt to select seats.
6. A selected seat receives a short-lived temporary hold.
7. Reservation state is written through a PostgreSQL transaction.
8. Payment simulation is requested through a Kafka event.
9. Payment workers produce success or failure results.
10. Seats become reserved, released, or expired.
11. Frontend receives live updates through SSE.

## Architecture

The system has two deployment modes.

### Local Development

Local development uses Docker Compose to reproduce the full topology on one machine:

- local reverse proxy
- `api-a` Spring Boot container
- `api-b` Spring Boot container
- worker container
- PostgreSQL container
- Redis container
- Kafka container
- optional local frontend container

Local mode is for development, automated tests, and architecture demonstrations. It is not intended to prove infrastructure high availability.

### Production v1

Production v1 prioritizes runtime stability and cost control over managed load balancer experience.

Production v1 uses:

- Vercel for the Next.js frontend
- Lightsail A for self-hosted Nginx and `api-a`
- Lightsail B for `api-b` and worker
- Lightsail C for Redis and Kafka
- Amazon RDS PostgreSQL for durable state

Traffic flow:

```text
Vercel frontend
  -> api domain
  -> Nginx on Lightsail A
  -> api-a on Lightsail A
  -> api-b on Lightsail B
  -> Redis/Kafka on Lightsail C
  -> RDS PostgreSQL
```

Nginx is self-hosted on Lightsail A to avoid managed load balancer cost in the first production version. This is a known single entry point. Production v2 can replace it with Lightsail Load Balancer or AWS ALB.

### Production v2

Production v2 options:

- replace self-hosted Nginx with Lightsail Load Balancer or AWS ALB
- move Redis to ElastiCache
- move Kafka to MSK, Redpanda Cloud, or another managed event-streaming platform
- split worker into a separate instance
- add monitoring dashboards and alerting

## Component Responsibilities

### Frontend

The frontend is a thin observability and control UI. It should not become the main technical focus. Its job is to make backend behavior easy to see.

Responsibilities:

- start a simulation with N virtual users
- show a live seat map
- show a virtual user list
- show the selected virtual user's timeline
- show system metrics such as queue size, reservation success count, payment failure count, request distribution, and Kafka lag
- render all visitor-facing text in Korean

### Nginx

Local mode uses a local reverse proxy container. Production v1 uses self-hosted Nginx on Lightsail A.

Nginx responsibilities:

- provide one public API endpoint
- route requests to `api-a` and `api-b`
- terminate HTTPS in production v1
- disable buffering for SSE routes

This is not a fully highly available load balancer. That limitation should be documented clearly in the portfolio.

### API Servers

The API servers expose simulation, queue, seat, user timeline, and SSE endpoints.

Responsibilities:

- create simulation sessions
- create virtual users
- accept queue entry requests
- validate admission tokens
- process seat hold requests
- create reservations
- publish domain events through an outbox-backed path
- expose SSE streams for dashboard updates

### Redis

Redis handles fast, temporary, high-contention state.

Responsibilities:

- waiting queue order with a sorted set
- admission tokens with TTL
- temporary seat hold state with TTL
- counters for live dashboard metrics

Redis is not the final source of truth for completed reservations.

### PostgreSQL

PostgreSQL is the source of truth for durable state.

Responsibilities:

- concerts
- seat inventory
- simulation sessions
- virtual users
- seat holds
- reservations
- payments
- outbox events
- audit log records

PostgreSQL enforces correctness with transactions and constraints. A seat must not have more than one active reservation.

PostgreSQL-specific features to showcase:

- partial unique index for active seat reservation protection
- `SKIP LOCKED` for outbox publisher polling
- JSONB for event metadata or audit payloads
- outbox table for reliable event publication

### Kafka

Kafka carries asynchronous domain events after synchronous correctness decisions have been made.

Kafka is not responsible for deciding whether a seat can be reserved. Redis and PostgreSQL handle that critical path.

Kafka responsibilities:

- payment simulation
- seat hold expiration handling
- reservation status events
- audit log processing
- dashboard metric aggregation
- retry and DLQ demonstration

Candidate topics:

- `queue.events`
- `reservation.events`
- `payment.events`
- `seat-hold.events`
- `audit.events`
- `simulation-metrics.events`
- `payment.dlq`

### Workers

Worker applications consume Kafka events and run background workflows.

Responsibilities:

- simulate payment success or failure
- release seats after payment failure
- expire abandoned seat holds
- record audit logs
- aggregate simulation metrics
- handle retries and DLQ routing

## Data Flow

Critical decisions are synchronous. Side effects are asynchronous.

### Synchronous Path

1. A virtual user enters the queue.
2. Redis assigns queue order.
3. The API flow or an admission worker grants an admission token.
4. The user selects a seat.
5. Redis attempts a fast temporary hold.
6. A PostgreSQL transaction verifies and persists hold or reservation state.
7. The API returns `accepted`, `rejected`, or `expired`.

### Asynchronous Path

1. Reservation creation records an outbox event.
2. Outbox publisher emits the event to Kafka.
3. Payment worker consumes `PaymentRequested`.
4. Worker simulates success or failure.
5. Payment result event updates PostgreSQL state.
6. Metrics and audit consumers process the same event stream.
7. SSE stream publishes updated state to the frontend.

## Real-Time Updates

Use SSE for the first version.

The dashboard mostly observes server state. The frontend sends commands with normal HTTP requests, then receives seat map, metric, and timeline changes through server-sent events. This is simpler than WebSocket and fits the first version better.

Core endpoints:

- `POST /simulations`
- `GET /simulations/{simulationId}`
- `GET /simulations/{simulationId}/users`
- `GET /simulations/{simulationId}/users/{userId}`
- `GET /simulations/{simulationId}/events`

The `/events` endpoint streams:

- seat state changes
- queue size changes
- selected user timeline updates
- aggregate metrics
- simulation completion status

## State Model

### Seat States

- `AVAILABLE`
- `HELD`
- `PAYMENT_IN_PROGRESS`
- `RESERVED`

### Virtual User States

- `CREATED`
- `QUEUED`
- `ADMITTED`
- `SELECTING_SEAT`
- `SEAT_HELD`
- `PAYMENT_IN_PROGRESS`
- `RESERVED`
- `FAILED`
- `EXPIRED`

### Payment States

- `REQUESTED`
- `SUCCEEDED`
- `FAILED`
- `TIMED_OUT`

State transitions must be explicit. Invalid transitions must be rejected and recorded as errors or audit events.

## Korean Presentation Labels

The UI should map internal states to Korean labels.

Seat states:

- `AVAILABLE`: `선택 가능`
- `HELD`: `임시 선점`
- `PAYMENT_IN_PROGRESS`: `결제 진행 중`
- `RESERVED`: `예약 완료`

Virtual user states:

- `CREATED`: `생성됨`
- `QUEUED`: `대기 중`
- `ADMITTED`: `입장 허가`
- `SELECTING_SEAT`: `좌석 선택 중`
- `SEAT_HELD`: `좌석 선점`
- `PAYMENT_IN_PROGRESS`: `결제 진행 중`
- `RESERVED`: `예약 성공`
- `FAILED`: `실패`
- `EXPIRED`: `만료`

Payment states:

- `REQUESTED`: `결제 요청됨`
- `SUCCEEDED`: `결제 성공`
- `FAILED`: `결제 실패`
- `TIMED_OUT`: `결제 시간 초과`

Dashboard labels:

- `시뮬레이션 시작`
- `가상 사용자 수`
- `실시간 좌석표`
- `가상 사용자 목록`
- `선택한 사용자 타임라인`
- `대기열 크기`
- `예약 성공`
- `결제 실패`
- `서버별 요청 비율`
- `Kafka 지연`

## Failure Handling

The simulator should intentionally include realistic failure cases.

Failure cases:

- admission token expires before seat selection
- seat hold expires before payment
- simulated payment fails
- duplicate request arrives with the same idempotency key
- two API servers race for the same seat
- Kafka consumer fails during payment processing
- event repeatedly fails and moves to DLQ

Required patterns:

- idempotency key for queue, seat hold, reservation, and payment commands
- outbox pattern for PostgreSQL state changes and Kafka publication
- retry policy for Kafka consumers
- DLQ for repeatedly failing events
- scheduled cleanup for expired holds and timed-out payments
- state machine checks for seat, reservation, virtual user, and payment status

## Testing Strategy

Testing should prove correctness under contention and produce results that are useful in a portfolio discussion.

Test categories:

- unit tests for state transition rules
- repository tests for PostgreSQL constraints and indexes
- Redis tests for queue ordering and TTL behavior
- Kafka integration tests for event publication, retry, and DLQ behavior
- SSE tests for dashboard event delivery
- concurrency tests proving reservations never exceed available seats
- multi-instance tests proving distributed requests do not create duplicate reservations
- load tests with 100, 500, and 1,000 virtual users

Important acceptance checks:

- 1,000 virtual users competing for 100 seats must not create more than 100 successful reservations.
- duplicate reservation requests must not create duplicate reservations or duplicate payments.
- payment failure must release or expire the affected seat.
- Kafka worker failure must be observable through retry count or DLQ entry.
- API server A/B request distribution must not affect final consistency.
- frontend screenshots and runtime UI must be understandable to Korean reviewers.

## Technology Stack

Backend:

- Java 17
- Spring Boot
- Spring MVC
- Gradle Groovy DSL
- Spring Data JPA
- native SQL where PostgreSQL-specific features are needed
- Spring Kafka
- Spring Data Redis

Frontend:

- React or Next.js
- Vercel deployment
- SSE client
- seat map dashboard
- Korean user-facing UI

Local infrastructure:

- Docker Compose
- local reverse proxy
- two API containers
- one worker container
- PostgreSQL
- Redis
- Kafka

Production v1 infrastructure:

- Vercel frontend
- Lightsail A: Nginx + `api-a`
- Lightsail B: `api-b` + worker
- Lightsail C: Redis + Kafka
- RDS PostgreSQL

Testing and validation:

- JUnit
- Testcontainers
- k6 or Gatling

## Scope Boundaries

The first version should stay focused on backend portfolio value.

In scope:

- one concert event
- fixed seat map
- backend-generated virtual users
- simulated payment
- live seat status
- selected virtual user timeline
- backend metrics dashboard
- Kafka retry and DLQ demonstration
- Korean visitor-facing output
- local Docker Compose topology
- production v1 deployment plan using Vercel, Lightsail, and RDS

Out of scope for the first version:

- real user accounts
- real payment gateway
- admin CMS
- multiple venues
- complex seat pricing
- mobile-specific UI
- fully managed production load balancer
- managed Redis
- managed Kafka

## Success Criteria

The project succeeds if a reviewer can run the local environment, start a simulation, watch seats change in real time, inspect one virtual user's timeline, and verify that the backend remains consistent under concurrent load.

The project should clearly answer:

- why Redis is used
- why PostgreSQL is the source of truth
- why Kafka is used
- how duplicate requests are handled
- how seat overbooking is prevented
- how failures are retried or isolated
- how two API instances remain consistent
- how local and production deployment topologies differ

The final demo should be easy for a Korean reviewer to understand without reading internal code.
