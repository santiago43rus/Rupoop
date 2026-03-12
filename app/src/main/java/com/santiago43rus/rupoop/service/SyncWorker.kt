package com.santiago43rus.rupoop.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.santiago43rus.rupoop.auth.GistSyncManager
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.network.RetrofitClient

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val token = settingsManager.accessToken ?: return Result.success()
        
        val registryManager = UserRegistryManager(applicationContext)
        val syncManager = GistSyncManager(RetrofitClient.gistApi, registryManager, settingsManager)
        
        return try {
            syncManager.sync(token)
            settingsManager.lastSyncTime = System.currentTimeMillis()
            Log.d("SyncWorker", "Background sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

