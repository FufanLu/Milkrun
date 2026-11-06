package com.milkrun.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.milkrun.android.MilkrunApp

/** Background drain: one sync round on the process's session. WorkManager owns the retry schedule. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MilkrunApp
        return runCatching { app.session.await().engine.syncOnce() }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val NAME = "milkrun-sync"
    }
}
