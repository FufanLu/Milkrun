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
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

/**
 * iOS composition root, called once from the SwiftUI App. Mirrors MilkrunApp on Android: one
 * session per process, one engine per route database.
 */
public object Milkrun {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    public suspend fun start(serverUrl: String): MilkrunSession = MilkrunSession.start(
        http = HttpClient(Darwin) { milkrunDefaults(serverUrl) },
        store = DefaultsSessionStore(),
        database = { route -> IosDatabase.create("route-${route.value}.db") },
        scope = scope,
        wallClock = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    )
}

/** Shift credentials in UserDefaults. M6 moves the token to the Keychain. */
private class DefaultsSessionStore : SessionStore {
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
