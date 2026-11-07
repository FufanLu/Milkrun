# 07 · Load: hundreds of drivers against one server, and how it would run on AWS

> Measured on 2026-11-07 with `./gradlew :tools:simulation:load` against one dev server process
> (fat JAR, local Postgres 16) on a 12-core, 18 GB laptop. **The host was shared**: another
> project's `rustc` build was running at the same time, and loadavg was 48–72, four to six times
> the core count. Read the absolute numbers as an upper bound on latency, not as a capacity
> figure. The useful results are the zero failures, the ratio between client and server times,
> and the bug this run found.

## What one virtual driver does

```
POST /v1/dev/session                    own route (12 stops, 15 parcels), own driver token
SyncEngine(JvmDatabase.create(), …)     own SQLite file, the same engine the phones run
loop until the route is done or time is up:
    next stop → Arrive, or Deliver all parcels (FRONT_DOOR)
    engine.record(m)                     outbox write in the view transaction (ch. 01)
    engine.syncOnce()   ◀── timed        push (seq-deduped) + cursor pull (ch. 02)
    think 100–300 ms
```

A sync after every action is the worst case. Real phones batch: the outbox drains on a timer
and on reconnect, so one push usually carries several mutations.

## Results

| drivers | wall | mutations | rate | sync ok / failed | p50 | p95 | p99 | max | host loadavg (1m) |
|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|
| 20  | 11 s | 480   | 41.5/s  | 480 / 0   | 53.7 ms  | 257 ms  | 393 ms  | 526 ms  | 72 |
| 50  | 19 s | 1,200 | (≈63/s) | 1,200 / 0 | 468 ms   | 1,086 ms | 1,587 ms | 2,009 ms | 43 |
| 100 | 14 s | 2,400 | 160/s   | 2,400 / 0 | 328 ms   | 564 ms  | 673 ms  | 889 ms  | 50 |
| 200 | 29 s | 4,360 | (≈150/s) | 4,360 / 0 | 994 ms  | 1,554 ms | 1,773 ms | 2,415 ms | 48 |

The 50- and 200-driver runs were made before a fix to the rate column (it divided two integers
and printed 0.0), so their rate is computed from mutations ÷ wall time. A run that ends before
`--seconds` has finished every route: 24 mutations × N drivers.

**0 failed sync rounds out of 8,440.** Every push was accepted, deduplicated or ruled on.
Nothing timed out, and nothing went into the retry and backoff path.

### Where the time goes

The server logs its own handling time on each request line, and the same trace id from chapter 06
links it to the client:

```
04:27:08.233 INFO [c316fbbe…52d5fc] 200 OK: POST - /v1/routes/R-DEMO-MW6DOCVS-D1A032/push in 4ms
```

Over every push and pull logged during these runs:

| endpoint | requests | p50 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|
| push | 8,440 | 123 ms | 390 ms | 533 ms | 934 ms |
| pull | 8,813 | 69 ms  | 363 ms | 562 ms | 934 ms |

With little load a push takes 3–4 ms on the server, and the gap up to the client's p50 is
scheduling on an overloaded host. At 200 drivers, the client JVM (200 SQLite files, 200
coroutine loops, CIO) and the server compete with the Rust build for 12 cores. The server never
answers with an error. It only answers later, and this is the behaviour wanted under load:
back-pressure shows up as latency, and the phone keeps the work on its tape until the response
arrives.

## The bug load found

The first run failed before any driver started:

```
IllegalStateException: dev session: 400 Bad Request {"code":"bad_request","message":"route R-DEMO-MW6BSX63 exists"}
```

Dev route ids were `R-DEMO-` + `currentTimeMillis` in base 36, so 50 sessions requested in the
same millisecond got the same id. The route insert's primary key refused the duplicates. That
was correct, but the dev endpoint did not expect it. Ids now carry a random suffix
(`R-DEMO-MW6DOBOS-78A30B`). The production path is not affected, because dispatch assigns route
ids. The fix still matters: an id taken from a clock is a collision waiting for concurrency, and
only a concurrent test shows it.

## Deploying: `deploy/k8s/milkrun-server.yaml`

```
                    ┌──────────── Service :80 → :8080 ────────────┐
   phones ── ALB ──▶│  pod   pod   pod   (3 … 20, HPA at 65 % CPU) │──▶ Postgres (RDS / Aurora)
                    └─────────────────────────────────────────────┘     one writer; route row
                         readyz: DB reachable                            locked FOR UPDATE per push
                         healthz: process alive                    ──▶ blobs (S3 in prod)
```

| setting | value | why |
|---|---|---|
| replicas / HPA | 3 → 20, CPU 65 % | the morning load-out, when every van pulls its route within ~30 minutes, is the peak |
| rollout | `maxUnavailable: 0` | a phone mid-push always finds a ready pod; pushes are idempotent, so a retry on another pod is safe |
| PDB | `minAvailable: 2` | node drains never take the API below two pods |
| probes | `/readyz` (DB) vs `/healthz` (process) | a database blip takes pods out of the Service without restarting them |
| resources | 500m / 512 Mi request, 768 Mi limit | no CPU limit, which avoids CFS throttling on latency-sensitive pushes |
| pod security | non-root, read-only root FS, `/data` volume | matches the Dockerfile (`BLOB_DIR=/data/blobs`), which fixed the CI image failure |
| secrets | `DATABASE_*`, `JWT_SECRET` from a Secret | nothing secret in the image or the manifest |

The servers hold no state between requests. All ordering lives in Postgres (per-client `seq`,
per-route version), so any pod can serve any phone. ADR 0005 requires the server to deploy
first: a new server must still accept the previous app's protocol major.

## The same design on AWS

| Milkrun piece | AWS service | note |
|---|---|---|
| server pods | EKS (or ECS Fargate) behind an ALB | the manifest above, as is |
| Postgres | Aurora PostgreSQL | per-route `FOR UPDATE` keeps write contention per route, not global |
| `FileBlobStore` | S3 multipart upload | the upload protocol in ch. 05 was modelled on it: client-chosen id, parts, `complete` with checksum |
| JWT secret | Secrets Manager → Secret via External Secrets | rotated without an image rebuild |
| logs with `trace_id` | CloudWatch Logs / OpenSearch | `fields @message | filter @message like "4bf92f35"` |
| traces | ADOT collector → X-Ray | the W3C `traceparent` from the phone is the trace root |
| background sync on phones | (device side) WorkManager / BGTaskScheduler | unchanged |

## Reproduce

```
MILKRUN_DEV_MODE=true java -jar server/app/build/libs/milkrun-server.jar      # :18081
./gradlew :tools:simulation:load --args="--url http://localhost:18081 --drivers 100 --seconds 60"
```
