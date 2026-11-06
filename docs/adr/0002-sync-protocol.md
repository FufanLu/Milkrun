# ADR 0002 — Sync protocol: local outbox, push with per-client sequence, cursor pull, rebase

- Status: accepted
- Date: 2026-11-06

## Context

A driver marks a stop delivered in a basement with no signal. The app must:

1. show the stop as delivered immediately,
2. keep the fact across app kills and reboots,
3. deliver it to the server exactly once in effect, even if the request is retried, duplicated or
   reordered by the network,
4. accept that the server may have changed the same route in the meantime (dispatcher re-sequenced
   stops, cancelled a parcel, reassigned a stop to another driver),
5. end with phone and server holding the same state once the network settles.

## Decision

The model follows Replicache's push/pull design, adapted to a server-authoritative domain:

- **Mutation log.** Every user action is a mutation `(clientId, seq, hlc, name, args)`. It is written
  to the local `outbox` table in the same SQLite transaction that applies it optimistically to the
  local view. `seq` is a dense per-client counter.
- **Push.** Batches of pending mutations go to `POST /v1/sync/push`. The server applies each one in
  `seq` order inside one database transaction per mutation and stores `last_processed_seq` per
  client. A mutation with `seq <= last_processed_seq` is skipped, which makes retries and duplicates
  harmless. A mutation that breaks a domain rule is *processed and rejected* (it advances
  `last_processed_seq` and is recorded with a reason) rather than retried forever.
- **Pull.** `GET /v1/sync/pull?cursor=n` returns every row change with server version `> n`, the new
  cursor, and the client's `last_processed_seq`.
- **Rebase.** The client replaces its confirmed state with the server's, deletes outbox entries with
  `seq <= last_processed_seq`, and re-applies the remaining pending mutations on top. The UI reads the
  rebased view, so a pending action stays visible until the server has ruled on it.
- **Conflicts.** Stop status is a state machine owned by the server (`shared/model`). Physical facts
  win over plans: a stop delivered offline stays delivered even if a dispatcher cancelled it while
  the driver was offline, and the server records the conflict for the dispatcher. Free-text fields
  (driver notes) use last-writer-wins on a hybrid logical clock.
- **Retries.** Exponential backoff with full jitter, capped, reset on success (AWS Builders' Library,
  "Timeouts, retries and backoff with jitter").

## Verification

`shared/sync` contains a deterministic simulation: a seeded scheduler drives N clients, an
in-memory server and a network that drops, duplicates, delays and reorders messages. For every
seed it checks: phones and server converge, no acknowledged mutation is lost, and no mutation takes
effect twice.

## Rejected alternatives

- **CRDT everywhere.** A delivery is not a mergeable value; two drivers delivering the same parcel is
  a business conflict that needs a ruling.
- **Last-writer-wins rows.** Silently drops one side of every conflict.
- **Request/response without a log.** Loses actions when the app is killed between tap and response.
