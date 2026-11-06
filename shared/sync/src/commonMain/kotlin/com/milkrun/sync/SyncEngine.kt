package com.milkrun.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.ClientId
import com.milkrun.model.ClockDriftException
import com.milkrun.model.Decision
import com.milkrun.model.Hlc
import com.milkrun.model.HlcClock
import com.milkrun.model.Mutation
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.StopRules
import com.milkrun.model.wireName
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse
import com.milkrun.protocol.SyncApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The client half of the sync protocol (ADR 0002) for one route on one device.
 *
 * ```
 *  record(m) ──▶ [txn: StopRules on view ─▶ outbox += (seq, hlc, m) ─▶ view = result]
 *
 *  syncOnce():
 *     push   outbox (seq > acked)  ──▶ server rules, returns lastProcessedSeq + results
 *     pull   changes since cursor  ──▶ base := server snapshots
 *     rebase [txn: outbox -= seq <= lastProcessedSeq;  view := base + replay(remaining outbox)]
 * ```
 *
 * All local writes are serialised by one [Mutex] and happen inside SQLite transactions. Network
 * calls run outside the lock, so the driver can keep recording while a slow request is in flight.
 */
public class SyncEngine(
    database: MilkrunDatabase,
    private val transport: SyncTransport,
    public val routeId: RouteId,
    private val wallClock: () -> Long,
    random: Random = Random.Default,
    private val config: SyncConfig = SyncConfig(),
    private val io: CoroutineDispatcher = Dispatchers.Default,
    /** Used only on first launch; afterwards the id is read from the database. */
    newClientId: () -> String = { randomClientId(random) },
) {
    private val store = LocalStore(database)
    private val db = database
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val backoff = Backoff(random = random)

    public val clientId: ClientId
    private val clock: HlcClock

    init {
        val id = store.meta(KEY_CLIENT_ID) ?: newClientId().also { store.putMeta(KEY_CLIENT_ID, it) }
        clientId = ClientId(id)
        clock = HlcClock(id, wallClock, initial = store.meta(KEY_HLC)?.let(Hlc::parse) ?: Hlc.ZERO)
    }

    private val _status = MutableStateFlow(SyncStatus.INITIAL.copy(pending = outboxDepth()))
    public val status: StateFlow<SyncStatus> = _status.asStateFlow()

    // ---------------------------------------------------------------- reads (UI)

    public fun stops(): Flow<List<StopItem>> = db.stopQueries
        .stopsForRoute(routeId.value)
        .asFlow()
        .mapToList(io)
        .map { rows ->
            val parcels =
                db.stopQueries
                    .parcelsForRoute(routeId.value)
                    .executeAsList()
                    .groupBy { it.stop_id }
            rows.map { StopItem(it.toStop(parcels[it.id].orEmpty()), unsynced = it.pending > 0) }
        }

    public fun progress(): Flow<RouteProgress> = db.stopQueries
        .routeProgress(routeId.value)
        .asFlow()
        .mapToOne(io)
        .map { RouteProgress(it.total.toInt(), it.delivered.toInt(), it.attempted.toInt(), it.unsynced.toInt()) }

    public fun rejections(): Flow<List<Rejection>> = db.rejectionQueries.unseen().asFlow().mapToList(io).map { rows ->
        rows.map { Rejection(it.seq, StopId(it.stop_id), it.type, RejectReason.valueOf(it.reason)) }
    }

    public suspend fun markRejectionSeen(seq: Long): Unit = withContext(io) { db.rejectionQueries.markSeen(seq) }

    /** Current local view of the route, for tests and the simulator. */
    public suspend fun snapshot(): List<Stop> = withContext(io) { store.stopsForRoute(routeId) }

    // ---------------------------------------------------------------- writes

    /**
     * Applies [mutation] to the local view and appends it to the outbox, atomically. Refuses
     * mutations that are invalid against what this device currently knows; those never reach the
     * server.
     */
    public suspend fun record(mutation: Mutation): RecordResult {
        val result =
            mutex.withLock {
                withContext(io) {
                    db.transactionWithResult {
                        val view =
                            store.view(mutation.stopId) ?: return@transactionWithResult RecordResult.Refused(
                                RejectReason.UNKNOWN_STOP,
                            )
                        val hlc = clock.now()
                        when (val d = StopRules.apply(view, mutation, hlc)) {
                            is Decision.Rejected -> {
                                RecordResult.Refused(d.reason)
                            }

                            is Decision.Accepted -> {
                                val seq = (store.meta(KEY_NEXT_SEQ)?.toLong() ?: 1L)
                                store.append(MutationEnvelope(seq, hlc, mutation), wallClock())
                                store.writeView(d.stop, pending = store.pendingForStop(mutation.stopId).size.toLong())
                                store.putMeta(KEY_NEXT_SEQ, (seq + 1).toString())
                                store.putMeta(KEY_HLC, hlc.encode())
                                RecordResult.Recorded(seq, d.stop)
                            }
                        }
                    }
                }
            }
        if (result is RecordResult.Recorded) {
            _status.update { it.copy(pending = it.pending + 1) }
            wake.trySend(Unit)
        }
        return result
    }

    /** Asks [run] to sync now, for example when connectivity comes back or the app is foregrounded. */
    public fun requestSync() {
        wake.trySend(Unit)
    }

    /**
     * One full round: push everything pending, then pull and rebase. Throws if the network fails;
     * the local state is consistent at every point where it can throw.
     */
    public suspend fun syncOnce() {
        _status.update { it.copy(phase = SyncStatus.Phase.SYNCING) }
        try {
            pushAll()
            applyPull(transport.pull(routeId, clientId, cursor()))
        } catch (e: CancellationException) {
            _status.update { it.copy(phase = SyncStatus.Phase.IDLE) }
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Callers other than run() (pull-to-refresh, a WorkManager job) must not leave the
            // status stuck in SYNCING.
            _status.update {
                it.copy(
                    pending = outboxDepth(),
                    phase = SyncStatus.Phase.IDLE,
                    consecutiveFailures = it.consecutiveFailures + 1,
                    lastError = e.message ?: e::class.simpleName,
                )
            }
            throw e
        }
        _status.update {
            it.copy(
                pending = outboxDepth(),
                phase = SyncStatus.Phase.IDLE,
                lastSuccessAtMillis = wallClock(),
                consecutiveFailures = 0,
                lastError = null,
            )
        }
    }

    /**
     * Sync loop for the lifetime of the session: sync, then sleep until [requestSync], a new
     * mutation, or the periodic interval; on failure sleep with full-jitter backoff instead. A wake-up
     * cuts a backoff sleep short, so regaining signal syncs immediately.
     */
    public suspend fun run(): Nothing {
        while (true) {
            val sleepMillis =
                try {
                    syncOnce()
                    backoff.reset()
                    config.periodicIntervalMillis
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    // syncOnce() already recorded the failure in the status.
                    _status.update { it.copy(phase = SyncStatus.Phase.BACKING_OFF) }
                    backoff.next().inWholeMilliseconds
                }
            withTimeoutOrNull(sleepMillis) { wake.receive() }
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend fun pushAll() {
        var acked = 0L
        while (true) {
            val batch = withContext(io) { store.pendingAfter(acked, config.pushBatchSize.toLong()) }
            if (batch.isEmpty()) return
            val response = transport.push(routeId, PushRequest(clientId, batch))
            applyPushResults(batch, response)
            if (response.lastProcessedSeq < batch.last().seq) return // server stopped early; pull tells us why
            acked = response.lastProcessedSeq
        }
    }

    private suspend fun applyPushResults(batch: List<MutationEnvelope>, response: PushResponse) = mutex.withLock {
        withContext(io) {
            db.transaction {
                val bySeq = batch.associateBy { it.seq }
                for (r in response.results) {
                    if (r.outcome != Outcome.REJECTED) continue
                    val env = bySeq[r.seq] ?: continue
                    db.rejectionQueries.record(
                        seq = r.seq,
                        stop_id = env.mutation.stopId.value,
                        type = env.mutation.wireName,
                        reason = (r.reason ?: RejectReason.UNKNOWN_STOP).name,
                        payload = SyncApi.json.encodeToString(Mutation.serializer(), env.mutation),
                    )
                }
                observe(response.serverHlc)
            }
        }
    }

    private suspend fun applyPull(pull: PullResponse) = mutex.withLock {
        withContext(io) {
            db.transaction {
                val affected = LinkedHashSet<StopId>()
                pull.changes.forEach {
                    store.writeBase(it)
                    affected += it.id
                }
                db.outboxQueries
                    .stopsTouchedUpTo(pull.lastProcessedSeq)
                    .executeAsList()
                    .mapTo(affected) { StopId(it) }
                db.outboxQueries.confirmUpTo(pull.lastProcessedSeq)
                for (id in affected) rebase(id)
                store.putMeta(KEY_CURSOR, pull.cursor.toString())
                observe(pull.serverHlc)
            }
        }
    }

    /** view := confirmed base with every still-pending local mutation replayed in seq order. */
    private fun rebase(id: StopId) {
        val base = store.base(id) ?: return
        val pending = store.pendingForStop(id)
        var view = base
        for (env in pending) {
            // A pending mutation the new base makes invalid stays queued: the server will rule on it.
            val d = StopRules.apply(view, env.mutation, env.hlc)
            if (d is Decision.Accepted) view = d.stop
        }
        store.writeView(view, pending.size.toLong())
    }

    private fun observe(remote: Hlc) {
        try {
            clock.observe(remote)
        } catch (_: ClockDriftException) {
            // The server's clock is implausibly far ahead of ours. Keep our own clock rather than
            // jump into the future; ordering against the server only matters for LWW notes.
            clockDriftEvents++
        }
        store.putMeta(KEY_HLC, clock.last.encode())
    }

    /** Times the server's clock was rejected as too far ahead (diagnostics). */
    public var clockDriftEvents: Int = 0
        private set

    private suspend fun cursor(): Long = withContext(io) { store.meta(KEY_CURSOR)?.toLong() ?: 0L }

    private fun outboxDepth(): Long = db.outboxQueries.depth().executeAsOne()

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_NEXT_SEQ = "next_seq"
        const val KEY_CURSOR = "cursor"
        const val KEY_HLC = "hlc"
    }
}

private const val CLIENT_ID_LENGTH = 8

internal fun randomClientId(random: Random): String {
    val alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
    return buildString { repeat(CLIENT_ID_LENGTH) { append(alphabet[random.nextInt(alphabet.length)]) } }
}
