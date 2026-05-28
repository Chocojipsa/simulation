# Frontend Dashboard Separation Design

## Decision

Build a separated React frontend using the "operations dashboard" layout.

The frontend should make the project readable as a backend portfolio demo, not only as a seat map toy. The first screen must show the running distributed system: simulation controls, live seat map, waiting queue, server distribution, conflict counts, payment/Kafka progress, and selected virtual-user timeline.

## Goal

Create a Korean React dashboard that visualizes the distributed concert seat reservation simulation through the existing backend API.

## Non-Goals

- Do not redesign backend domain behavior in this phase.
- Do not add authentication.
- Do not add a real payment gateway.
- Do not replace nginx, Redis, Kafka, or PostgreSQL infrastructure in this phase.
- Do not build a marketing landing page as the first screen.

## Current Backend Contract

The frontend will use the existing API through nginx in local Docker:

```text
POST /api/simulations
POST /api/simulations/{simulationId}/run
GET  /api/simulations/{simulationId}
```

The snapshot returned by `GET /api/simulations/{simulationId}` is the main read model. It includes:

- `simulationId`
- `seats`
- `users`
- `metrics`
- `serverStats`
- `running`

The current backend already proves:

- generated users enter through nginx
- nginx distributes requests to `api-a` and `api-b`
- Redis stores queue/snapshot state
- PostgreSQL performs seat hold concurrency control
- Kafka carries payment request/result events
- worker applies payment results

## Frontend Architecture

Create a new `frontend/` app beside `backend/` and `infra/`.

```text
timedeal/
  backend/
  frontend/
  infra/
  docs/
```

Use:

- React
- Vite
- TypeScript
- CSS modules or plain CSS files
- `fetch` for API calls

The app should read API base URL from an environment variable:

```text
VITE_API_BASE_URL=http://localhost:8080
```

For local development:

- frontend dev server: `http://localhost:5173`
- backend/nginx API: `http://localhost:8080`

For the production deployment phase:

- Vercel serves frontend
- `VITE_API_BASE_URL` points to deployed nginx/API domain

## Screen Layout

Use a dense operational dashboard layout.

### Top Bar

Purpose: show project identity and current simulation state.

Content:

- title: `분산 좌석 예매 시뮬레이터`
- subtitle: `nginx · api-a/api-b · Redis · PostgreSQL · Kafka · worker`
- current simulation ID, shortened

### Left Control Column

Purpose: start repeatable test scenarios.

Controls:

- virtual user count presets: `30`, `150`, `300`
- concurrency presets: `10`, `50`, `100`
- start button: `시뮬레이션 시작`
- refresh/polling status

Scenario copy should be Korean and concrete:

- `가벼운 테스트`
- `충돌 확인`
- `고부하 데모`

### Center Seat Map

Purpose: make live reservation competition visible.

Rules:

- render a 10 x 12 seat grid
- include a small `STAGE` label
- show row labels `A` through `J`
- status colors:
  - available: green
  - payment in progress: yellow
  - reserved: gray
  - failed/conflict marker if represented separately: red
- selected user's current/last seat should be visually highlighted

The seat map should be the largest visual area on the page.

### Right Insight Column

Purpose: make backend architecture visible.

Sections:

- `서버 분산`
  - `api-a` request count
  - `api-b` request count
  - conflict count per server
  - success count per server
- `Redis 대기열`
  - queue size
  - admitted count
- `Kafka 결제`
  - payment in progress
  - reserved count
  - failed count
- `PostgreSQL 좌석 선점`
  - reserved/held/payment counts derived from snapshot

This column is what turns the UI into a backend portfolio demo.

### Bottom User Panel

Purpose: let the viewer inspect one virtual user's story.

Content:

- searchable or scrollable user list
- each row shows:
  - display name
  - status
  - selected seat label
  - seat attempt count
  - conflict count
- selected user detail:
  - timeline entries
  - handled server if present in future
  - final result

Default selection:

- choose the user with highest conflict count
- if none, choose first failed user
- otherwise choose first user

## Data Flow

1. User clicks `시뮬레이션 시작`.
2. Frontend calls `POST /api/simulations` with selected virtual user count.
3. Frontend calls `POST /api/simulations/{simulationId}/run` with count and concurrency.
4. Frontend polls `GET /api/simulations/{simulationId}` every 500ms while running.
5. Frontend slows polling to every 2s when all users are terminal.
6. User can click seats or users to inspect details.

Terminal user statuses:

- `RESERVED`
- `FAILED`

The UI should consider a simulation visually complete when every user is `RESERVED` or `FAILED`.

## Korean UX Copy

All user-facing text should be Korean.

Use direct, operational labels:

- `대기열`
- `좌석 선택`
- `이미 선택된 좌석`
- `결제 요청`
- `결제 성공`
- `결제 실패`
- `예약 완료`
- `실패`
- `처리 서버`
- `충돌 횟수`
- `좌석 선택 횟수`

Avoid tutorial text inside the app. The dashboard should be self-explanatory through labels, metrics, and visual state.

## Error Handling

The frontend should handle:

- API unavailable
- simulation creation failure
- run request failure
- snapshot polling failure

Behavior:

- show a compact error banner at the top of the dashboard
- keep the last successful snapshot visible
- allow retrying the start action
- do not clear the dashboard on transient polling failure

## Local Development Changes

The local stack should support both:

1. `docker compose up -d --build` for backend infrastructure
2. `npm run dev` in `frontend/` for React development

nginx may continue serving the old static backend page temporarily, but the portfolio dashboard source of truth should become `frontend/`.

Later, nginx can serve the built frontend or only proxy `/api/**`. For the first React split, Vite dev server can call nginx directly.

## Testing Strategy

Frontend tests should cover:

- API client URL construction
- simulation start flow
- snapshot polling terminal detection
- seat status color mapping
- default selected user logic
- Korean labels present on dashboard

Use lightweight tests first. Do not add heavy end-to-end testing until the React screen is stable.

Backend tests should not be changed unless API contract issues are discovered.

## Acceptance Criteria

- `frontend/` exists as a separate React TypeScript Vite app.
- Running `npm run dev` shows a Korean dashboard at `http://localhost:5173`.
- The dashboard can start a simulation through `http://localhost:8080`.
- The seat map updates from backend snapshots.
- `api-a` and `api-b` server stats are visible.
- queue, conflict, reserved, failed, and payment metrics are visible.
- clicking or auto-selecting a virtual user shows seat attempts and timeline.
- 150 users with concurrency 50 can be observed without manual API calls.
- The old backend static page is no longer the primary UI target.

## Risks

- Polling large snapshots every 500ms may become heavy for high user counts. The first version accepts this for simplicity, but the interval should slow down after terminal completion.
- The snapshot currently aggregates live read data in Redis. This is good for the dashboard, but future production polish may need SSE or WebSocket streaming.
- Some older static files and console output have shown encoding issues. The React app must save all source files as UTF-8 and render Korean copy correctly.
