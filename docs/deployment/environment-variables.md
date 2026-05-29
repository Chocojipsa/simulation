# Environment Variables

## Backend

- `APP_INSTANCE_ID`: visible server id such as `api-a`, `api-b`, `worker`, or `traffic-generator`
- `SPRING_PROFILES_ACTIVE`: `prod`, `prod,worker`, or `prod,generator`
- `SPRING_DATASOURCE_URL`: RDS PostgreSQL JDBC URL
- `SPRING_DATASOURCE_USERNAME`: RDS username
- `SPRING_DATASOURCE_PASSWORD`: RDS password
- `SPRING_DATA_REDIS_HOST`: Redis host
- `SPRING_DATA_REDIS_PORT`: Redis port, default `6379`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka bootstrap servers
- `TRAFFIC_GENERATOR_CONTROL_BASE_URL`: traffic-generator control endpoint used by API servers
- `TRAFFIC_GENERATOR_TARGET_BASE_URL`: nginx URL used by the generator to send virtual-user traffic
- `TRAFFIC_GENERATOR_DEFAULT_CONCURRENCY`: default generator concurrency
- `LIVE_EVENT_ID`: shared event UUID, default `00000000-0000-0000-0000-000000000001`
- `LIVE_EVENT_TITLE`: Korean event title shown in the frontend
- `LIVE_EVENT_SEAT_COUNT`: total seats, default `120`
- `LIVE_EVENT_COUNTDOWN_SECONDS`: countdown after a visitor starts the event, default `60`
- `LIVE_EVENT_OPEN_WINDOW_SECONDS`: ticketing open window, default `300`
- `LIVE_EVENT_AI_PARTICIPANT_COUNT`: AI participants started after open, default `150`
- `LIVE_EVENT_AI_CONCURRENCY`: AI runner concurrency, default `50`
- `PAYMENT_FAILURE_RATE_PERCENT`: simulated payment failure rate, default `0`
- `PAYMENT_SIMULATION_DELAY_MIN_MS`: worker payment delay lower bound, default `300`
- `PAYMENT_SIMULATION_DELAY_MAX_MS`: worker payment delay upper bound, default `900`
- `PAYMENT_KAFKA_PARTITIONS`: payment topic partition count, default `5`

## Frontend

- `VITE_API_BASE_URL`: public API base URL, for example `https://ticket-api.chocojipsa.blog`. Leave unset when using the Vercel `/api/**` rewrite in `frontend/vercel.json`.

## Suggested Production Values

### api-a

```text
APP_INSTANCE_ID=api-a
SPRING_PROFILES_ACTIVE=prod
TRAFFIC_GENERATOR_CONTROL_BASE_URL=http://LIGHTSAIL_B_PRIVATE_IP:8080
```

### api-b

```text
APP_INSTANCE_ID=api-b
SPRING_PROFILES_ACTIVE=prod
TRAFFIC_GENERATOR_CONTROL_BASE_URL=http://127.0.0.1:8080
```

### worker

```text
APP_INSTANCE_ID=worker
SPRING_PROFILES_ACTIVE=prod,worker
```

### traffic-generator

```text
APP_INSTANCE_ID=traffic-generator
SPRING_PROFILES_ACTIVE=prod,generator
TRAFFIC_GENERATOR_TARGET_BASE_URL=https://api.example.com
```
