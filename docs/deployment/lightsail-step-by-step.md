# Lightsail Step-by-Step Deployment

This guide deploys the portfolio demo to three Lightsail instances and one Lightsail PostgreSQL database.

## Current Topology

```text
Lightsail A public:  3.38.135.98
Lightsail A private: 172.26.15.219

Lightsail B public:  43.203.235.85
Lightsail B private: 172.26.4.84

Lightsail C public:  13.209.82.221
Lightsail C private: 172.26.2.197

PostgreSQL endpoint: ls-9e218c14490bdd692cf48125f0eb329605ac9794.cbc6i8cyumv7.ap-northeast-2.rds.amazonaws.com
PostgreSQL port: 5432
PostgreSQL database: simulation
PostgreSQL username: dbmasteruser
```

Do not commit the database password. Put it only in each server's `.env` file.

## 1. Security Rules

In Lightsail networking:

- A: allow public TCP `80`.
- B: do not need public app ports for normal traffic. Keep SSH only if possible.
- C: do not expose Redis or Kafka publicly. Allow private access from A and B to TCP `6379` and `9092`.
- Database: allow access from Lightsail A and B.

## 2. Clone The Repository On Each Server

Run on A, B, and C:

```bash
git clone https://github.com/Chocojipsa/simulation.git
cd simulation
```

If the repository already exists:

```bash
cd simulation
git pull
```

## 3. Start Redis And Kafka On C

Run on Lightsail C:

```bash
cd ~/simulation/infra/prod
cp env.c.example .env
sed -i 's/LIGHTSAIL_C_PRIVATE_IP/172.26.2.197/g' .env
docker compose -f lightsail-c.compose.yml up -d --build
docker compose -f lightsail-c.compose.yml ps
```

## 4. Start api-b, worker, And traffic-generator On B

Run on Lightsail B:

```bash
cd ~/simulation/infra/prod
cp env.b.example .env
sed -i 's/YOUR_LIGHTSAIL_DB_ENDPOINT/ls-9e218c14490bdd692cf48125f0eb329605ac9794.cbc6i8cyumv7.ap-northeast-2.rds.amazonaws.com/g' .env
sed -i 's/LIGHTSAIL_A_PRIVATE_IP/172.26.15.219/g' .env
sed -i 's/LIGHTSAIL_B_PRIVATE_IP/172.26.4.84/g' .env
sed -i 's/LIGHTSAIL_C_PRIVATE_IP/172.26.2.197/g' .env
nano .env
```

Replace `replace-with-db-password` with the real PostgreSQL password, then run:

```bash
docker compose -f lightsail-b.compose.yml up -d --build
docker compose -f lightsail-b.compose.yml ps
```

## 5. Start api-a And nginx On A

Run on Lightsail A:

```bash
cd ~/simulation/infra/prod
cp env.a.example .env
sed -i 's/YOUR_LIGHTSAIL_DB_ENDPOINT/ls-9e218c14490bdd692cf48125f0eb329605ac9794.cbc6i8cyumv7.ap-northeast-2.rds.amazonaws.com/g' .env
sed -i 's/LIGHTSAIL_A_PRIVATE_IP/172.26.15.219/g' .env
sed -i 's/LIGHTSAIL_B_PRIVATE_IP/172.26.4.84/g' .env
sed -i 's/LIGHTSAIL_C_PRIVATE_IP/172.26.2.197/g' .env
sed -i 's/LIGHTSAIL_B_PRIVATE_IP/172.26.4.84/g' nginx-api.conf
nano .env
```

Replace `replace-with-db-password` with the real PostgreSQL password, then run:

```bash
docker compose -f lightsail-a.compose.yml up -d --build
docker compose -f lightsail-a.compose.yml ps
```

## 6. Verify Backend

Run from your local machine:

```powershell
curl http://3.38.135.98/health
curl http://3.38.135.98/api/events/active
```

If `/api/events/active` returns event JSON, the backend is reachable through nginx.

## 7. Vercel Frontend

The production frontend domain is:

```text
ticket.chocojipsa.blog
```

The production API domain is:

```text
ticket-api.chocojipsa.blog
```

DNS records:

```text
ticket.chocojipsa.blog      -> Vercel DNS target
ticket-api.chocojipsa.blog  -> A record 3.38.135.98
```

The first production deployment uses `frontend/vercel.json` rewrites:

```text
/api/** -> https://ticket-api.chocojipsa.blog/api/**
```

In Vercel:

- Root Directory: `frontend`
- Build Command: `npm run build`
- Output Directory: `dist`
- Environment Variables: leave `VITE_API_BASE_URL` unset

The browser calls the Vercel HTTPS origin, and Vercel proxies `/api/**` to the HTTPS API domain.
