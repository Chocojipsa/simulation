# Portfolio Demo MVP Replan Design

## Purpose

This document supersedes the first MVP direction where one API instance started and owned an entire in-memory simulation.

The revised goal is to build a backend-focused portfolio demo where virtual users behave like separate clients. Their requests must pass through the local reverse proxy and be distributed across multiple API instances. Redis, Kafka, and PostgreSQL must be visible in the product experience, not only present in the infrastructure.

The first milestone is a local portfolio demo MVP:

- React/Vite frontend separated from Spring Boot
- traffic-generator service that creates virtual user HTTP traffic
- nginx distributing traffic to `api-a` and `api-b`
- Redis-backed shared simulation state
- Kafka event flow visualization
- PostgreSQL reservation consistency visualization
- production configuration boundaries prepared, without creating AWS resources yet

## Confirmed Decisions

- Frontend: React + Vite
- Infra visibility: application-level observability panels, not only external tools
- Simulation state for local/prod: Redis, not API instance memory
- Kafka visualization: user-level event flow panel
- Production preparation: configuration files and deployment documentation, no real deployment in this milestone
- Virtual user traffic: separate traffic-generator service
- Seat model: simulation-scoped seat inventory
- Multi-simulation support: isolate all state by `simulationId`

## Core Architecture

```text
React/Vite frontend
  -> POST /api/simulations
  -> POST /api/simulations/{simulationId}/run
  -> GET /api/simulations/{simulationId}
  -> GET /api/simulations/{simulationId}/events

traffic-generator
  -> sends N virtual user HTTP requests to nginx

nginx
  -> round-robin or least-conn routing
  -> api-a
  -> api-b

api-a / api-b
  -> Redis queue and shared simulation state
  -> PostgreSQL seat hold and reservation transactions
  -> Kafka payment request events

worker
  -> consumes Kafka payment requests
  -> simulates payment result
  -> updates PostgreSQL
  -> updates Redis observable state
  -> publishes Kafka result events
```

The local nginx configuration must not use sticky routing for simulation commands. Sticky routing was only a workaround for in-memory state and conflicts with the goal of demonstrating multi-server distribution.

## Runtime Flow

### 1. Create Simulation

The frontend creates a simulation:

```text
POST /api/simulations
```

The backend creates:

- a `simulations` row in PostgreSQL
- simulation-scoped seats in PostgreSQL
- initial Redis snapshot keys
- initial dashboard state

The response includes `simulationId`.

### 2. Start Traffic Generation

The frontend starts the run:

```text
POST /api/simulations/{simulationId}/run
```

The traffic-generator receives:

- `simulationId`
- virtual user count
- request pacing settings
- target base URL, normally nginx inside Docker Compose

Then it creates N virtual user flows. Each virtual user sends real HTTP requests through nginx, so requests are distributed to `api-a` and `api-b`.

### 3. Virtual User Flow

Each virtual user follows this flow:

```text
enter queue
  -> wait for admission
  -> choose a random available-looking seat
  -> attempt seat hold
  -> retry on conflict
  -> request payment
  -> wait for payment result
  -> finish as RESERVED or FAILED
```

The API response should include `handledBy`, such as `api-a` or `api-b`, so the frontend can show real request distribution.

### 4. Payment Flow

Payment is asynchronous:

```text
API publishes PaymentRequested to Kafka
worker consumes PaymentRequested
worker simulates success/failure
worker publishes PaymentResult
worker updates PostgreSQL reservation state
worker updates Redis dashboard state
```

The frontend should show this as a per-user event flow:

```text
seat held -> Kafka PaymentRequested -> worker processed -> Kafka PaymentResult -> reservation finalized
```

## Multi-Simulation Isolation

Multiple simulations may run at the same time. They must not share runtime state or seat inventory.

Every stateful component must include `simulationId`.

### Redis Keys

Use simulation-scoped keys:

```text
simulation:{simulationId}:queue
simulation:{simulationId}:users
simulation:{simulationId}:snapshot
simulation:{simulationId}:server-stats
simulation:{simulationId}:events
simulation:{simulationId}:kafka-flow
```

Redis keys must have TTLs, for example one to three hours, so repeated demos do not leave permanent temporary state.

### Kafka Events

Kafka topics can be shared across simulations, but every event payload must include:

- `simulationId`
- `virtualUserId`
- `reservationId` when available
- `seatId` when available
- `eventType`
- `handledBy` or `producer`
- `occurredAt`

The frontend and observability APIs filter events by `simulationId`.

### PostgreSQL Tables

Use simulation-scoped durable data:

```text
simulations
simulation_seats
virtual_users
reservations
payment_attempts
reservation_events
```

Important constraints:

```text
primary key(simulation_id, seat_id) on simulation_seats
unique(simulation_id, virtual_user_id) for final reservation ownership
unique active reservation per (simulation_id, seat_id)
```

This means simulation A and simulation B can both reserve seat `A-1` independently because they are separate demo runs.

## Component Responsibilities

### Frontend

The frontend is a Korean dashboard for control and observability.

Responsibilities:

- create simulations
- start traffic generation
- show live seat map
- show Redis waiting queue panel
- show Kafka event flow panel
- show PostgreSQL reservation panel
- show per-user timeline
- show server distribution, such as request counts for `api-a` and `api-b`
- show errors clearly when a simulation or stream fails

The frontend should be a Vite React app in a new `frontend` directory. It should call the backend through `/api`.

### Traffic Generator

The traffic-generator is a backend-side simulator of real client traffic.

Responsibilities:

- accept a run command for a simulation
- create N virtual user flows
- send HTTP requests to nginx, not directly to API containers
- support configurable concurrency and pacing
- record generated request outcomes
- stop when all virtual users finish or when the simulation is cancelled

The generator may be implemented as a separate Spring Boot profile, separate Java module, or separate process inside the existing backend build. The key architectural rule is that virtual users must enter through nginx.

### Nginx

Responsibilities:

- serve or proxy the frontend in local mode
- proxy `/api/**` to `api-a` and `api-b`
- distribute traffic without sticky routing
- support SSE routes with buffering disabled
- expose local demo on one browser URL

### API Servers

Responsibilities:

- create simulation records
- process virtual user commands
- register queue entry in Redis
- update shared Redis state
- perform PostgreSQL seat hold and reservation transactions
- publish Kafka payment events
- return `handledBy`
- expose simulation snapshot and SSE stream

API servers must not keep authoritative simulation state in memory for local or prod profiles.

### Redis

Responsibilities:

- waiting queue
- shared live simulation snapshot
- virtual user current state
- recent user timeline entries
- server distribution counters
- recent Kafka flow entries for frontend display

Redis is not the durable source of truth for final reservations.

### PostgreSQL

Responsibilities:

- simulation records
- simulation-scoped seat inventory
- reservation consistency
- payment attempt records
- event/audit history where useful

PostgreSQL must prevent overbooking even when `api-a` and `api-b` race for the same seat.

### Kafka

Responsibilities:

- payment request events
- payment result events
- optional reservation/audit events
- visible event flow for the dashboard

Kafka does not decide seat ownership. It carries asynchronous events after synchronous consistency checks.

### Worker

Responsibilities:

- consume payment requests
- simulate payment success/failure
- update PostgreSQL reservation/payment state
- update Redis live state
- publish payment result events
- record retry/DLQ-visible failures in a later milestone

## Local Infrastructure

Docker Compose should evolve toward:

```text
frontend
nginx
api-a
api-b
traffic-generator
worker
postgres
redis
kafka
```

Expected local browser entry:

```text
http://localhost:8080
```

Suggested internal routing:

```text
/              -> frontend
/api/**         -> api-a/api-b
/api/**/events  -> api-a/api-b with SSE buffering disabled
```

## Production Preparation

The first MVP should prepare, but not execute, production deployment.

Required artifacts:

- `application-prod.yml`
- production environment variable list
- backend Docker image execution notes
- frontend Vercel environment variable example
- AWS Lightsail/RDS deployment document
- local vs prod topology comparison

Expected low-cost production direction:

```text
Vercel frontend
  -> public API endpoint
  -> Lightsail nginx or managed LB
  -> api-a/api-b
  -> worker
  -> Redis/Kafka host or future managed alternatives
  -> RDS PostgreSQL
```

Actual AWS resource creation is out of scope for this milestone.

## Observability Panels

The frontend should make infrastructure behavior visible:

### Redis Panel

- waiting queue size
- admitted count
- currently active users
- selected user's queue state
- Redis TTL or temporary hold state where useful

### Kafka Flow Panel

- per-user payment event timeline
- `PaymentRequested`
- worker received/processed marker
- `PaymentResult`
- result applied to reservation

### PostgreSQL Panel

- reserved count
- failed count
- active holds
- seat conflict count
- final consistency summary, such as successful reservations never exceeding seat count

### Server Distribution Panel

- requests handled by `api-a`
- requests handled by `api-b`
- conflicts by server
- successful reservations by server

## Testing Strategy

Tests should prove the architecture claim:

- multiple simulations do not share Redis keys
- multiple simulations do not share seat inventory
- Kafka events include `simulationId`
- API responses include `handledBy`
- nginx config does not use sticky routing for simulation traffic
- traffic-generator targets nginx, not individual API containers
- PostgreSQL constraints prevent duplicate active reservations
- Redis snapshot can be read by either API instance

Important acceptance tests:

```text
Two simulations can run at the same time without mixing queue/user/seat state.
150 virtual users generate HTTP requests that are handled by both api-a and api-b.
100 seats cannot produce more than 100 successful reservations.
Payment events are visible in the Kafka flow panel.
Final reservation state is visible in the PostgreSQL panel.
```

## Scope Boundaries

In scope for this MVP:

- React/Vite frontend separation
- local Docker Compose topology update
- traffic-generator service
- Redis shared simulation state
- visible Redis/Kafka/PostgreSQL panels
- simulation-scoped database model
- production configuration preparation

Out of scope for this MVP:

- real payments
- real user accounts
- actual AWS deployment
- managed Kafka or managed Redis
- Kubernetes
- complex admin features
- full distributed tracing stack

## Success Criteria

The MVP is successful when a reviewer can:

1. Open the React dashboard.
2. Start a simulation with N virtual users.
3. See requests handled by both `api-a` and `api-b`.
4. See Redis queue and live state change.
5. See Kafka payment flow events for selected users.
6. See PostgreSQL final reservation state and consistency counts.
7. Run multiple simulations without mixed state.
8. Understand local and future production topology from documentation.

The demo should make it clear why Redis, Kafka, PostgreSQL, multiple API instances, nginx, and a worker exist.
