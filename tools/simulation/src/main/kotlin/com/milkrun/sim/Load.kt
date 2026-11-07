package com.milkrun.sim

import com.milkrun.database.JvmDatabase
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Mutation
import com.milkrun.model.StopStatus
import com.milkrun.network.KtorSyncTransport
import com.milkrun.network.milkrunDefaults
import com.milkrun.protocol.DevSession
import com.milkrun.sync.SyncEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Load generator: N virtual drivers, each on its own demo route, working stops against a running
 * server over real HTTP with the real SyncEngine (one SQLite database per driver, as on a phone).
 * Every action is followed by a sync round (push + pull), the worst case for the server.
 *
 * ```
 * MILKRUN_DEV_MODE=true server on :18081, then
 * ./gradlew :tools:simulation:load --args="--url http://localhost:18081 --drivers 100 --seconds 60"
 * ```
 */
public fun main(args: Array<String>): Unit = runBlocking {
    val opts = args.toList().chunked(2).associate { it[0].removePrefix("--") to it.getOrElse(1) { "" } }
    val url = opts["url"] ?: "http://localhost:18081"
    val drivers = opts["drivers"]?.toInt() ?: 50
    val seconds = opts["seconds"]?.toInt() ?: 60
    val thinkMillis = opts["think-ms"]?.toLong() ?: 200

    val http = HttpClient(CIO) {
        milkrunDefaults(url)
        engine { maxConnectionsCount = drivers * 2 }
    }
    val latencies = ConcurrentLinkedQueue<Long>()
    val mutations = AtomicInteger()
    val failures = AtomicInteger()
    val clock = TimeSource.Monotonic
    val deadline = clock.markNow() + seconds.seconds

    val sessions = (1..drivers).map {
        async(Dispatchers.IO) {
            val res = http.post(DevSession.PATH)
            check(res.status.isSuccess()) { "dev session: ${res.status} ${res.bodyAsText()}" }
            res.body<DevSession>()
        }
    }.awaitAll()
    val start = clock.markNow()
    sessions.mapIndexed { i, s ->
        async(Dispatchers.IO) {
            val r = Random(i)
            val engine =
                SyncEngine(
                    JvmDatabase.create(),
                    KtorSyncTransport(http) {
                        s.driverToken
                    },
                    s.routeId,
                    System::currentTimeMillis,
                    r,
                )
            engine.syncOnce()
            while (deadline.hasNotPassedNow()) {
                val next =
                    engine.snapshot().filter { it.status == StopStatus.PLANNED || it.status == StopStatus.ARRIVED }
                        .minByOrNull { it.sequence } ?: break
                val m = if (next.status == StopStatus.PLANNED) {
                    Mutation.Arrive(next.id, System.currentTimeMillis())
                } else {
                    Mutation.Deliver(
                        next.id,
                        next.parcels.map {
                            it.id
                        },
                        HandoffMethod.FRONT_DOOR,
                        System.currentTimeMillis(),
                    )
                }
                engine.record(m)
                mutations.incrementAndGet()
                val t0 = clock.markNow()
                runCatching {
                    engine.syncOnce()
                }.onSuccess {
                    latencies += t0.elapsedNow().inWholeMicroseconds
                }.onFailure { failures.incrementAndGet() }
                delay(r.nextLong(thinkMillis / 2, thinkMillis * 3 / 2 + 1))
            }
        }
    }.awaitAll()
    val elapsed = start.elapsedNow()
    http.close()

    val sorted = latencies.sorted()
    fun p(q: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * q).toInt()] / 1000.0
    val load = runCatching {
        ProcessBuilder("sysctl", "-n", "vm.loadavg").start().inputStream.bufferedReader().readText().trim()
    }.getOrDefault("?")
    println("Milkrun load: $drivers drivers, ${elapsed.inWholeSeconds}s, think ${thinkMillis}ms, server $url")
    println(
        "  mutations          ${mutations.get()}  (${"%.1f".format(
            mutations.get() * 1000.0 / elapsed.inWholeMilliseconds.coerceAtLeast(1),
        )}/s)",
    )
    println("  sync rounds        ${sorted.size} ok, ${failures.get()} failed")
    println(
        "  sync latency (ms)  p50 ${"%.1f".format(
            p(0.5),
        )}  p95 ${"%.1f".format(p(0.95))}  p99 ${"%.1f".format(p(0.99))}  max ${"%.1f".format(p(1.0))}",
    )
    println("  host loadavg       $load")
}
