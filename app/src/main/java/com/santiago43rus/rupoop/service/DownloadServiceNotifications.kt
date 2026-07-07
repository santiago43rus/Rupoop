package com.santiago43rus.rupoop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs

internal fun DownloadService.getServiceNotification(): Notification {
    val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
    val channelId = if (showNotif) CHANNEL_ID else CHANNEL_SILENT_ID
    
    return NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Скачивание Rupoop")
        .setContentText("Выполняются задачи загрузки...")
        .setPriority(if (showNotif) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
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
    videoId: String,
    isAudioTrack: Boolean,
    isPaused: Boolean,
    statusText: String? = null
): Notification {
    val vidHash = videoId.hashCode().let { if (it == 0) 1 else abs(it) }

    val cancelIntent = Intent(this, DownloadService::class.java).apply {
        action = "CANCEL"
        putExtra("VIDEO_ID", videoId)
    }
    val cancelPendingIntent = PendingIntent.getService(this, vidHash + 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
    val channelId = if (showNotif) CHANNEL_ID else CHANNEL_SILENT_ID

    val contentIntent = PendingIntent.getActivity(
        this,
        vidHash,
        Intent(this, Class.forName("com.santiago43rus.rupoop.MainActivity")).apply {
            action = "OPEN_DOWNLOADS"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val displayTitle = if (isAudioTrack) "Загрузка аудио: $title" else "Загрузка: $title"
    val displayText = statusText ?: "Скачивание... $progress%"

    val builder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(displayTitle)
        .setContentText(displayText)
        .setOngoing(!isPaused && statusText == null)
        .setProgress(100, progress, false)
        .setPriority(if (showNotif) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
        .setContentIntent(contentIntent)
        .setSilent(true)

    // Action buttons: "Возобновить" (Resume) / "Пауза" (Pause) and "Отмена" (Cancel)
    val playPauseAction = if (isPaused || (statusText != null && statusText.contains("Приостановлено"))) {
        val resumeIntent = Intent(this, DownloadService::class.java).apply {
            action = "RESUME"
            putExtra("VIDEO_ID", videoId)
        }
        val resumePendingIntent = PendingIntent.getService(this, vidHash + 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Action(android.R.drawable.ic_media_play, "Возобновить", resumePendingIntent)
    } else {
        val pauseIntent = Intent(this, DownloadService::class.java).apply {
            action = "PAUSE"
            putExtra("VIDEO_ID", videoId)
        }
        val pausePendingIntent = PendingIntent.getService(this, vidHash + 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Action(android.R.drawable.ic_media_pause, "Пауза", pausePendingIntent)
    }
    
    builder.addAction(playPauseAction)
    builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)

    return builder.build()
}

internal fun DownloadService.updateNotification(
    videoId: String,
    title: String,
    progress: Int,
    isAudioTrack: Boolean,
    isPaused: Boolean,
    statusText: String? = null
) {
    val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
    if (!showNotif) return
    val manager = getSystemService(NotificationManager::class.java)
    val notifId = videoId.hashCode().let { if (it == 0) 1 else abs(it) }
    manager?.notify(notifId, createNotification(title, progress, videoId, isAudioTrack, isPaused, statusText))
}

internal fun DownloadService.showCompleteNotification(title: String, isAudioTrack: Boolean, videoId: String) {
    val manager = getSystemService(NotificationManager::class.java)
    val notifId = videoId.hashCode().let { if (it == 0) 1 else abs(it) }
    
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
}

internal fun DownloadService.stopForegroundSafely(flags: Int) {
    try {
        stopForeground(flags)
    } catch (e: Exception) {
        Log.e("DownloadService", "Error stopping foreground", e)
    }
}