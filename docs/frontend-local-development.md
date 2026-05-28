# Frontend Local Development

## Purpose

The React frontend is the primary portfolio dashboard for the distributed seat reservation simulation.

## Start Backend Infrastructure

```powershell
cd infra
docker compose up -d --build
docker compose restart nginx
```

The API is available through nginx:

```text
http://localhost:8080
```

## Start Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

## Environment

Create `frontend/.env.local` when needed:

```text
VITE_API_BASE_URL=http://localhost:8080
```

## Demo Scenario

Use:

- 150 virtual users
- concurrency 50

Expected dashboard result:

- `api-a` and `api-b` both show request counts
- Redis queue reaches 0 when completed
- Kafka payment metrics move through payment/reserved states
- PostgreSQL reservation outcome is visible through seat counts
- users end as `예약 완료` or `실패`
