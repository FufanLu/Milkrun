package com.milkrun.sim

import com.milkrun.database.JvmDatabase
import com.milkrun.model.AttemptReason
import com.milkrun.model.ClientId
import com.milkrun.model.DispatcherCommand
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Mutation
import com.milkrun.model.Parcel
import com.milkrun.model.ParcelId
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse
import com.milkrun.server.sync.InMemorySyncStore
import com.milkrun.server.sync.SyncException
import com.milkrun.server.sync.SyncObserver
import com.milkrun.server.sync.SyncService
import com.milkrun.server.sync.SyncStore
import com.milkrun.server.sync.SyncTransaction
import com.milkrun.sync.RecordResult
import com.milkrun.sync.SyncEngine
import com.milkrun.sync.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.IOException
import kotlin.random.Random

private val OPEN = setOf(StopStatus.PLANNED, StopStatus.ARRIVED, StopStatus.ATTEMPTED)

/** 2026-11-06 08:00 PST, the start of a shift. Virtual time 0 maps here. */
public const val SHIFT_START_MILLIS: Long = 1_793_977_200_000

public data class NetworkProfile(
    val minLatencyMillis: Long = 20,
    val maxLatencyMillis: Long = 800,
    /** Request never reaches the server. */
    val dropRequest: Double = 0.10,
    /** Server processed the request, the phone never hears back. The dangerous one. */
    val dropResponse: Double = 0.10,
    /** A stale copy of a push reaches the server again, up to five minutes later. */
    val duplicate: Double = 0.08,
    /** Dead zones (garages, lifts) per client per shift. */
    val deadZones: Int = 4,
    val maxDeadZoneMillis: Long = 10 * 60_000,
)

public data class SimConfig(
    val seed: Long,
    val stops: Int = 14,
    val clients: Int = 2,
    /** Short enough that drivers are still mid-route, with mutations in flight, when it ends. */
    val shiftMillis: Long = 45 * 60_000,
    val dispatcherActions: Int = 6,
    val network: NetworkProfile = NetworkProfile(),
    val maxHealRounds: Int = 8,
    /** App process killed by the OS and relaunched, per device per shift. */
    val restartsPerDevice: Int = 2,
    /** Deliberate protocol bugs, to prove the invariant checks catch them. Empty in real runs. */
    val faults: Set<Fault> = emptySet(),
)

public enum class Fault {
    /** Server ignores lastProcessedSeq, so a retried push is applied again. */
    SERVER_FORGETS_SEQ,

    /**
     * Server loses writes it already acknowledged: a third of the way through the shift a
     * checkpoint is taken, and two thirds through the server is restored to it (an asynchronous
     * replica promoted after failover, or a restore from backup). The protocol assumes durable
     * acknowledgements, so this must show up as lost writes.
     */
    SERVER_ROLLBACK,
}

public data class SimStats(
    var recorded: Int = 0,
    var refusedLocally: Int = 0,
    var pushes: Int = 0,
    var pulls: Int = 0,
    var droppedRequests: Int = 0,
    var droppedResponses: Int = 0,
    var offlineFailures: Int = 0,
    var staleDuplicates: Int = 0,
    var serverDuplicates: Int = 0,
    var accepted: Int = 0,
    var rejected: Int = 0,
    var deliveredAfterCancel: Int = 0,
    var dispatcherCancels: Int = 0,
    var healRounds: Int = 0,
    var restarts: Int = 0,
    val rejectedBy: MutableMap<String, Int> = sortedMapOf(),
) {
    public operator fun plusAssign(o: SimStats) {
        recorded += o.recorded
        refusedLocally += o.refusedLocally
        pushes += o.pushes
        pulls += o.pulls
        droppedRequests += o.droppedRequests
        droppedResponses += o.droppedResponses
        offlineFailures += o.offlineFailures
        staleDuplicates += o.staleDuplicates
        serverDuplicates += o.serverDuplicates
        accepted += o.accepted
        rejected += o.rejected
        deliveredAfterCancel += o.deliveredAfterCancel
        dispatcherCancels += o.dispatcherCancels
        healRounds += o.healRounds
        restarts += o.restarts
        o.rejectedBy.forEach { (k, n) -> rejectedBy.merge(k, n, Int::plus) }
    }
}

public data class SimReport(val seed: Long, val stats: SimStats, val violations: List<String>) {
    val ok: Boolean get() = violations.isEmpty()
}

/**
 * Deterministic simulation of one shift: [SimConfig.clients] devices on one route, a dispatcher,
 * and a network that drops, duplicates and delays messages and has dead zones. Everything runs on
 * one virtual-time scheduler and one seeded [Random], so a seed reproduces the exact same run.
 *
 * After the shift the network heals, every device syncs until quiet, and the invariants in
 * [checkInvariants] must hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class Simulation(private val cfg: SimConfig) {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val rng = Random(cfg.seed)
    private val stats = SimStats()
    private val route = RouteId("R-SIM-${cfg.seed}")
    private val rulings = RulingLog()
    private val memory = InMemorySyncStore()
    private val store: SyncStore = if (Fault.SERVER_FORGETS_SEQ in cfg.faults) ForgetfulStore(memory) else memory
    private val server =
        SyncService(
            store = store,
            wallClock = { SHIFT_START_MILLIS + scheduler.currentTime },
            observer = rulings,
        )

    private inner class Device(private val index: Int) {
        val network = FlakyNetwork(index)
        val skew = rng.nextLong(-30_000, 30_000)

        /** Survives restarts, like the SQLite file on the phone. Everything else is process memory. */
        private val database = JvmDatabase.create()
        private val seed = rng.nextLong()
        var engine = launchApp()

        fun launchApp() = SyncEngine(
            database = database,
            transport = network,
            routeId = route,
            wallClock = { SHIFT_START_MILLIS + scheduler.currentTime + skew },
            random = Random(seed + stats.restarts),
            io = dispatcher,
            newClientId = { "dev$index" },
        )

        val primary = index == 0
        val recorded = mutableListOf<Pair<Long, Mutation>>()
        val jobs = mutableListOf<Job>()
    }

    private inner class FlakyNetwork(index: Int) : SyncTransport {
        private val r = Random(cfg.seed * 31 + index)
        private val p = cfg.network
        var healed = false
        private val deadZones: List<LongRange> =
            List(p.deadZones) {
                val start = r.nextLong(0, cfg.shiftMillis)
                start..(start + r.nextLong(30_000, p.maxDeadZoneMillis))
            }

        private suspend fun hop() {
            delay(r.nextLong(p.minLatencyMillis, p.maxLatencyMillis))
            if (!healed && deadZones.any { scheduler.currentTime in it }) {
                stats.offlineFailures++
                throw IOException("no signal")
            }
        }

        private fun chance(x: Double) = !healed && r.nextDouble() < x

        override suspend fun push(route: RouteId, request: PushRequest): PushResponse {
            stats.pushes++
            hop()
            if (chance(p.dropRequest)) {
                stats.droppedRequests++
                throw IOException("push lost on the way up")
            }
            if (chance(p.duplicate)) {
                stats.staleDuplicates++
                val lag = r.nextLong(1_000, 5 * 60_000)
                scope.launch {
                    delay(lag)
                    try {
                        server.push(route, request)
                    } catch (e: SyncException.SeqGap) {
                        rulings.violations += "stale duplicate caused a seq gap: ${e.message}"
                    }
                }
            }
            val response = server.push(route, request)
            hop()
            if (chance(p.dropResponse)) {
                stats.droppedResponses++
                throw IOException("push response lost")
            }
            return response
        }

        override suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse {
            stats.pulls++
            hop()
            if (chance(p.dropRequest)) {
                stats.droppedRequests++
                throw IOException("pull lost")
            }
            val response = server.pull(route, client, cursor)
            hop()
            if (chance(p.dropResponse)) {
                stats.droppedResponses++
                throw IOException("pull response lost")
            }
            return response
        }
    }

    private inner class RulingLog : SyncObserver {
        val byKey = HashMap<Pair<ClientId, Long>, MutableList<MutationResult>>()
        val acceptedDeliveries = HashMap<StopId, Int>()
        val violations = mutableListOf<String>()

        override fun onRuling(route: RouteId, client: ClientId, envelope: MutationEnvelope, result: MutationResult) {
            byKey.getOrPut(client to envelope.seq) { mutableListOf() } += result
            if (result.outcome == Outcome.ACCEPTED) {
                stats.accepted++
                if (envelope.mutation is Mutation.Deliver) {
                    acceptedDeliveries.merge(envelope.mutation.stopId, 1, Int::plus)
                }
            } else {
                stats.rejected++
                val who = if (client.value == "dev0") "driver" else "helper"
                stats.rejectedBy.merge("${envelope.mutation::class.simpleName}/${result.reason}/$who", 1, Int::plus)
            }
        }

        override fun onDuplicate(route: RouteId, client: ClientId, seq: Long) {
            stats.serverDuplicates++
        }
    }

    public fun run(): SimReport {
        await { server.createRoute(route, buildRoute()) }
        val devices = List(cfg.clients) { Device(it) }

        // ---- the shift: chaos
        for (d in devices) {
            d.jobs += scope.launch { appLifecycle(d) }
            d.jobs += scope.launch { drive(d) }
        }
        val dispatch =
            scope.launch {
                launch { dispatcherLoop() }
                if (Fault.SERVER_ROLLBACK in cfg.faults) launch { serverRollback() }
            }
        scheduler.advanceTimeBy(cfg.shiftMillis)
        await {
            dispatch.cancelAndJoin()
            devices.flatMap { it.jobs }.forEach { it.cancelAndJoin() }
        }

        // ---- the network heals; stale duplicates still in flight may land during this phase
        devices.forEach { it.network.healed = true }
        scheduler.advanceUntilIdle()
        for (round in 1..cfg.maxHealRounds) {
            stats.healRounds = round
            devices.forEach { d -> scope.launch { runCatching { d.engine.syncOnce() } } }
            scheduler.advanceUntilIdle()
            if (converged(devices)) break
        }
        val violations = rulings.violations + checkInvariants(devices)
        return SimReport(cfg.seed, stats, violations)
    }

    /** The OS kills the app at random moments; the next launch starts from what is on disk. */
    private suspend fun appLifecycle(d: Device): Unit = coroutineScope {
        val r = Random(rng.nextLong())
        var app = launch { d.engine.run() }
        for (at in List(cfg.restartsPerDevice) { r.nextLong(0, cfg.shiftMillis) }.sorted()) {
            delay(at - scheduler.currentTime)
            app.cancelAndJoin()
            stats.restarts++
            d.engine = d.launchApp()
            app = launch { d.engine.run() }
        }
    }

    private suspend fun serverRollback() {
        delay(cfg.shiftMillis / 3)
        val checkpoint = memory.checkpoint()
        delay(cfg.shiftMillis / 3)
        memory.restore(checkpoint)
    }

    private suspend fun drive(d: Device) {
        val r = Random(rng.nextLong())
        while (true) {
            delay(r.nextLong(20_000, 6 * 60_000)) // driving to the next stop
            val open =
                d.engine
                    .snapshot()
                    .filter { it.status in OPEN }
                    .sortedBy { it.sequence }
            if (open.isEmpty()) continue
            // The primary device works the route in order; the second one is a helper picking stops.
            val stop = if (d.primary) open.first() else open[r.nextInt(open.size)]

            fun now() = SHIFT_START_MILLIS + scheduler.currentTime + d.skew
            record(d, Mutation.Arrive(stop.id, now()))
            delay(r.nextLong(5_000, 60_000))
            for (p in stop.parcels) if (r.nextDouble() < 0.8) record(d, Mutation.ScanParcel(stop.id, p.id, now()))
            if (r.nextDouble() < 0.12) {
                val reason = AttemptReason.entries[r.nextInt(AttemptReason.entries.size)]
                record(d, Mutation.Attempt(stop.id, reason, now()))
            } else {
                val handoff = HandoffMethod.entries[r.nextInt(HandoffMethod.entries.size)]
                record(d, Mutation.Deliver(stop.id, stop.parcels.map { it.id }, handoff, now()))
            }
            if (r.nextDouble() < 0.25) {
                record(d, Mutation.SetNote(stop.id, "note ${r.nextInt(1000)} from ${d.engine.clientId}"))
            }
        }
    }

    private suspend fun record(d: Device, m: Mutation) {
        when (val res = d.engine.record(m)) {
            is RecordResult.Recorded -> {
                stats.recorded++
                d.recorded += res.seq to m
            }

            is RecordResult.Refused -> {
                stats.refusedLocally++
            }
        }
    }

    private suspend fun dispatcherLoop() {
        val r = Random(rng.nextLong())
        repeat(cfg.dispatcherActions) {
            delay(r.nextLong(0, cfg.shiftMillis / cfg.dispatcherActions))
            val stops = server.snapshot(route)
            val target = stops[r.nextInt(stops.size)]
            if (r.nextBoolean()) {
                server.dispatch(route, DispatcherCommand.Cancel(target.id))
                stats.dispatcherCancels++
            } else {
                server.dispatch(route, DispatcherCommand.Resequence(target.id, r.nextInt(1, cfg.stops * 2)))
            }
        }
    }

    private fun buildRoute(): List<Stop> = List(cfg.stops) { i ->
        val n = i + 1
        Stop(
            id = StopId("S-%03d".format(n)),
            routeId = route,
            sequence = n,
            address = "${100 + n * 7} Sim Street",
            location = GeoPoint(47.60 + n * 0.001, -122.33 - n * 0.001),
            parcels =
            List(rng.nextInt(1, 4)) { k ->
                Parcel(ParcelId("TBA%06d%02d".format(n, k)), "parcel ${k + 1}")
            },
        )
    }

    private fun converged(devices: List<Device>): Boolean {
        val truth = await { server.snapshot(route) }.sortedBy { it.id.value }
        return devices.all { d ->
            d.engine.status.value.pending == 0L && await { d.engine.snapshot() }.sortedBy { it.id.value } == truth
        }
    }

    private fun checkInvariants(devices: List<Device>): List<String> {
        val v = mutableListOf<String>()
        val truth = await { server.snapshot(route) }.sortedBy { it.id.value }
        val byId = truth.associateBy { it.id }
        stats.deliveredAfterCancel = truth.count { it.conflict != null }

        for (d in devices) {
            val c = d.engine.clientId
            // 1. Convergence: the device shows exactly what the server has, with nothing pending.
            val pending = d.engine.status.value.pending
            if (pending != 0L) v += "$c: $pending mutations still pending"
            val view = await { d.engine.snapshot() }.sortedBy { it.id.value }
            if (view != truth) v += "$c: local view diverges from server (${diff(view, truth)})"

            // 2. No lost write: the server ruled on every seq the device recorded, and on nothing else.
            val last = await { server.pull(route, c, Long.MAX_VALUE).lastProcessedSeq }
            val expectedLast = d.recorded.lastOrNull()?.first ?: 0L
            if (last != expectedLast) v += "$c: server lastProcessedSeq=$last, device recorded up to $expectedLast"

            // 3. Exactly once: every seq ruled on exactly one time.
            for ((seq, _) in d.recorded) {
                val n = rulings.byKey[c to seq]?.size ?: 0
                if (n != 1) v += "$c/$seq ruled $n times"
            }

            // 4. The driver is told: every delivery the device recorded is either delivered on the
            //    server or shows up in the device's rejection list.
            val rejectedSeqs = await { d.engine.rejections().first() }.map { it.seq }.toSet()
            for ((seq, m) in d.recorded) {
                val ruling = rulings.byKey[c to seq]?.firstOrNull() ?: continue
                if (ruling.outcome == Outcome.REJECTED && seq !in rejectedSeqs) {
                    v += "$c/$seq rejected but device was never told"
                }
                val finalStatus = byId[m.stopId]?.status
                val acceptedDelivery = m is Mutation.Deliver && ruling.outcome == Outcome.ACCEPTED
                if (acceptedDelivery && finalStatus != StopStatus.DELIVERED) {
                    v += "$c/$seq accepted delivery of ${m.stopId} but stop ended ${byId[m.stopId]?.status}"
                }
            }
        }
        // 5. A parcel is delivered once: at most one accepted delivery per stop, across devices.
        rulings.acceptedDeliveries.filterValues { it > 1 }.forEach { (stop, n) -> v += "$stop delivered $n times" }
        return v
    }

    private fun diff(a: List<Stop>, b: List<Stop>): String {
        val bm = b.associateBy { it.id }
        return a.filter { bm[it.id] != it }.take(2).joinToString { s -> "${s.id}: local=$s server=${bm[s.id]}" }
    }

    private fun <T> await(block: suspend () -> T): T {
        val deferred = scope.async { block() }
        scheduler.advanceUntilIdle()
        @OptIn(ExperimentalCoroutinesApi::class)
        return deferred.getCompleted()
    }
}

/** [Fault.SERVER_FORGETS_SEQ]: a store whose dedup bookkeeping always says "nothing processed yet". */
private class ForgetfulStore(private val inner: SyncStore) : SyncStore by inner {
    override suspend fun <T> transaction(route: RouteId, block: SyncTransaction.() -> T): T = inner.transaction(route) {
        val real = this
        object : SyncTransaction by real {
            override fun lastProcessedSeq(client: ClientId): Long = 0

            override fun result(client: ClientId, seq: Long): MutationResult? = null

            override fun recordResult(client: ClientId, envelope: MutationEnvelope, result: MutationResult) {
                if (real.result(client, envelope.seq) == null) real.recordResult(client, envelope, result)
            }
        }.block()
    }
}
