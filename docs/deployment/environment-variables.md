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

## Frontend

- `VITE_API_BASE_URL`: public API base URL
