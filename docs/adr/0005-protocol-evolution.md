# ADR 0005 — Evolving the sync protocol: server first, new mutations are breaking

- Status: accepted
- Date: 2026-11-06

## What happened

M5 added a mutation type, `attach_proof`. On the development machine the Android app was updated
before the server. Every push from that phone then failed with `400 Bad Request`. The old server's
JSON decoder rejects a `type` it does not know, and the whole batch is decoded before any
mutation is ruled on. Because pushes are strictly ordered by `seq`, a single undecodable mutation
at the head of the outbox blocks every later one: deliveries recorded after the photo could not
leave the phone either. Once the new server was deployed, the same outbox drained on the next
attempt (`lastProcessedSeq 15`, `accepted`).

The protocol guarantees what it promised (nothing lost, nothing applied twice), but a version
mismatch can stall the queue indefinitely. In the field that means a driver whose deliveries never
reach dispatch.

## Decision

1. **Server first.** Any change that adds a mutation type, adds a required field, or changes the
   meaning of an existing one ships in two steps: the server learns it, then the apps start
   emitting it. CI builds both from one repository, so this is a release-ordering rule rather than
   a code rule. It is written into the release checklist.
2. **Additive fields are free.** `SyncApi.json` has `ignoreUnknownKeys = true` on both sides, so an
   optional field added to a DTO needs no ordering.
3. **Protocol major for real breaks.** When the server cannot accept old apps (a removed mutation,
   a changed meaning), `SyncApi.PROTOCOL_VERSION` is bumped. Old apps get `426 Upgrade Required` and
   tell the driver to update, rather than retrying forever (chapter 02 §1).
4. **Next step, not yet built:** the server should answer an undecodable mutation with a ruling
   (`rejected`, reason `unsupported`) instead of failing the batch. The phone would then record it
   in the rejection list and keep draining. That turns a stuck queue into one visible sticker.

## Consequences

- A rollout always deploys the server first. A mobile release that emits a new mutation type
  depends on a server version, and that dependency is checked before the release goes out.
- Until (4) exists, a mismatch shows up as a growing "waiting for signal" count with
  `consecutiveFailures` climbing. The status line tells the driver that sync is retrying, but not
  why.
