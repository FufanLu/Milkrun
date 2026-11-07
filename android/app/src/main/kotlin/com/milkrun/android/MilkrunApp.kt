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

    /** The current shift; replaced after [endShift]. Awaited by whoever needs it first. */
    @Volatile
    var session: Deferred<MilkrunSession> = startSession()
        private set

    private fun startSession(): Deferred<MilkrunSession> = appScope.async {
        MilkrunSession.start(
            http = http,
            store = KeystoreSessionStore(this@MilkrunApp),
            database = { route -> AndroidDatabase.create(this@MilkrunApp, "route-${route.value}.db") },
            scope = appScope,
            wallClock = System::currentTimeMillis,
        )
    }

    /**
     * End of shift: wipe this route's database and proof photos (customer addresses and doorstep
     * pictures must not outlive the shift), then start the next session. False while anything
     * is unsent.
     */
    suspend fun endShift(): Boolean {
        val ended = session.await().endShift { route ->
            deleteDatabase("route-$route.db")
            java.io.File(filesDir, "proof").deleteRecursively()
        }
        if (ended) session = startSession()
        return ended
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

object Prefs {
    fun sunMode(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("sun", false)

    fun setSunMode(context: Context, on: Boolean) = context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit {
        putBoolean("sun", on)
    }
}
