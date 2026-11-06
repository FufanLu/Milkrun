# ADR 0003 — Server: Ktor + PostgreSQL

- Status: accepted
- Date: 2026-11-06

## Decision

- **Ktor** (Netty engine), because it is Kotlin end to end and can share `:shared:protocol` with the
  apps without a codegen step.
- **PostgreSQL**: one transaction per mutation, `last_processed_seq` updated in the same
  transaction, and a **per-route** change version for pull cursors (see the amendment below).
  Migrations with **Flyway**, plain **JDBC** pooled by **HikariCP**.
- **JWT** bearer auth (driver tokens scoped to one route).
- **Micrometer + Prometheus** for metrics, W3C `traceparent` accepted from the apps.
- Integration tests run against a real Postgres through **Testcontainers**.

AWS mapping for the write-up: Ktor service on ECS/Fargate, Aurora PostgreSQL, S3 for proof-of-delivery
photos (MinIO locally), CloudWatch / X-Ray for metrics and traces.

## Amendment (M2, 2026-11-06)

**Plain JDBC instead of Exposed.** `PostgresSyncStore` is about 200 lines with nine
statements. The locking (`SELECT … FOR UPDATE`), the upsert and the `jsonb` casts are the point of
the class, and a reviewer should see them verbatim. A DSL would hide exactly the parts that need
review. If the schema grows past the sync tables, revisit this.

**Per-route version, not a global sequence.** A global `BIGSERIAL` assigned at write time can
commit out of order. Transaction A takes version 41, transaction B takes 42, and B commits first.
A pull at that moment returns cursor 42, so the client never asks for anything at or below 42
again, and A's change is lost to it. Locking the route row and incrementing `route.version` under
that lock makes versions on a route gap-free and commit-ordered. The cost is that writes to one
route are serialised. That is acceptable because only the one to three devices on that route and
its dispatcher ever contend; different routes never block each other (measured in
`docs/illustrated/02-server-side.md`).

**Verification.** `SyncStoreContract` (in `server/core` test fixtures) runs against both the
in-memory store and PostgreSQL 17, including a concurrency test where 8 writers × 25 commands must
produce exactly versions 2…201. The chaos simulator also runs against PostgreSQL
(`PostgresSimulationTest`).
