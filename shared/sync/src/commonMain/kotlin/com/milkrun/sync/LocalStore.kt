package com.milkrun.sync

import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.AttemptReason
import com.milkrun.model.ConflictFlag
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Hlc
import com.milkrun.model.Mutation
import com.milkrun.model.Parcel
import com.milkrun.model.ParcelId
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.model.wireName
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.SyncApi
import com.milkrun.database.Outbox as OutboxRow
import com.milkrun.database.Parcel as ParcelRow
import com.milkrun.database.Stop as StopRow

/** Row mapping for the engine. Callers own transactions. */
internal class LocalStore(val db: MilkrunDatabase) {
    private val json = SyncApi.json

    fun meta(key: String): String? = db.syncMetaQueries.get(key).executeAsOneOrNull()

    fun putMeta(key: String, value: String) = db.syncMetaQueries.put(key, value)

    fun view(id: StopId): Stop? {
        val row = db.stopQueries.stopById(id.value).executeAsOneOrNull() ?: return null
        return row.toStop(db.stopQueries.parcelsForStop(id.value).executeAsList())
    }

    fun writeView(stop: Stop, pending: Long) {
        db.stopQueries.replaceStop(
            id = stop.id.value,
            route_id = stop.routeId.value,
            sequence = stop.sequence.toLong(),
            address = stop.address,
            lat = stop.location.lat,
            lng = stop.location.lng,
            status = stop.status.name,
            attempt_reason = stop.attemptReason?.name,
            handoff = stop.handoff?.name,
            arrived_at = stop.arrivedAtMillis,
            completed_at = stop.completedAtMillis,
            note = stop.note,
            note_hlc = stop.noteHlc.encode(),
            conflict = stop.conflict?.name,
            pending = pending,
        )
        db.stopQueries.deleteParcels(stop.id.value)
        stop.parcels.forEachIndexed { i, p ->
            db.stopQueries.insertParcel(
                p.id.value,
                stop.id.value,
                i.toLong(),
                p.description,
                p.scanned.long,
                p.delivered.long,
            )
        }
    }

    fun base(id: StopId): Stop? = db.stopBaseQueries
        .baseById(id.value)
        .executeAsOneOrNull()
        ?.let { json.decodeFromString(Stop.serializer(), it) }

    fun writeBase(stop: Stop) =
        db.stopBaseQueries.replaceBase(stop.id.value, stop.routeId.value, json.encodeToString(Stop.serializer(), stop))

    fun append(env: MutationEnvelope, nowMillis: Long) = db.outboxQueries.append(
        seq = env.seq,
        stop_id = env.mutation.stopId.value,
        hlc = env.hlc.encode(),
        type = env.mutation.wireName,
        payload = json.encodeToString(Mutation.serializer(), env.mutation),
        created_at = nowMillis,
    )

    fun pendingForStop(id: StopId): List<MutationEnvelope> = db.outboxQueries
        .pendingForStop(id.value)
        .executeAsList()
        .map { it.toEnvelope() }

    fun pendingAfter(seq: Long, limit: Long): List<MutationEnvelope> = db.outboxQueries
        .after(seq, limit)
        .executeAsList()
        .map { it.toEnvelope() }

    fun stopsForRoute(route: RouteId): List<Stop> {
        val parcels =
            db.stopQueries
                .parcelsForRoute(route.value)
                .executeAsList()
                .groupBy { it.stop_id }
        return db.stopQueries
            .stopsForRoute(route.value)
            .executeAsList()
            .map { it.toStop(parcels[it.id].orEmpty()) }
    }

    private fun OutboxRow.toEnvelope() =
        MutationEnvelope(seq, Hlc.parse(hlc), json.decodeFromString(Mutation.serializer(), payload))

    private val Boolean.long: Long get() = if (this) 1L else 0L
}

internal fun StopRow.toStop(parcels: List<ParcelRow>): Stop = Stop(
    id = StopId(id),
    routeId = RouteId(route_id),
    sequence = sequence.toInt(),
    address = address,
    location = GeoPoint(lat, lng),
    parcels = parcels.map { Parcel(ParcelId(it.id), it.description, it.scanned != 0L, it.delivered != 0L) },
    status = StopStatus.valueOf(status),
    attemptReason = attempt_reason?.let(AttemptReason::valueOf),
    handoff = handoff?.let(HandoffMethod::valueOf),
    arrivedAtMillis = arrived_at,
    completedAtMillis = completed_at,
    note = note,
    noteHlc = Hlc.parse(note_hlc),
    conflict = conflict?.let(ConflictFlag::valueOf),
)
