package com.santiago43rus.rupoop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// New notification system: each upload gets a unique numeric notification id mapped from the upload's unique string id (VIDEO_ID)
// Actions: PAUSE / RESUME / CANCEL. Old system removed.

internal val notifIdCounter = AtomicInteger(1000)
internal val uploadToNotifId = ConcurrentHashMap<String, Int>()

internal fun getOrCreateNotifId(uploadId: String): Int {
    return uploadToNotifId.computeIfAbsent(uploadId) { notifIdCounter.getAndIncrement() }
}

// Generation counter per upload. Bumped on every explicit state change (pause/resume/cancel).
// A background progress update from DownloadTask's coroutine is tagged with the generation
// it started with; if that generation is stale by the time it's about to be posted (because
// the user paused/cancelled/resumed in the meantime), it's dropped instead of clobbering the
// more recent state. This is what used to cause the notification to silently revert back to
// "Downloading" right after tapping Pause, making it look like the tap had no effect.
internal val uploadGeneration = ConcurrentHashMap<String, AtomicInteger>()

internal fun bumpGeneration(uploadId: String): Int =
    uploadGeneration.computeIfAbsent(uploadId) { AtomicInteger(0) }.incrementAndGet()

internal fun currentGeneration(uploadId: String): Int =
    uploadGeneration.computeIfAbsent(uploadId) { AtomicInteger(0) }.get()

internal fun DownloadService.getServiceNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_SILENT_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Служба загрузок Rupoop")
        .setContentText("Выполняются фоновые задачи загрузки...")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .setSilent(true)
        .build()
}

internal fun DownloadService.createNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java)
    val serviceChannel = NotificationChannel(CHANNEL_ID, "Download Service", NotificationManager.IMPORTANCE_LOW)
    manager?.createNotificationChannel(serviceChannel)
    val completeChannel = NotificationChannel(CHANNEL_COMPLETE_ID, "Download Complete", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Уведомления о завершении загрузки"
    }
    manager?.createNotificationChannel(completeChannel)
    val silentChannel = NotificationChannel(CHANNEL_SILENT_ID, "Download Service (Silent)", NotificationManager.IMPORTANCE_MIN).apply {
        description = "Тихие уведомления для скачивания в фоновом режиме"
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
        setSound(null, null)
    }
    manager?.createNotificationChannel(silentChannel)
}

internal fun DownloadService.createNotification(
    title: String,
    progress: Int,
    uploadId: String,
    isAudioTrack: Boolean,
    isPaused: Boolean,
    statusText: String? = null,
    showNotif: Boolean
): Notification {
    val notifId = getOrCreateNotifId(uploadId)

    // Intents use the existing "VIDEO_ID" extra for compatibility with service handlers
    val cancelIntent = Intent(this, DownloadService::class.java).apply {
        action = "CANCEL"
        putExtra("VIDEO_ID", uploadId)
    }
    val cancelPendingIntent = PendingIntent.getService(this, notifId + 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val channelId = if (showNotif) CHANNEL_ID else CHANNEL_SILENT_ID

    val contentIntent = PendingIntent.getActivity(
        this,
        notifId + 2,
        Intent(this, Class.forName("com.santiago43rus.rupoop.MainActivity")).apply {
            action = "OPEN_DOWNLOADS"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val displayTitle = if (isAudioTrack) "Загрузка аудио: $title" else "Загрузка: $title"
    val displayText = statusText?.let { if (progress > 0) "$it • $progress%" else it } ?: "Скачивание... $progress%"

    val builder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(displayTitle)
        .setContentText(displayText)
        .setOngoing(!isPaused && statusText == null)
        .setProgress(100, progress, false)
        .setPriority(if (showNotif) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
        .setContentIntent(contentIntent)
        .setSilent(true)
        .setOnlyAlertOnce(true)

    // Play/Pause action
    val playPauseAction = if (isPaused || (statusText != null && statusText.contains("Приостановлено"))) {
        val resumeIntent = Intent(this, DownloadService::class.java).apply {
            action = "RESUME"
            putExtra("VIDEO_ID", uploadId)
        }
        val resumePendingIntent = PendingIntent.getService(this, notifId + 3, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Action(android.R.drawable.ic_media_play, "Возобновить", resumePendingIntent)
    } else {
        val pauseIntent = Intent(this, DownloadService::class.java).apply {
            action = "PAUSE"
            putExtra("VIDEO_ID", uploadId)
        }
        val pausePendingIntent = PendingIntent.getService(this, notifId + 4, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Action(android.R.drawable.ic_media_pause, "Пауза", pausePendingIntent)
    }

    builder.addAction(playPauseAction)
    builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)

    return builder.build()
}

internal fun DownloadService.updateNotification(
    uploadId: String,
    title: String,
    progress: Int,
    isAudioTrack: Boolean,
    isPaused: Boolean,
    statusText: String? = null,
    generation: Int? = null
) {
    // If this update was tagged with a generation (i.e. it's a routine progress tick from the
    // download coroutine) and a newer generation has since been stamped for this upload (the
    // user tapped pause/resume/cancel), drop it. Otherwise a progress tick that was already
    // "in flight" when the user paused can land right after the pause notification and silently
    // overwrite it back to "Downloading", which is what made pause look like it needed two taps.
    if (generation != null && generation != currentGeneration(uploadId)) {
        return
    }

    val manager = getSystemService(NotificationManager::class.java) ?: return
    // Read the setting once per call (it used to be read twice: once here, once in
    // createNotification). We intentionally do NOT bail out when notifications are disabled:
    // this is a foreground service and Android requires it to always have an active
    // notification, so we still post one on the silent/minimal channel. This also keeps the
    // Pause/Resume/Cancel actions functional even with notifications toggled off.
    val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications

    val notifId = getOrCreateNotifId(uploadId)
    val notification = createNotification(title, progress, uploadId, isAudioTrack, isPaused, statusText, showNotif)

    // Manage foreground state per-upload: keep existing behavior but keyed by uploadId
    if (foregroundVideoId == null && !isPaused && statusText == null) {
        foregroundVideoId = uploadId
    }

    if (foregroundVideoId == uploadId) {
        // If this upload is the current foreground task, update the foreground notification explicitly.
        // Calling startForeground with the updated notification ensures immediate UI refresh on all Android versions.
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(notifId, notification)
            }
            isServiceForeground = true
            manager.notify(notifId, notification)
        } catch (e: Exception) {
            Log.e("DownloadService", "Error updating foreground notification", e)
            manager.notify(notifId, notification)
        }
    } else {
        // Non-foreground uploads: simple notify
        manager.notify(notifId, notification)
    }
}

internal fun DownloadService.showCompleteNotification(title: String, isAudioTrack: Boolean, uploadId: String) {
    val manager = getSystemService(NotificationManager::class.java)
    val notifId = getOrCreateNotifId(uploadId)

    val intent = Intent(this, Class.forName("com.santiago43rus.rupoop.MainActivity")).apply {
        action = "OPEN_DOWNLOADS"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val pendingIntent = PendingIntent.getActivity(this, notifId + 5, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val completeNotif = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Загрузка завершена")
        .setContentText(if (isAudioTrack) "Аудио: $title" else "Видео: $title")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    manager?.notify(notifId, completeNotif)

    // free mapping after a short time could be done; for now keep mapping until app restart to avoid collisions
}

internal fun DownloadService.stopForegroundSafely(flags: Int) {
    try {
        stopForeground(flags)
    } catch (e: Exception) {
        Log.e("DownloadService", "Error stopping foreground", e)
    }
}
