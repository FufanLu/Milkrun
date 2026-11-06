# Product

<!-- impeccable:product-schema 1 -->

> Written without a live interview: the user asked for the work to proceed unattended
> ("不用管我 你自己干就行了", 2026-11-06). Facts marked *(inferred)* come from the original brief and
> the repository, not from the user's answers.

## Platform

adaptive

## Users

Last-mile delivery drivers working an assigned route for one shift (8–10 hours). They drive a van
between stops, walk parcels to doors, mailrooms and lockers, and use the app in between: often
one-handed, with a parcel or a scanner in the other hand, sometimes wearing gloves, in direct
sunlight, at night, and in basements and lifts with no signal *(inferred from the brief)*. A
second, occasional user is the dispatcher at the station, who plans routes and cancels or
re-sequences stops. Dispatchers have no app surface yet; they use the HTTP API.

## Product Purpose

The app lets a driver work their route whether or not the network is there. The driver sees the
stops in order and marks each one arrived, scanned, delivered or not deliverable. Every action
takes effect on the phone at once and reaches the server exactly once. Success means a driver
never loses a delivery, never records one twice, and is told plainly when the server ruled
against something they did.

## Positioning

The sync engine is shared by both apps (Kotlin Multiplatform). The server rules on every action
with the same compiled state machine the phone runs. Correctness under lost, duplicated and
delayed messages is proven by deterministic simulation (`docs/illustrated/01`). The UI's job is
to make that guarantee legible: what is done, what is still waiting for signal, and what the
server decided.

## Operating Context

- A shift is one route: 100–200 stops on a real route (the demo routes have 12–14). Each stop is
  an address with one or more parcels identified by barcode labels (`TBA…`).
- Per stop: arrive, scan each parcel, then deliver with a handoff method (in person, front door,
  mailroom, locker) or record a failed attempt with a reason (no access, nobody home, unsafe
  location, business closed). An optional free-text note (gate codes and the like) can be added.
- Connectivity comes and goes. The app shows how many actions are waiting to sync, and whether it
  is retrying.
- The server can reject an action. The most common reason is that another device on the same
  route already delivered the stop; another is that the dispatcher cancelled the stop. A delivery
  made after an unseen cancel stands, and dispatch is notified.

## Capabilities and Constraints

- Native apps on both platforms: SwiftUI on iOS (Swift 6.2, iOS 26 SDK) and Jetpack Compose on
  Android (minSdk 28, Material 3). UI is native per platform; logic is shared.
- Data available to the UI (from `SyncEngine`): ordered stops with status, parcels with scan and
  delivered flags, per-stop "unsynced" flag, route progress (total / delivered / attempted /
  unsynced), sync status (pending count, phase idle / syncing / backing off, last success,
  consecutive failures, last error), and unseen server rejections with their reason.
- Camera barcode scanning (CameraX + ML Kit on Android, VisionKit on iOS) is planned. Until it
  lands, a parcel is scanned by tapping it.
- Undecided: maps and navigation (no map SDK is committed), photo proof of delivery (M5), and
  localisation beyond English.

## Brand Commitments

- Name: **Milkrun**. No logo or brand assets exist yet.
- Portfolio project aimed at Amazon's Last Mile teams. It must not imitate Amazon's branding,
  trade dress or app UI.

## Evidence on Hand

- Simulation and trace results in `docs/illustrated/01-offline-delivery.md` and `02-server-side.md`.
- Demo route data: the Seattle addresses in `server/app/src/test/kotlin/com/milkrun/server/ServerTrace.kt`
  and `tools/simulation/src/main/kotlin/com/milkrun/sim/Trace.kt`. These are synthetic routes over
  real public addresses, not customer data.
- No users, testimonials, or field data exist. None may be fabricated.

## Product Principles

1. **The phone is the source of truth for what the driver did.** Every tap lands at once, offline
   or not. The UI never makes a driver wait on the network to finish a stop.
2. **Waiting is visible and calm.** Unsynced work is shown as a count and a per-stop mark, never as
   an error, until the server actually rules.
3. **Rulings are explained in the driver's terms.** Say what happened to which stop and what the
   driver should do next, not protocol codes.
4. **One hand, glanceable.** Next stop, its parcels and the primary action must work at arm's
   length, in a moving vehicle's aftermath, with a thumb.

## Accessibility & Inclusion

- Touch targets ≥ 56 dp/pt for primary actions (gloves) *(inferred from the brief)*. Platform
  minimums (48 dp / 44 pt) apply everywhere else.
- Must stay legible in direct sunlight: a high-contrast scheme in addition to light and dark
  *(inferred from the brief)*.
- Status must never be carried by color alone. Every state also has a label or symbol.
- Dynamic Type (iOS) and font scale (Android) up to at least 1.3× without clipping.
