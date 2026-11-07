# 06 · One trace id from the phone to the log line, and a shift that leaves nothing behind

> The log lines and headers below come from the dev server (`MILKRUN_DEV_MODE=true`, local
> Postgres) on 2026-11-07. The Keychain status codes were printed by the iOS simulator app, and
> the end-of-shift rule is pinned by `RouteScreenModelTest.theShiftCanOnlyEndWhenEverythingIsDoneAndSent`.

## Why a driver's "it didn't send" needs a trace id

A driver reports that stop 7 showed *Unsent* for ten minutes. Without a shared id, support has a
phone log in one place, a load balancer log in another and the server log in a third, and only
timestamps to join them. Milkrun uses the W3C Trace Context header, so each request carries one
id the whole way:

```
phone (shared Kotlin)                   server (Ktor)                         log
─────────────────────                   ─────────────                         ───
Traceparent.random()
  00-4bf92f35…0e4736-00f067aa…02b7-01 ─▶ TraceContext plugin
                                          parse() ok → child(): same trace,
                                          new span 848b7953…8382
                                          call.attributes[TraceKey]
                                          CallLogging MDC trace_id ──────────▶ [4bf92f35…0e4736] 200 OK …
◀── traceresponse: 00-4bf92f35…0e4736-848b795344288382-01
```

| piece | where | what it does |
|---|---|---|
| `Traceparent` | `shared/protocol` | `version-traceId-spanId-flags`; `parse` rejects all-zero ids, bad hex and wrong lengths (`TraceparentTest`) |
| `KtorSyncTransport`, `KtorUploadTransport` | `shared/network`, `shared/proof` | every push, pull and upload part gets a fresh `traceparent` |
| `TraceContext` plugin | `server/app` | keeps the caller's trace id, opens a child span, or starts a trace when the header is missing or malformed |
| logback pattern | `server/app` | `[%X{trace_id:-}] [request id]` on every line, from the request's MDC |
| `traceresponse` | server response | the phone (and curl) learns the server's span without reading logs |

## Real lines

A pull sent with the W3C spec's example trace id:

```
$ curl -D - -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" \
       -H "Milkrun-Protocol: 1" -H "Authorization: Bearer …" \
       "localhost:18081/v1/routes/R-DEMO-MW6DOBOS-78A30B/pull?client=c-doc06&cursor=0"
HTTP/1.1 200 OK
X-Request-ID: df3cecdd-aba4-462b-b5bc-4d3260498ac5
traceresponse: 00-4bf92f3577b34da6a3ce929d0e0e4736-848b795344288382-01
```

```
04:26:51.959 INFO  [4bf92f3577b34da6a3ce929d0e0e4736] [df3cecdd-aba4-462b-b5bc-4d3260498ac5]
                   i.k.s.Application - 200 OK: GET - /v1/routes/R-DEMO-MW6DOBOS-78A30B/pull in 10ms
```

Rejected requests carry the trace too, and those are exactly the ones support is asked about.
These are the two failures from building this chapter (a missing protocol header, then a
missing `client`):

```
04:26:25.573 INFO  [4bf92f3577b34da6a3ce929d0e0e4736] [a84043d9-…] 426 Upgrade Required: GET - /v1/routes/…/pull in 292ms
04:26:39.294 INFO  [4bf92f3577b34da6a3ce929d0e0e4736] [33c4ce6c-…] 400 Bad Request: GET - /v1/routes/…/pull in 40ms
```

`grep 4bf92f35` across every replica's logs returns the whole conversation. In production, the
same MDC field is what an OpenTelemetry log exporter or CloudWatch Logs Insights query keys on
(chapter 07).

## The driver token lives in hardware-backed storage

The session (route id plus driver token, a JWT valid for the shift) is the one secret on the
phone. Both apps keep it out of plain preferences and out of backups:

```
                Android                                   iOS
 save   AndroidKeyStore AES-256-GCM key        SecItemAdd(kSecClassGenericPassword,
        "milkrun-session" (never leaves TEE)     service com.milkrun.driver,
        iv ‖ ciphertext → base64 → prefs          kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
 load   any failure → null → fresh session    errSecItemNotFound → null → fresh session
 wipe   remove pref (key stays, useless)      SecItemDelete
```

Both are implementations of the shared `SessionStore` interface, so `MilkrunSession` never
knows which platform it is running on. A store that fails to load gives the same result as a
first launch: the app asks for a new session instead of crashing.

### The bug the simulator found

Switching iOS to the Keychain made the shift UI test fail with "route never loaded". The app's
own log showed:

```
milkrun keychain load -34018      ← errSecMissingEntitlement
milkrun keychain save -34018
```

The Xcode project was built with `CODE_SIGNING_ALLOWED = NO`. An unsigned app has no
application identifier, so it has no Keychain access group, and every `SecItem` call fails.
The fix is ad-hoc signing (`CODE_SIGN_IDENTITY = "-"`), which needs no team account:

```
milkrun keychain load -25300      ← errSecItemNotFound: first launch, expected
milkrun keychain save 0
(relaunch)
milkrun keychain load 0           ← the session survived the restart
```

After that, `ShiftUITests.testOneShift` passed again (113 s).

## Ending a shift wipes the phone, but only when nothing would be lost

At the end of a shift the phone holds the route database (addresses, customer notes) and proof
photos. These belong to the next driver as little as they belong to anyone else, so *End shift*
deletes them. The risk is deleting the only copy of work the server has not received, so the
button exists only when that cannot happen:

```
canEndShift = finished                 every stop delivered, attempted or cancelled
           && sync is AllSent          outbox empty, nothing taped "Unsent"
           && notices.isEmpty()        no exception sticker the driver hasn't read
```

```
End shift ─▶ MilkrunSession.endShift(wipe) ── canEndShift? ── no ──▶ false (nothing touched)
                                                  │ yes
                                                  ▼
                              stop sync loop · store.clear() (token gone)
                              wipe(routeId):  Android  deleteDatabase("route-<id>.db"), filesDir/proof
                                              iOS      ApplicationSupport/databases/<id>, Documents/proof
                              ─▶ new session (next route)
```

`endShift` checks the rule itself instead of trusting the button, so a stale UI state cannot
cause a wipe. `RouteScreenModelTest.theShiftCanOnlyEndWhenEverythingIsDoneAndSent` pins the
rule. With a stop still open, `canEndShift` is false. After a delivery made offline, the route is
`finished` but `canEndShift` is still false, because the delivery is on tape. Once the phone is
back online and has synced, `canEndShift` is true.
