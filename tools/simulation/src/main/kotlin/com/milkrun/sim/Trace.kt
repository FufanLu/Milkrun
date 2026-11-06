package com.milkrun.sim

import com.milkrun.database.JvmDatabase
import com.milkrun.model.ClientId
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Mutation
import com.milkrun.model.Parcel
import com.milkrun.model.ParcelId
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse
import com.milkrun.protocol.SyncApi
import com.milkrun.server.sync.InMemorySyncStore
import com.milkrun.server.sync.SyncService
import com.milkrun.sync.Backoff
import com.milkrun.sync.RecordResult
import com.milkrun.sync.SyncEngine
import com.milkrun.sync.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Produces the data quoted in docs/illustrated/01-offline-delivery.md: one stop delivered with no
 * signal, a push whose response is lost, and the retry that settles it. Deterministic (virtual
 * clock, fixed seed), except the record() latency benchmark at the end, which is real time.
 *
 * ```
 * ./gradlew :tools:simulation:trace     # writes tools/simulation/build/trace/
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun main(args: Array<String>) {
    val out =
        File(args.getOrElse(0) { "build/trace" }).apply {
            deleteRecursively()
            mkdirs()
        }
    val log = File(out, "trace.md").printWriter()

    fun say(s: String) {
        println(s)
        log.println(s)
    }
    val pretty = Json(SyncApi.json) { prettyPrint = true }

    fun <T> json(ser: KSerializer<T>, v: T) = pretty.encodeToString(ser, v)

    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun <T> await(block: suspend () -> T): T {
        val d = scope.async { block() }
        scheduler.advanceUntilIdle()
        return d.getCompleted()
    }

    fun clock() = SHIFT_START_MILLIS + scheduler.currentTime

    val route = RouteId("R-2026-11-06-DSE4-017")
    val stops =
        listOf(
            Stop(
                StopId("S-013"),
                route,
                13,
                "400 Fairview Ave N, Seattle, WA 98109",
                GeoPoint(47.6219, -122.3337),
                listOf(Parcel(ParcelId("TBA318840271001"), "small box")),
            ),
            Stop(
                StopId("S-014"),
                route,
                14,
                "410 Terry Ave N, Seattle, WA 98109",
                GeoPoint(47.6223, -122.3365),
                listOf(
                    Parcel(ParcelId("TBA318840275001"), "medium box"),
                    Parcel(ParcelId("TBA318840275002"), "padded mailer"),
                ),
            ),
        )
    val server = SyncService(InMemorySyncStore(), ::clock)
    await { server.createRoute(route, stops) }

    // The phone's network: can be switched off, and can lose the next response.
    val net =
        object : SyncTransport {
            var online = true
            var loseNextResponse = false
            val wire = mutableListOf<String>()

            override suspend fun push(route: RouteId, request: PushRequest): PushResponse {
                if (!online) throw IOException("no signal (UnknownHostException)")
                wire += "POST ${SyncApi.pushPath(route)}\n" + json(PushRequest.serializer(), request)
                val response = server.push(route, request)
                if (loseNextResponse) {
                    loseNextResponse = false
                    wire += "<response lost: lift doors closed>"
                    throw IOException("timeout waiting for response")
                }
                wire += "200 OK\n" + json(PushResponse.serializer(), response)
                return response
            }

            override suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse {
                if (!online) throw IOException("no signal")
                val response = server.pull(route, client, cursor)
                wire += "GET ${SyncApi.pullPath(route)}?client=${client.value}&cursor=$cursor\n200 OK\n" +
                    json(PullResponse.serializer(), response)
                return response
            }
        }

    val dbFile = File(out, "phone.db")
    val engine =
        SyncEngine(
            database = JvmDatabase.create(dbFile.path),
            transport = net,
            routeId = route,
            wallClock = ::clock,
            random = Random(17),
            io = dispatcher,
            newClientId = { "q7m2x9ka" },
        )

    fun snapshot(name: String) = dbFile.copyTo(File(out, "$name.db"), overwrite = true)

    fun flushWire(title: String) {
        say("\n### $title\n")
        net.wire.forEach { say("```\n$it\n```") }
        net.wire.clear()
    }

    say("# Trace: one offline delivery (client ${engine.clientId})")
    await { engine.syncOnce() }
    flushWire("1. Route loaded at the station (t=${scheduler.currentTime} ms)")
    snapshot("01-loaded")

    // 08:41 — basement garage under 410 Terry Ave N, no signal.
    net.online = false
    scheduler.advanceTimeBy(41 * 60_000L)
    val s14 = StopId("S-014")
    val p1 = ParcelId("TBA318840275001")
    val p2 = ParcelId("TBA318840275002")
    val actions =
        listOf(
            0L to Mutation.Arrive(s14, clock()),
            48_000L to Mutation.ScanParcel(s14, p1, clock() + 48_000),
            51_000L to Mutation.ScanParcel(s14, p2, clock() + 51_000),
            97_000L to Mutation.Deliver(s14, listOf(p1, p2), HandoffMethod.FRONT_DOOR, clock() + 97_000),
            112_000L to Mutation.SetNote(s14, "Left behind the planter, gate code 4512"),
        )
    val base = scheduler.currentTime
    say("\n### 2. Five taps in the garage, no signal\n")
    say("| t (virtual ms) | mutation | result |\n|---|---|---|")
    for ((offset, m) in actions) {
        scheduler.advanceTimeBy(base + offset - scheduler.currentTime)
        val r = await { engine.record(m) }
        val shown = if (r is RecordResult.Recorded) "Recorded(seq=${r.seq}, status=${r.stop.status})" else r.toString()
        say("| ${scheduler.currentTime} | ${m::class.simpleName} | $shown |")
    }
    snapshot("02-offline")

    val attempt = await { runCatching { engine.syncOnce() } }
    say("\n### 3. Sync attempt while offline\n\n`${attempt.exceptionOrNull()}`; status = `${engine.status.value}`")
    val b = Backoff(random = Random(17))
    say("\nFull-jitter backoff, first eight sleeps (seed 17): " + List(8) { b.next() }.joinToString())

    // 08:45 — back at street level; the lift door cuts the response off.
    scheduler.advanceTimeBy(4 * 60_000L)
    net.online = true
    net.loseNextResponse = true
    val lost = await { runCatching { engine.syncOnce() } }
    flushWire("4. Signal back, response lost (t=${scheduler.currentTime} ms): ${lost.exceptionOrNull()}")
    say(
        "\nServer lastProcessedSeq now = ${await {
            server.pull(
                route,
                engine.clientId,
                Long.MAX_VALUE,
            )
        }.lastProcessedSeq}",
    )
    snapshot("03-response-lost")

    scheduler.advanceTimeBy(1_300)
    await { engine.syncOnce() }
    flushWire("5. Retry: duplicate push, then pull and rebase (t=${scheduler.currentTime} ms)")
    say("\nstatus = `${engine.status.value}`")
    snapshot("04-synced")
    log.close()

    val bench = File(out, "bench.txt")
    bench.writeText("JVM ${System.getProperty("java.version")}, ${System.getProperty("os.arch")}\n")
    repeat(3) { run ->
        for (wal in listOf(false, true)) bench.appendText(benchmarkRecord(File(out, "bench-$run-$wal.db"), wal) + "\n")
    }
    print(bench.readText())
}

/** Real-time latency of [SyncEngine.record] on an on-disk SQLite file (one transaction per call). */
private fun benchmarkRecord(file: File, wal: Boolean): String = runBlocking {
    val route = RouteId("R-BENCH")
    val n = 400
    val stops =
        List(n) { i ->
            Stop(
                StopId("S-%04d".format(i)),
                route,
                i,
                "addr $i",
                GeoPoint(0.0, 0.0),
                listOf(Parcel(ParcelId("P$i"), "box")),
            )
        }
    val server = SyncService(InMemorySyncStore(), System::currentTimeMillis).apply { createRoute(route, stops) }
    val transport =
        object : SyncTransport {
            override suspend fun push(route: RouteId, request: PushRequest) = server.push(route, request)

            override suspend fun pull(route: RouteId, client: ClientId, cursor: Long) =
                server.pull(route, client, cursor)
        }
    val engine =
        SyncEngine(JvmDatabase.create(file.path, wal), transport, route, System::currentTimeMillis, io = Dispatchers.IO)
    engine.syncOnce()
    val samples = mutableListOf<Long>()
    repeat(3) { round ->
        for (s in stops) {
            val m =
                when (round) {
                    0 -> Mutation.Arrive(s.id, 1)
                    1 -> Mutation.ScanParcel(s.id, s.parcels[0].id, 2)
                    else -> Mutation.Deliver(s.id, listOf(s.parcels[0].id), HandoffMethod.FRONT_DOOR, 3)
                }
            val t0 = System.nanoTime()
            engine.record(m)
            samples += System.nanoTime() - t0
        }
    }
    val warm = samples.drop(200).sorted()

    fun pct(p: Double) = warm[((warm.size - 1) * p).toInt()] / 1_000.0
    val t0 = System.nanoTime()
    engine.syncOnce()
    val syncMs = (System.nanoTime() - t0) / 1e6
    (if (wal) "WAL      " else "rollback ") + "record() ${warm.size} samples after 200 warm-up: " +
        "p50=%.0f µs p90=%.0f µs p99=%.0f µs max=%.0f µs; ".format(pct(0.5), pct(0.9), pct(0.99), pct(1.0)) +
        "syncOnce() pushing ${samples.size} mutations + rebasing $n stops: %.0f ms".format(syncMs)
}
