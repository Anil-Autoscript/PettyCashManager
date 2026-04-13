package com.pettycash.manager.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.repository.TransactionRepository

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferenceManager.getInstance(applicationContext)
        if (!prefs.isLoggedIn) return Result.success()

        val repo = TransactionRepository(applicationContext)
        val companyId = prefs.companyId

        return try {
            // Push unsynced local transactions
            repo.syncUnsyncedTransactions(companyId)
            // Pull latest from server
            repo.syncFromServer(companyId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
