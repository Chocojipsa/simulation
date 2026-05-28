# Production v1 Deployment

Production v1 is prepared but not provisioned in the first MVP.

## Target Topology

Vercel serves the React frontend. The API domain points to nginx. Nginx distributes `/api/**` traffic to `api-a` and `api-b`. Redis and Kafka may run on a low-cost instance for the first portfolio deployment. PostgreSQL should use RDS for durable reservation state.

## Suggested Low-Cost Layout

- Vercel: frontend
- Lightsail A: nginx and `api-a`
- Lightsail B: `api-b`, worker, and traffic-generator
- Lightsail C: Redis and Kafka
- RDS PostgreSQL: durable reservations

## Known Limitations

This layout is cost-oriented. Self-hosted nginx and self-hosted Redis/Kafka are not high availability. Production v2 can replace them with managed load balancing, ElastiCache, and managed Kafka or Redpanda Cloud.
