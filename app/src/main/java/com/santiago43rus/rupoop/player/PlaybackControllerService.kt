package com.santiago43rus.rupoop.player

import android.content.Intent
import android.os.Build
import android.util.Log
import com.santiago43rus.rupoop.util.PlayerState
import com.santiago43rus.rupoop.util.extractId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun PlaybackController.toggleBackgroundPlayback() {
    isBackgroundPlaybackEnabled = !isBackgroundPlaybackEnabled
    syncPlaybackService()
}

internal fun PlaybackController.syncPlaybackService() {
    val showBackgroundNotifications = settingsManager.showBackgroundNotifications
    val shouldRun = showBackgroundNotifications && isBackgroundPlaybackEnabled && playerState != PlayerState.CLOSED
    if (shouldRun) {
        try {
            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("Rupoop", "Failed to start PlaybackService", e)
        }
    } else {
        try {
            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e("Rupoop", "Failed to stop PlaybackService", e)
        }
    }
}

// ── Periodic progress saving ──
fun PlaybackController.startProgressSaving() {
    progressSavingJob?.cancel()
    progressSavingJob = scope.launch {
        while (true) {
            delay(15000)
            if (isPlaying && currentVideo != null) {
                extractId(currentVideo!!.videoUrl)?.let { id ->
                    registryManager.updateWatchProgress(id, exoPlayer.currentPosition, exoPlayer.duration)
                    onRegistryUpdate(registryManager.registry)
                    pushToGitHub()
                }
            }
        }
    }
}

fun PlaybackController.stopProgressSaving() {
    progressSavingJob?.cancel()
}
