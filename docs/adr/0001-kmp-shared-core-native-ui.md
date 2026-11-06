# ADR 0001 — Kotlin Multiplatform core, native UI on each platform

- Status: accepted
- Date: 2026-11-06

## Context

Milkrun ships a driver app on iOS and Android. Both apps must behave the same way offline: the same
stop state machine, the same outbox, the same conflict rules. If that logic is written twice it will
drift, and drift in a sync protocol shows up as lost or duplicated deliveries.

The UI needs the opposite: the iOS app should feel like an iOS app (SwiftUI navigation, VisionKit
scanner, background tasks), and the Android app should feel like an Android app (Compose, CameraX,
WorkManager). Drivers use these screens for 8–10 hours a shift.

## Options

| Option | Shared | Native feel | Swift on the resume |
|---|---|---|---|
| Two fully native apps | ~0% | full | yes |
| **KMP core + SwiftUI / Compose** | domain, storage, sync, networking, screen state (~60–70%) | full | yes |
| Compose Multiplatform for everything | ~90% | partial on iOS | thin |
| React Native / Flutter | ~90% | partial | no |

## Decision

Kotlin Multiplatform for everything below the UI (`shared/*`), exported to iOS as one umbrella
framework (`MilkrunKit`, built by `:shared:kit`), with SKIE turning `Flow` into `AsyncSequence` and
`suspend` into `async`. SwiftUI on iOS, Jetpack Compose on Android.

The server (`server/`) is also Kotlin and depends on the JVM target of `:shared:model` and
`:shared:protocol`, so the wire format and the state machine are one compiled artifact used by the
phone and the server.

## Consequences

- One implementation of the sync engine, tested once in `commonTest` and run on JVM, Android host and
  iOS simulator in CI.
- iOS engineers consume a binary framework; its public API is kept small (`explicitApi()`), and
  everything else is `internal`.
- Kotlin/Native builds are slow; CI caches `~/.konan` and runs iOS on a separate macOS job.
