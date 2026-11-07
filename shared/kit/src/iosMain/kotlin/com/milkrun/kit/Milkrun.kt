package com.milkrun.kit

import com.milkrun.database.IosDatabase
import com.milkrun.network.milkrunDefaults
import com.milkrun.presentation.MilkrunSession
import com.milkrun.presentation.SessionStore
import com.milkrun.protocol.DevSession
import com.milkrun.protocol.SyncApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

/**
 * iOS composition root, called once from the SwiftUI App. Mirrors MilkrunApp on Android: one
 * session per process, one engine per route database.
 */
public object Milkrun {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** [store] is the app's Keychain-backed store (Swift); the UserDefaults one is for tests only. */
    public suspend fun start(serverUrl: String, store: SessionStore): MilkrunSession {
        installCrashLogging()
        return startSession(serverUrl, store)
    }

    /** Deletes a finished route's database file (the platform half of MilkrunSession.endShift). */
    public fun deleteRouteDatabase(routeId: String) {
        platform.Foundation.NSFileManager.defaultManager.let { fm ->
            val dir = (
                fm.URLsForDirectory(
                    platform.Foundation.NSApplicationSupportDirectory,
                    platform.Foundation.NSUserDomainMask,
                ).firstOrNull() as? platform.Foundation.NSURL
                )
            listOf("route-$routeId.db", "route-$routeId.db-wal", "route-$routeId.db-shm").forEach { name ->
                dir?.URLByAppendingPathComponent("databases")?.URLByAppendingPathComponent(name)?.let {
                    fm.removeItemAtURL(it, null)
                }
            }
        }
    }

    /** Kotlin's uncaught exceptions go to stderr, which iOS drops: put them in the unified log first. */
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private fun installCrashLogging() {
        val previous = setUnhandledExceptionHook { t ->
            // NSLog is variadic; a Kotlin String is not bridged there, so the text goes in the format itself.
            NSLog("Milkrun uncaught Kotlin exception: " + t.stackTraceToString().replace("%", "%%"))
        }
        if (previous != null) setUnhandledExceptionHook(previous)
    }

    private suspend fun startSession(serverUrl: String, store: SessionStore): MilkrunSession = MilkrunSession.start(
        http = HttpClient(Darwin) { milkrunDefaults(serverUrl) },
        store = store,
        database = { route -> IosDatabase.create("route-${route.value}.db") },
        scope = scope,
        wallClock = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    )
}

/** Plain UserDefaults; tests and previews only. The app passes a Keychain store. */
public class DefaultsSessionStore : SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): DevSession? = defaults.stringForKey(KEY)?.let {
        runCatching { SyncApi.json.decodeFromString(DevSession.serializer(), it) }.getOrNull()
    }

    override fun save(session: DevSession) =
        defaults.setObject(SyncApi.json.encodeToString(DevSession.serializer(), session), KEY)

    override fun clear() = defaults.removeObjectForKey(KEY)

    private companion object {
        const val KEY = "milkrun.session"
    }
}

/** JSON helpers for Swift, so the Keychain store uses the exact wire format. */
public object Wire {
    public fun encodeSession(session: DevSession): String =
        SyncApi.json.encodeToString(DevSession.serializer(), session)

    public fun decodeSession(json: String): DevSession? =
        runCatching { SyncApi.json.decodeFromString(DevSession.serializer(), json) }.getOrNull()
}
