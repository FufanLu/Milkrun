@file:Suppress("ktlint:standard:max-line-length")

package com.milkrun.server

import com.milkrun.database.JvmDatabase
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Mutation
import com.milkrun.model.ParcelId
import com.milkrun.model.RouteId
import com.milkrun.model.StopId
import com.milkrun.network.KtorSyncTransport
import com.milkrun.network.milkrunDefaults
import com.milkrun.protocol.DispatchApi
import com.milkrun.protocol.PlanRouteRequest
import com.milkrun.protocol.PlanRouteResponse
import com.milkrun.protocol.PlannedParcel
import com.milkrun.protocol.PlannedStop
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.SyncApi
import com.milkrun.sync.SyncEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.DriverManager

/**
 * Produces the data quoted in docs/illustrated/02-server-side.md: a planned route, one driver's
 * push and pull over real HTTP (Netty + CIO) into a real PostgreSQL, the SQL Postgres executed, the
 * resulting rows, and latency under concurrency.
 *
 * ```
 * ./gradlew :server:app:trace -PpgAdminUrl=jdbc:postgresql://localhost:55432/postgres -PpgLog=/path/to/server.log
 * ```
 */
object ServerTrace {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val (adminUrl, pgLog, outDir) = args
        val out = File(outDir).apply { mkdirs() }
        val md = File(out, "trace.md").printWriter()
        fun say(s: String) {
            println(s)
            md.println(s)
        }

        // Fresh database with statement logging switched on for this database only.
        DriverManager.getConnection(adminUrl, "milkrun", "").use { c ->
            c.createStatement().use {
                it.execute("DROP DATABASE IF EXISTS milkrun_trace WITH (FORCE)")
                it.execute("CREATE DATABASE milkrun_trace")
                it.execute("ALTER DATABASE milkrun_trace SET log_statement = 'all'")
                it.execute("ALTER DATABASE milkrun_trace SET log_min_duration_statement = 0")
            }
        }
        val dbUrl = adminUrl.substringBeforeLast('/') + "/milkrun_trace"
        val ds = dataSource(dbUrl, "milkrun", "", poolSize = 32)
        migrate(ds)
        val jwt = JwtConfig("trace-secret-trace-secret-trace-secret")
        val server = embeddedServer(Netty, port = 18080) { milkrun(ServerDeps(PostgresSyncStore(ds), jwt)) }.start()

        val wire = mutableListOf<String>()
        var lastPush: PushRequest? = null
        val recorder = createClientPlugin("Recorder") {
            onRequest { req, body ->
                if (body is PushRequest) lastPush = body
                wire += "> ${req.method.value} ${req.url.buildString().removePrefix("http://localhost:18080")}\n" +
                    req.headers.entries().joinToString("\n") { (k, v) ->
                        "> $k: ${if (k == "Authorization") v.single().take(24) + "…" else v.joinToString()}"
                    }
            }
            onResponse { resp ->
                wire += "< HTTP/1.1 ${resp.status}\n" + resp.headers.entries()
                    .filter { it.key.lowercase() in setOf("x-request-id", "content-type", "content-length") }
                    .joinToString("\n") { (k, v) -> "< $k: ${v.joinToString()}" }
            }
        }
        val http = HttpClient(CIO) {
            milkrunDefaults("http://localhost:18080")
            install(recorder)
        }

        // ---- 1. dispatcher plans a 12-stop route out of a SoDo station
        val route = RouteId("R-2026-11-06-DSE4-017")
        val addresses = listOf(
            "410 Terry Ave N" to GeoPoint(47.6223, -122.3365),
            "2021 7th Ave" to GeoPoint(47.6153, -122.3383),
            "1st Ave S & S Royal Brougham Way" to GeoPoint(47.5917, -122.3343),
            "300 Pine St" to GeoPoint(47.6114, -122.3392),
            "1201 3rd Ave" to GeoPoint(47.6077, -122.3360),
            "400 Broad St" to GeoPoint(47.6205, -122.3493),
            "1000 4th Ave" to GeoPoint(47.6067, -122.3325),
            "500 Fairview Ave N" to GeoPoint(47.6236, -122.3335),
            "85 Pike St" to GeoPoint(47.6094, -122.3417),
            "701 5th Ave" to GeoPoint(47.6045, -122.3305),
            "1600 E Olive Way" to GeoPoint(47.6190, -122.3245),
            "999 3rd Ave" to GeoPoint(47.6050, -122.3349),
        )
        val stops = addresses.mapIndexed { i, (addr, loc) ->
            PlannedStop(
                StopId("S-%03d".format(i + 1)),
                addr,
                loc,
                listOf(PlannedParcel(ParcelId("TBA3188402%05d".format(i + 1)), "box")),
            )
        }
        val dispatcherToken = jwt.issue(Caller.Dispatcher("disp-ops-1"))
        val plan: PlanRouteResponse = http.post(DispatchApi.ROUTES_PATH) {
            bearerAuth(dispatcherToken)
            contentType(ContentType.Application.Json)
            setBody(PlanRouteRequest(route, GeoPoint(47.5800, -122.3300), stops))
        }.body()
        say(
            "### 1. Plan\n\n```\n${Json {
                prettyPrint = true
            }.encodeToString(PlanRouteResponse.serializer(), plan)}\n```",
        )
        val order = plan.order.mapIndexed { i, id -> "${i + 1}. $id ${stops.first { it.id == id }.address}" }
        say("\n" + order.joinToString("\n"))
        wire.clear()

        // ---- 2. a driver's device: load, deliver the first stop, sync — with statement logging
        val driverToken = jwt.issue(Caller.Driver("drv-4471", route))
        val engine =
            SyncEngine(
                JvmDatabase.create(),
                KtorSyncTransport(http) { driverToken },
                route,
                System::currentTimeMillis,
                newClientId = { "q7m2x9ka" },
            )
        engine.syncOnce()
        wire.clear()
        val first = plan.order.first()
        val parcel = stops.first { it.id == first }.parcels.single().id
        engine.record(Mutation.Arrive(first, System.currentTimeMillis()))
        engine.record(Mutation.Deliver(first, listOf(parcel), HandoffMethod.FRONT_DOOR, System.currentTimeMillis()))
        val logFile = File(pgLog)
        val before = logFile.length()
        engine.syncOnce()
        val sqlLines = logFile.readText().substring(before.toInt()).lines()
        File(out, "push-pull.sql.log").writeText(sqlLines.joinToString("\n"))
        say("\n### 2. HTTP\n")
        wire.forEach { say("```\n$it\n```") }
        wire.clear()

        // ---- 3. duplicate push: replay the exact same batch
        // (Done with a raw client so the engine's outbox state is irrelevant.)
        val dupBefore = logFile.length()
        val replay = http.post(SyncApi.pushPath(route)) {
            bearerAuth(driverToken)
            header(SyncApi.PROTOCOL_HEADER, 1)
            contentType(ContentType.Application.Json)
            setBody(checkNotNull(lastPush))
        }
        File(out, "duplicate.sql.log").writeText(logFile.readText().substring(dupBefore.toInt()))
        say("\nreplay of seq ${lastPush?.mutations?.map { it.seq }}: ${replay.status} ${replay.bodyAsText()}")
        wire.clear()

        val metrics = http.get("/metrics").bodyAsText().lines().filter { it.startsWith("milkrun_") }
        say("\n### 3. Metrics\n\n```\n${metrics.joinToString("\n")}\n```")

        // ---- 4. latency: N drivers on N routes pushing+pulling concurrently, over HTTP, into Postgres
        for ((round, concurrency) in listOf(8, 8, 8, 1, 8, 32).withIndex()) {
            val warmUp = round < 3
            val timersBefore = serverTimers(http)
            val samples = java.util.Collections.synchronizedList(mutableListOf<Long>())
            coroutineScope {
                (1..concurrency).map { k ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        val r = RouteId("R-LAT-$round-$k")
                        http.post(DispatchApi.ROUTES_PATH) {
                            bearerAuth(dispatcherToken)
                            contentType(ContentType.Application.Json)
                            setBody(
                                PlanRouteRequest(
                                    r,
                                    GeoPoint(47.58, -122.33),
                                    stops.map {
                                        it.copy(
                                            id = StopId("$r-${it.id}"),
                                            parcels = it.parcels.map { p ->
                                                p.copy(id = ParcelId("$r-${p.id}"))
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                        val tok = jwt.issue(Caller.Driver("drv-$k", r))
                        val e =
                            SyncEngine(
                                JvmDatabase.create(),
                                KtorSyncTransport(http) {
                                    tok
                                },
                                r,
                                System::currentTimeMillis,
                            )
                        e.syncOnce()
                        for (s in e.snapshot()) {
                            e.record(Mutation.Arrive(s.id, 1))
                            val t0 = System.nanoTime()
                            e.syncOnce() // one push of 1 mutation + one pull
                            samples += System.nanoTime() - t0
                        }
                    }
                }.awaitAll()
            }
            val timersAfter = serverTimers(http)
            val serverMeans = timersAfter.filterKeys { it.endsWith("/push") || it.endsWith("/pull") }.map { (k, v) ->
                val (c0, s0) = timersBefore[k] ?: (0.0 to 0.0)
                "${k.substringAfterLast('/')} %.2f ms".format((v.second - s0) / (v.first - c0) * 1000)
            }
            val sorted = samples.sorted()
            fun p(q: Double) = sorted[((sorted.size - 1) * q).toInt()] / 1e6
            if (!warmUp) {
                say(
                    "\nconcurrency=$concurrency syncOnce (push 1 + pull) n=${sorted.size}: p50=%.1f ms p90=%.1f ms p99=%.1f ms; server-side mean $serverMeans".format(
                        p(0.5),
                        p(0.9),
                        p(0.99),
                    ),
                )
            }
        }
        val serverSide = http.get("/metrics").bodyAsText().lines()
            .filter {
                it.startsWith("ktor_http_server_requests_seconds_") && "/v1/routes/{routeId}" in it &&
                    "status=\"200\"" in it
            }
        File(out, "server-timers.txt").writeText(serverSide.joinToString("\n"))
        say("\n```\n${serverSide.filter { "_count" in it || "_sum" in it || "_max" in it }.joinToString("\n")}\n```")
        say(
            "\nload average: " +
                ProcessBuilder("sysctl", "-n", "vm.loadavg").start().inputStream.bufferedReader().readText().trim(),
        )

        md.close()
        http.close()
        server.stop(500, 1000)
        ds.close()
    }

    /** (count, sum seconds) of successful server-side request timers, per route template. */
    private suspend fun serverTimers(http: HttpClient): Map<String, Pair<Double, Double>> {
        val lines = http.get("/metrics").bodyAsText().lines().filter {
            it.startsWith("ktor_http_server_requests_seconds_") &&
                "status=\"200\"" in it
        }
        fun value(l: String) = l.substringAfterLast(' ').toDouble()
        fun route(l: String) = Regex("route=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: "?"
        val counts = lines.filter { it.startsWith("ktor_http_server_requests_seconds_count") }.associate {
            route(it) to
                value(it)
        }
        val sums = lines.filter { it.startsWith("ktor_http_server_requests_seconds_sum") }.associate {
            route(it) to
                value(it)
        }
        return counts.mapValues { (k, c) -> c to (sums[k] ?: 0.0) }
    }
}
