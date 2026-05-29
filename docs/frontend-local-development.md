# Frontend Local Development

## Purpose

The React frontend is the primary portfolio dashboard for the distributed seat reservation simulation.

## Start Backend Infrastructure

```powershell
cd infra
docker compose up -d --build
docker compose restart nginx
```

The API is available through nginx. The backend is API-only; the old Spring-served static page is intentionally removed.

```text
http://localhost:8080
```

Useful endpoints:

```text
http://localhost:8080/health
http://localhost:8080/api/events/active
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

Local development uses the Vite `/api` proxy by default. The proxy forwards browser requests from:

```text
http://localhost:5173/api
```

to:

```text
http://localhost:8080/api
```

`frontend/.env.local` is usually unnecessary for local work. Set `VITE_API_BASE_URL` only when the frontend must call a deployed API directly, for example:

```text
VITE_API_BASE_URL=https://api.example.com
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
