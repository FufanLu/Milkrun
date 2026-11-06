# ADR 0004 — One design system, two native UIs

- Status: accepted
- Date: 2026-11-06

## Context

ADR 0001 keeps the UI native on each platform. Two native UIs drift unless something holds them
together: different words for the same state, different colours for "unsent", a button that is
filled on one platform and outlined on the other. For a driver who switches devices, or a fleet
that runs both, drift reads as unreliability.

## Decision

1. **One written system.** `design/DESIGN.md` (with tokens in its front matter, and
   `.impeccable/design.json` as the machine-readable sidecar) is the source for colour, type,
   spacing, targets, shapes and motion. `Theme.kt` (Compose) and `Theme.swift` (SwiftUI) each
   implement it by hand. The token set is small (15 roles × 3 schemes) and changes rarely, so a
   generator is not yet worth its cost.
2. **One set of words.** Every sentence a driver reads lives in `shared/presentation/Copy.kt` and
   is compiled into both apps. That includes the server's rulings ("Stop 04 was cancelled by
   dispatch…") and the difference between *Sending* and *waiting for signal*. `MilkrunTests` on
   iOS and `RouteScreenModelTest` in common code pin the wording.
3. **One screen state.** `RouteScreenModel` decides which stop is next, what recedes, and which
   actions are legal. The views only draw.
4. **Platform structure, shared look.** Android follows Material 3 (top app bar, bottom sheet,
   system Back, WorkManager). iOS follows the HIG: navigation stack, confirmation dialog,
   edge-swipe back, SF Symbols, Dynamic Type, BGTaskScheduler. The packing-room marks (stencil,
   tape, sticker, strike) are themed through each platform's open layer.

## How the system was chosen

The visual direction came out of the impeccable design process. The concept roll assigned a
direction, which was weighed against catalog challengers and raised with the strongest discipline
of each declined one. The build was then reviewed four times by a separate reviewer on emulator
captures (chapter 03). The contract and the review history are kept in `.impeccable/`.

## Consequences

- Changing a token means editing two theme files. The reviewer pass on each platform's captures
  is what catches a missed one.
- A copy change in `Copy.kt` reaches both apps in the same commit and cannot drift.
- Each platform's Roborazzi and XCTest checks protect its own rendering. Cross-platform parity is
  checked by eye on the captures, which is a known gap.
