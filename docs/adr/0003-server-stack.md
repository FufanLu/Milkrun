# ADR 0003 — Server: Ktor + PostgreSQL

- Status: accepted
- Date: 2026-11-06

## Decision

- **Ktor** (Netty engine), because it is Kotlin end to end and can share `:shared:protocol` with the
  apps without a codegen step.
- **PostgreSQL**: one transaction per mutation, `last_processed_seq` updated in the same
  transaction, and a global `BIGSERIAL`-style change version for pull cursors. Migrations with
  **Flyway**, access through **Exposed** (DSL, not DAO), pooled by **HikariCP**.
- **JWT** bearer auth (driver tokens scoped to one route).
- **Micrometer + Prometheus** for metrics, W3C `traceparent` accepted from the apps.
- Integration tests run against a real Postgres through **Testcontainers**.

AWS mapping for the write-up: Ktor service on ECS/Fargate, Aurora PostgreSQL, S3 for proof-of-delivery
photos (MinIO locally), CloudWatch / X-Ray for metrics and traces.
