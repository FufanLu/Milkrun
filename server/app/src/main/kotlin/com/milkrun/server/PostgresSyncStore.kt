package com.milkrun.server

import com.milkrun.model.ClientId
import com.milkrun.model.Mutation
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.wireName
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.SyncApi
import com.milkrun.server.sync.SyncStore
import com.milkrun.server.sync.SyncTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext

/**
 * [SyncStore] on PostgreSQL. One JDBC transaction per [transaction] call, opened with
 * `SELECT version FROM route WHERE id = ? FOR UPDATE`, so all work on a route is serialised.
 *
 * Contention is bounded by design: only the devices on one route (one to three) and that route's
 * dispatcher ever wait on the same lock; different routes never block each other.
 *
 * @param io where blocking JDBC runs. The simulator passes an empty context so the work stays on its
 *   virtual-time scheduler and remains deterministic.
 */
public class PostgresSyncStore(private val dataSource: DataSource, private val io: CoroutineContext = Dispatchers.IO) :
    SyncStore {
    private val json = SyncApi.json

    override suspend fun createRoute(route: RouteId, stops: List<Stop>): Unit = withContext(io) {
        require(stops.all { it.routeId == route }) { "stop on another route" }
        dataSource.connection.use { c ->
            c.inTransaction {
                val inserted = c.prepareStatement(
                    "INSERT INTO route (id, version) VALUES (?, 1) ON CONFLICT DO NOTHING",
                ).use {
                    it.setString(1, route.value)
                    it.executeUpdate()
                }
                require(inserted == 1) { "route $route exists" }
                c.prepareStatement(
                    "INSERT INTO stop (route_id, id, version, sequence, status, snapshot) VALUES (?, ?, 1, ?, ?, ?::jsonb)",
                ).use { ps ->
                    for (s in stops) {
                        ps.setString(1, route.value)
                        ps.setString(2, s.id.value)
                        ps.setInt(3, s.sequence)
                        ps.setString(4, s.status.name)
                        ps.setString(5, json.encodeToString(Stop.serializer(), s))
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        }
    }

    override suspend fun <T> transaction(route: RouteId, block: SyncTransaction.() -> T): T = withContext(io) {
        dataSource.connection.use { c -> c.inTransaction { PgTransaction(c, route).block() } }
    }

    private inline fun <T> Connection.inTransaction(block: () -> T): T {
        autoCommit = false
        try {
            val result = block()
            commit()
            return result
        } catch (e: Throwable) {
            try {
                rollback()
            } catch (rollbackFailure: SQLException) {
                e.addSuppressed(rollbackFailure)
            }
            throw e
        } finally {
            autoCommit = true
        }
    }

    private inner class PgTransaction(private val c: Connection, override val route: RouteId) : SyncTransaction {
        /** null when the route does not exist. Taking it locks the route for this transaction. */
        private var version: Long? = c.prepareStatement("SELECT version FROM route WHERE id = ? FOR UPDATE").use {
            it.setString(1, route.value)
            it.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }

        private val v: Long get() = checkNotNull(version) { "route $route does not exist" }

        override fun exists(): Boolean = version != null

        override fun version(): Long = v

        override fun stop(id: StopId): Stop? =
            c.prepareStatement("SELECT snapshot FROM stop WHERE route_id = ? AND id = ?").use {
                it.setString(1, route.value)
                it.setString(2, id.value)
                it.executeQuery().use { rs -> if (rs.next()) decode(rs.getString(1)) else null }
            }

        override fun writeStop(stop: Stop): Long {
            require(stop.routeId == route)
            val next = v + 1
            c.prepareStatement("UPDATE route SET version = ? WHERE id = ?").use {
                it.setLong(1, next)
                it.setString(2, route.value)
                it.executeUpdate()
            }
            c.prepareStatement(
                """
                INSERT INTO stop (route_id, id, version, sequence, status, snapshot, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, now())
                ON CONFLICT (route_id, id) DO UPDATE SET
                    version = EXCLUDED.version, sequence = EXCLUDED.sequence, status = EXCLUDED.status,
                    snapshot = EXCLUDED.snapshot, updated_at = now()
                """.trimIndent(),
            ).use {
                it.setString(1, route.value)
                it.setString(2, stop.id.value)
                it.setLong(3, next)
                it.setInt(4, stop.sequence)
                it.setString(5, stop.status.name)
                it.setString(6, json.encodeToString(Stop.serializer(), stop))
                it.executeUpdate()
            }
            version = next
            return next
        }

        override fun changesSince(cursor: Long): List<Stop> = c.prepareStatement(
            "SELECT snapshot FROM stop WHERE route_id = ? AND version > ? ORDER BY sequence, id",
        ).use {
            it.setString(1, route.value)
            it.setLong(2, cursor)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(decode(rs.getString(1))) } }
        }

        override fun lastProcessedSeq(client: ClientId): Long =
            c.prepareStatement("SELECT last_seq FROM client_cursor WHERE route_id = ? AND client_id = ?").use {
                it.setString(1, route.value)
                it.setString(2, client.value)
                it.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }

        override fun result(client: ClientId, seq: Long): MutationResult? = c.prepareStatement(
            "SELECT outcome, reason FROM mutation_log WHERE route_id = ? AND client_id = ? AND seq = ?",
        ).use {
            it.setString(1, route.value)
            it.setString(2, client.value)
            it.setLong(3, seq)
            it.executeQuery().use { rs ->
                if (!rs.next()) return null
                MutationResult(
                    seq = seq,
                    outcome = if (rs.getString(1) == "accepted") Outcome.ACCEPTED else Outcome.REJECTED,
                    reason = rs.getString(2)?.let(RejectReason::valueOf),
                )
            }
        }

        override fun recordResult(client: ClientId, envelope: MutationEnvelope, result: MutationResult) {
            c.prepareStatement(
                """
                INSERT INTO mutation_log (route_id, client_id, seq, stop_id, hlc, type, payload, outcome, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """.trimIndent(),
            ).use {
                it.setString(1, route.value)
                it.setString(2, client.value)
                it.setLong(3, envelope.seq)
                it.setString(4, envelope.mutation.stopId.value)
                it.setString(5, envelope.hlc.encode())
                it.setString(6, envelope.mutation.wireName)
                it.setString(7, json.encodeToString(Mutation.serializer(), envelope.mutation))
                it.setString(8, if (result.outcome == Outcome.ACCEPTED) "accepted" else "rejected")
                it.setString(9, result.reason?.name)
                it.executeUpdate()
            }
            c.prepareStatement(
                """
                INSERT INTO client_cursor (route_id, client_id, last_seq) VALUES (?, ?, ?)
                ON CONFLICT (route_id, client_id) DO UPDATE SET last_seq = EXCLUDED.last_seq, updated_at = now()
                """.trimIndent(),
            ).use {
                it.setString(1, route.value)
                it.setString(2, client.value)
                it.setLong(3, envelope.seq)
                it.executeUpdate()
            }
        }

        private fun decode(text: String): Stop = json.decodeFromString(Stop.serializer(), text)
    }
}
