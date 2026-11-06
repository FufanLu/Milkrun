package com.milkrun.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.milkrun.android.sync.SyncWorker
import com.milkrun.database.AndroidDatabase
import com.milkrun.network.milkrunDefaults
import com.milkrun.presentation.MilkrunSession
import com.milkrun.presentation.SessionStore
import com.milkrun.protocol.DevSession
import com.milkrun.protocol.SyncApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Composition root. One session per process: the screen model, the WorkManager job and the
 * connectivity callback all share it, so only one SyncEngine ever writes the route's database.
 */
class MilkrunApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http by lazy { HttpClient(OkHttp) { milkrunDefaults(BuildConfig.SERVER_URL) } }

    /** Started once; awaited by whoever needs it first. */
    val session: Deferred<MilkrunSession> by lazy {
        appScope.async {
            MilkrunSession.start(
                http = http,
                store = PrefsSessionStore(this@MilkrunApp),
                database = { route -> AndroidDatabase.create(this@MilkrunApp, "route-${route.value}.db") },
                scope = appScope,
                wallClock = System::currentTimeMillis,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Regaining signal is the moment unsent work should go, not at the next backoff tick.
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    appScope.launch { session.await().screen.refresh() }
                }
            },
        )
        // While the app is backgrounded or killed, WorkManager keeps draining the outbox.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

/**
 * Shift credentials in private SharedPreferences. M6 moves the token into an Android Keystore
 * backed store; route data already never leaves the device (see data_extraction_rules.xml).
 */
private class PrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun load(): DevSession? = prefs.getString("session", null)?.let {
        runCatching { SyncApi.json.decodeFromString(DevSession.serializer(), it) }.getOrNull()
    }

    override fun save(session: DevSession) = prefs.edit {
        putString("session", SyncApi.json.encodeToString(DevSession.serializer(), session))
    }

    override fun clear() = prefs.edit { remove("session") }
}

object Prefs {
    fun sunMode(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("sun", false)

    fun setSunMode(context: Context, on: Boolean) = context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit {
        putBoolean("sun", on)
    }
}
