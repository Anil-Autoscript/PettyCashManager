package com.pettycash.manager

import androidx.multidex.MultiDexApplication
import androidx.work.*
import com.pettycash.manager.workers.SyncWorker
import java.util.concurrent.TimeUnit

class PettyCashApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        scheduleSyncWork()
    }

    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PettyCashSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
