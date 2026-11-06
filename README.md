**English** | [中文](README.zh.md)

# Milkrun

**An offline-first platform for last-mile delivery drivers.** Kotlin Multiplatform core shared by a
SwiftUI iOS app and a Jetpack Compose Android app, synced through a Kotlin (Ktor) service.

A *milk run* is a fixed delivery route with many stops. Drivers run it in basements, lifts and rural
roads where the network comes and goes; Milkrun is built so that the app never loses a delivery and
never records one twice, whatever the network does.

```mermaid
flowchart LR
    subgraph Phone
        UI_I["iOS · SwiftUI"] --> KIT["MilkrunKit (KMP)"]
        UI_A["Android · Compose"] --> CORE
        KIT --> CORE["shared/*<br/>model · protocol · database · sync"]
        CORE --> DB[("SQLite<br/>view + outbox")]
    end
    CORE -- "push mutations (seq)" --> API
    API -- "pull changes (cursor)" --> CORE
    subgraph Server
        API["server · Ktor"] --> PG[("PostgreSQL")]
        API --> S3[("S3 / MinIO<br/>POD photos")]
    end
```

## What makes it interesting

- **One sync engine for both apps.** Outbox written in the same SQLite transaction as the optimistic
  update; push deduplicated by per-client sequence; cursor-based pull; pending mutations rebased on
  server state ([ADR 0002](docs/adr/0002-sync-protocol.md)).
- **Server-arbitrated conflicts.** The stop state machine lives in `shared/model` and is compiled
  into the phone *and* the server, so both sides apply the same rules.
- **Deterministic simulation tests.** Seeded runs with a network that drops, duplicates and reorders
  messages; every run must converge with no lost or doubled delivery.
- **Industrial build.** Gradle convention plugins, version catalog, `explicitApi()`, warnings as
  errors, ktlint, CI on Linux (JVM + Android) and macOS (iOS simulator).

## Repository layout

| Path | What |
|---|---|
| `build-logic/` | Gradle convention plugins (`milkrun.kmp.library`, `milkrun.jvm.service`) |
| `shared/` | Kotlin Multiplatform modules (Android, iOS, JVM targets) |
| `server/` | Ktor sync service |
| `android/` | Compose app |
| `ios/` | SwiftUI app (XcodeGen) |
| `tools/` | Load and chaos simulator |
| `docs/illustrated/` | One parcel followed through the system, with real data |
| `docs/adr/` | Architecture decision records |

## Status

| Milestone | Scope | State |
|---|---|---|
| M0 | Skeleton, toolchain, CI, ADRs | done |
| M1 | Shared core: model, state machine, HLC, SQLDelight, sync engine, simulation tests | next |
| M2 | Server: auth, push/pull, Postgres, dispatch, route optimisation | |
| M3 | Android app | |
| M4 | iOS app | |
| M5 | Proof-of-delivery photo upload, location, geofencing | |
| M6 | Observability and security | |
| M7 | Load and chaos testing | |

## Build

Requirements: JDK 21 (Amazon Corretto), Android SDK (API 37), Xcode 26 for iOS.

```bash
./gradlew jvmTest                 # shared modules on the JVM (fastest)
./gradlew testAndroidHostTest     # shared modules on the Android host
./gradlew iosSimulatorArm64Test   # shared modules on the iOS simulator (macOS)
./gradlew spotlessCheck           # ktlint
```
