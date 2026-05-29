# Production v1 Deployment

Production v1 is a low-cost portfolio deployment. The frontend and backend are intentionally separated: Vercel serves the React app, and the Spring Boot backend only exposes API endpoints.

## Target Topology

- Vercel: React/Vite frontend
- Lightsail A: public nginx and `api-a`
- Lightsail B: `api-b`, `worker`, and `traffic-generator`
- Lightsail C: Redis and Kafka
- RDS PostgreSQL: durable seat and reservation state

Public traffic flows through:

```text
browser -> ticket.chocojipsa.blog -> https://ticket-api.chocojipsa.blog/api/** -> nginx -> api-a/api-b
```

AI traffic flows through the same public backend entry point:

```text
traffic-generator -> nginx -> api-a/api-b -> Redis/PostgreSQL/Kafka
```

## Backend Is API-Only

The backend must not serve the portfolio UI. The legacy Spring static files under `backend/src/main/resources/static` were removed. In production, `/` should not show a backend page. Use:

- `https://frontend.example.com`: Vercel frontend
- `https://ticket.chocojipsa.blog`: Vercel frontend
- `https://ticket-api.chocojipsa.blog/api/events/active`: backend API
- `https://ticket-api.chocojipsa.blog/health`: backend health check

## Deployment Order

1. Create RDS PostgreSQL.
2. Provision Lightsail C and run Redis and Kafka.
3. Provision Lightsail A and Lightsail B.
4. Deploy `api-a` on Lightsail A with `SPRING_PROFILES_ACTIVE=prod`.
5. Deploy `api-b` on Lightsail B with `SPRING_PROFILES_ACTIVE=prod`.
6. Deploy `worker` on Lightsail B with `SPRING_PROFILES_ACTIVE=prod,worker`.
7. Deploy `traffic-generator` on Lightsail B with `SPRING_PROFILES_ACTIVE=prod,generator`.
8. Configure nginx on Lightsail A to proxy `/api/**` to both API servers and `/health` to the API upstream.
9. Deploy the frontend to Vercel with `ticket.chocojipsa.blog` as the frontend domain.

## nginx Requirements

Production nginx should:

- expose port 80 or 443 publicly
- proxy `/api/**` to `api-a` and `api-b`
- proxy `/health` to the API upstream
- return 404 for `/`
- set CORS headers for the Vercel frontend
- disable buffering for streaming endpoints if SSE is used

The local `infra/nginx.conf` is the reference shape. In production, replace Docker service names with private Lightsail addresses:

```nginx
upstream api_servers {
  server 127.0.0.1:8080;
  server LIGHTSAIL_B_PRIVATE_IP:8080;
}
```

## Verification

After deployment:

```powershell
curl https://api.example.com/health
curl https://api.example.com/api/events/active
```

For this project:

```powershell
curl https://ticket-api.chocojipsa.blog/health
curl https://ticket-api.chocojipsa.blog/api/events/active
```

Then open the Vercel URL and confirm:

- event start begins a countdown
- queue position changes after reservation entry
- both `api-a` and `api-b` show request counts
- seats move through held/payment/reserved states
- Kafka worker completes payment confirmation

For exact commands using the current Lightsail IPs, see `docs/deployment/lightsail-step-by-step.md`.

## Known Limitations

This layout is cost-oriented. nginx, Redis, and Kafka are self-hosted and not highly available. Production v2 can replace them with Lightsail Load Balancer or ALB, ElastiCache, and managed Kafka or Redpanda Cloud.
