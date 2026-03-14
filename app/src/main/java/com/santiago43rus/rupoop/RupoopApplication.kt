package com.santiago43rus.rupoop

import android.app.Application
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.service.SyncWorker
import java.util.concurrent.TimeUnit

class RupoopApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        setupPeriodicSync()
    }


    private fun setupPeriodicSync() {
        val settingsManager = SettingsManager(this)
        val hours = settingsManager.syncFrequencyHours.toLong().coerceAtLeast(1)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rupoop_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

