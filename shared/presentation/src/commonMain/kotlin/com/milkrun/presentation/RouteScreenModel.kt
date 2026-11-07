package com.milkrun.presentation

import com.milkrun.model.AttemptReason
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Mutation
import com.milkrun.model.ParcelId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.proof.ProofUploader
import com.milkrun.proof.UploadJob
import com.milkrun.sync.RecordResult
import com.milkrun.sync.SyncEngine
import com.milkrun.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen logic for the route and stop screens, shared by both apps. Android wraps it in a
 * ViewModel; iOS observes [state] through SKIE as an AsyncSequence.
 *
 * Every intent records a mutation through [SyncEngine.record]: the local view updates in the same
 * transaction, so the UI changes the moment the driver taps, with or without signal.
 */
public class RouteScreenModel(
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val uploader: ProofUploader? = null,
) {
    private val uploads: StateFlow<List<UploadJob>> =
        (uploader?.jobs() ?: flowOf(emptyList())).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val stops: StateFlow<List<com.milkrun.sync.StopItem>> =
        engine.stops().stateIn(scope, SharingStarted.Eagerly, emptyList())

    public val state: StateFlow<RouteUiState> = combine(stops, engine.status, engine.rejections()) {
            items,
            status,
            rejections,
        ->
        build(items, status, rejections)
    }.stateIn(scope, SharingStarted.Eagerly, RouteUiState.LOADING)

    /** Detail for one stop, updating as the driver works it and as the server rules. */
    public fun stop(id: StopId): Flow<StopDetailState?> = combine(stops, uploads) { items, jobs ->
        items.firstOrNull { it.stop.id == id }?.let { item ->
            val job = jobs.lastOrNull { it.stopId == id }
            val photo = when {
                job == null && item.stop.proof == null -> PhotoState.None
                job == null || job.done -> PhotoState.Sent
                else -> PhotoState.Uploading(job.partsSent, job.parts)
            }
            detail(item.stop, item.unsynced).copy(photo = photo)
        }
    }

    /**
     * Queues the photo at [path] for upload and records which upload proves the stop. The mutation
     * goes through the outbox like any other; the bytes follow on their own resumable channel.
     */
    public suspend fun attachPhoto(stop: StopId, path: String): RecordResult {
        val up = checkNotNull(uploader) { "no uploader in this session" }
        val job = up.enqueue(stop, path)
        return engine.record(Mutation.AttachProof(stop, job.uploadId, job.sha256))
    }

    public suspend fun arrive(stop: StopId): RecordResult = engine.record(Mutation.Arrive(stop, now()))

    /** Scanning by tap until the camera scanner lands; the mutation is the same either way. */
    public suspend fun scan(stop: StopId, parcel: ParcelId): RecordResult =
        engine.record(Mutation.ScanParcel(stop, parcel, now()))

    /**
     * Delivers every parcel at the stop with [handoff]. The parcel list is resolved here, not passed
     * in: a `List<ParcelId>` built in Swift arrives with its value-class elements unboxed (as
     * strings) and fails the cast inside StopRules. Only single value-class parameters bridge safely.
     */
    public suspend fun deliver(stop: StopId, handoff: HandoffMethod): RecordResult {
        val parcels = stops.value.firstOrNull { it.stop.id == stop }?.stop?.parcels?.map { it.id }.orEmpty()
        return engine.record(Mutation.Deliver(stop, parcels, handoff, now()))
    }

    public suspend fun attempt(stop: StopId, reason: AttemptReason): RecordResult =
        engine.record(Mutation.Attempt(stop, reason, now()))

    public suspend fun setNote(stop: StopId, text: String): RecordResult = engine.record(Mutation.SetNote(stop, text))

    public fun dismiss(notice: Notice) {
        scope.launch { engine.markRejectionSeen(notice.seq) }
    }

    /** Pull-to-refresh, app foreground, connectivity regained. */
    public fun refresh() {
        engine.requestSync()
        uploader?.requestDrain()
    }

    /** For callers that need a reason in words, such as a refused tap. */
    public fun refusal(result: RecordResult, stopNumber: String): String? =
        (result as? RecordResult.Refused)?.let { Copy.rejection(stopNumber, it.reason).first }

    private fun build(
        items: List<com.milkrun.sync.StopItem>,
        status: SyncStatus,
        rejections: List<com.milkrun.sync.Rejection>,
    ): RouteUiState {
        val cards = items.map { card(it.stop, it.unsynced) }.sortedBy { it.sequence }
        val open = cards.filter { it.status == StopStatus.PLANNED || it.status == StopStatus.ARRIVED }
        val done = cards.filter {
            it.status == StopStatus.DELIVERED || it.status == StopStatus.ATTEMPTED ||
                it.status == StopStatus.CANCELLED
        }
        // An arrived stop is where the driver is standing, so it is next even if out of order.
        val next = open.firstOrNull { it.status == StopStatus.ARRIVED } ?: open.firstOrNull()
        val numbers = cards.associate { it.id to it.number }
        return RouteUiState(
            routeLabel = items.firstOrNull()?.stop?.routeId?.value.orEmpty(),
            loaded = status.lastSuccessAtMillis != null || items.isNotEmpty(),
            remaining = open.size,
            delivered = cards.count { it.status == StopStatus.DELIVERED },
            attempted = cards.count { it.status == StopStatus.ATTEMPTED },
            sync = if (status.pending == 0L) {
                SyncBanner.AllSent(status.lastSuccessAtMillis)
            } else {
                SyncBanner.Waiting(
                    status.pending,
                    retrying = status.consecutiveFailures > 0,
                    lastError = status.lastError,
                )
            },
            next = next,
            upcoming = open - setOfNotNull(next),
            done = done.sortedByDescending { it.sequence },
            // A stop that ended up delivered (for example after a cancel the driver never saw) makes
            // its earlier rejected arrive/scan moot; telling the driver to "bring the parcels back"
            // would contradict the delivery they just made.
            notices = rejections.filterNot { r ->
                cards.any { it.id == r.stopId && it.status == StopStatus.DELIVERED }
            }.map { r ->
                val number = numbers[r.stopId] ?: r.stopId.value
                val (title, body) = Copy.rejection(number, r.reason)
                Notice(r.seq, number, title, body)
            },
        )
    }

    private fun card(s: Stop, unsynced: Boolean) = StopCard(
        id = s.id,
        sequence = s.sequence,
        number = s.sequence.toString().padStart(2, '0'),
        address = s.address,
        parcelCount = s.parcels.size,
        scannedCount = s.parcels.count { it.scanned },
        status = s.status,
        attemptReason = s.attemptReason,
        handoff = s.handoff,
        unsynced = unsynced,
        deliveredAfterCancel = s.conflict != null,
        note = s.note,
    )

    private fun detail(s: Stop, unsynced: Boolean): StopDetailState {
        val card = card(s, unsynced)
        val open = s.status == StopStatus.PLANNED || s.status == StopStatus.ARRIVED || s.status == StopStatus.ATTEMPTED
        return StopDetailState(
            card = card,
            parcels = s.parcels.map { ParcelRow(it.id, it.description, it.scanned, it.delivered) },
            canArrive = s.status == StopStatus.PLANNED || s.status == StopStatus.ATTEMPTED,
            canScan = open,
            canDeliver = open && s.parcels.isNotEmpty(),
            canAttempt = open,
            canAttachPhoto = uploader != null && (s.status == StopStatus.DELIVERED || s.status == StopStatus.ATTEMPTED),
        )
    }
}
