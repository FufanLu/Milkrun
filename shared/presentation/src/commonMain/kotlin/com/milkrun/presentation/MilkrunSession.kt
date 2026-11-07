package com.milkrun.presentation

import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.RouteId
import com.milkrun.network.KtorSyncTransport
import com.milkrun.proof.KtorUploadTransport
import com.milkrun.proof.ProofUploader
import com.milkrun.protocol.DevSession
import com.milkrun.sync.SyncEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the app keeps its shift credentials (SharedPreferences / UserDefaults; Keystore/Keychain in M6). */
public interface SessionStore {
    public fun load(): DevSession?

    public fun save(session: DevSession)

    public fun clear()
}

/**
 * One driver's shift on one device: credentials, the route's database, the sync engine and its
 * loop. Each route gets its own database file, so ending a shift is deleting one file.
 */
public class MilkrunSession private constructor(
    public val session: DevSession,
    public val engine: SyncEngine,
    public val screen: RouteScreenModel,
    private val loop: Job,
    private val store: SessionStore,
) {
    public val routeId: RouteId get() = session.routeId

    public fun close() {
        loop.cancel()
    }

    /**
     * End of shift: stop syncing, forget the credentials, and let the platform delete the route's
     * database and photos ([wipe]). Refuses while anything is unsent (see RouteUiState.canEndShift).
     */
    public fun endShift(wipe: (routeId: String) -> Unit): Boolean {
        if (!screen.state.value.canEndShift) return false
        loop.cancel()
        store.clear()
        wipe(session.routeId.value)
        return true
    }

    public companion object {
        /**
         * Restores the saved shift, or (dev builds) asks the server for a demo route. Starts the
         * sync loop in [scope].
         */
        public suspend fun start(
            http: HttpClient,
            store: SessionStore,
            database: (RouteId) -> MilkrunDatabase,
            scope: CoroutineScope,
            wallClock: () -> Long,
        ): MilkrunSession {
            val session = store.load() ?: http.post(DevSession.PATH).body<DevSession>().also(store::save)
            val routeDb = database(session.routeId)
            val engine = SyncEngine(
                database = routeDb,
                transport = KtorSyncTransport(http) { session.driverToken },
                routeId = session.routeId,
                wallClock = wallClock,
            )
            val uploader =
                ProofUploader(routeDb, KtorUploadTransport(http) { session.driverToken }, session.routeId, wallClock)
            val loop = scope.launch {
                launch { engine.run() }
                launch { uploader.run() }
            }
            return MilkrunSession(session, engine, RouteScreenModel(engine, scope, wallClock, uploader), loop, store)
        }
    }
}
