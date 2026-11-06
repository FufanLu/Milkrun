# Milkrun, illustrated

These chapters follow **one parcel** through the system, from the moment the route is loaded at the
station to the moment the dispatcher sees it delivered. Every request, SQL row, clock value and
latency number in them comes from running the code in this repository; each chapter says which
command produced it.

| # | Chapter | Status |
|---|---|---|
| 01 | [A delivery made offline: from tap to outbox row](01-offline-delivery.md) | done |
| 02 | [The same mutation on the server: push, dedup, state machine, pull](02-server-side.md) | done |
| 03 | Android: one stop screen, from Compose to SQLite | planned (M3) |
| 04 | iOS: the same stop through MilkrunKit and SwiftUI | planned (M4) |
| 05 | Proof of delivery: a photo uploaded in resumable parts | planned (M5) |
| 06 | One trace from the phone to Postgres | planned (M6) |
| 07 | 1,000 drivers on a bad network | planned (M7) |
