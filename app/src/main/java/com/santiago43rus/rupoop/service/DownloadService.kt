package com.santiago43rus.rupoop.service

import android.app.*
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.santiago43rus.rupoop.data.DownloadItem
import com.santiago43rus.rupoop.data.DownloadStatus
import com.santiago43rus.rupoop.data.DownloadTracker
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.util.isNetworkAvailable
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadService : Service() {
    internal val CHANNEL_ID = "download_channel"
    internal val CHANNEL_COMPLETE_ID = "download_complete_channel"
    internal val CHANNEL_SILENT_ID = "download_silent_channel_v2"
    private val serviceJob = SupervisorJob()
    internal val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    internal val client = OkHttpClient()
    internal lateinit var downloadTracker: DownloadTracker
    
    internal val activeTasks = java.util.concurrent.ConcurrentHashMap<String, DownloadTask>()

    companion object {
        const val ACTION_DOWNLOAD_COMPLETE = "com.santiago43rus.rupoop.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_ERROR = "com.santiago43rus.rupoop.DOWNLOAD_ERROR"
        const val ACTION_DOWNLOAD_CANCELLED = "com.santiago43rus.rupoop.DOWNLOAD_CANCELLED"
        const val ACTION_DOWNLOAD_PAUSED = "com.santiago43rus.rupoop.DOWNLOAD_PAUSED"
        const val ACTION_DOWNLOAD_RESUMED = "com.santiago43rus.rupoop.DOWNLOAD_RESUMED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        downloadTracker = DownloadTracker(applicationContext)

        try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) {
                    Log.d("CodecList", "Encoder: ${info.name}, types: ${info.supportedTypes.joinToString()}")
                }
            }
        } catch (e: Exception) {
            Log.e("CodecList", "Error logging codecs", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = getServiceNotification()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(9999, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(9999, notification)
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "Error starting foreground in onStartCommand", e)
        }

        val action = intent?.action
        Log.d("DownloadService", "onStartCommand action: $action")

        val actionVideoId = intent?.getStringExtra("VIDEO_ID") ?: ""
        
        when (action) {
            "CANCEL" -> {
                if (actionVideoId.isNotEmpty()) {
                    val task = activeTasks[actionVideoId]
                    if (task != null) {
                        task.job?.cancel()
                        task.outputFile?.let { if (it.exists()) it.delete() }
                        activeTasks.remove(actionVideoId)
                    } else {
                        val tracked = downloadTracker.downloads.value.find { it.videoId == actionVideoId }
                        tracked?.filePath?.let { path ->
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                    }
                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.CANCELLED)
                    sendBroadcast(Intent(ACTION_DOWNLOAD_CANCELLED).putExtra("video_id", actionVideoId))
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.cancel(actionVideoId.hashCode().let { if (it == 0) 1 else abs(it) })
                    checkAndStopService()
                }
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                if (actionVideoId.isNotEmpty()) {
                    val task = activeTasks[actionVideoId]
                    if (task != null) {
                        task.isPaused = true
                        task.job?.cancel()
                        activeTasks.remove(actionVideoId)
                        updateNotification(task.videoId, task.title, if (task.totalSegments > 0) (task.downloadedSegments * 100) / task.totalSegments else 0, task.isAudio, true, "Приостановлено")
                    } else {
                        val tracked = downloadTracker.downloads.value.find { it.videoId == actionVideoId }
                        if (tracked != null) {
                            val isAudioTrack = tracked.filePath?.endsWith(".m4a") == true || tracked.filePath?.endsWith(".mp3") == true
                            updateNotification(actionVideoId, tracked.title, tracked.progress, isAudioTrack, true, "Приостановлено")
                        }
                    }
                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.PAUSED)
                    sendBroadcast(Intent(ACTION_DOWNLOAD_PAUSED).putExtra("video_id", actionVideoId))
                    checkAndStopService()
                }
                return START_NOT_STICKY
            }
            "RESUME" -> {
                if (actionVideoId.isNotEmpty() && !activeTasks.containsKey(actionVideoId)) {
                    val tracked = downloadTracker.downloads.value.find { it.videoId == actionVideoId }
                    if (tracked != null) {
                        serviceScope.launch {
                            try {
                                val realId = actionVideoId.substringBefore("___")
                                val opt = RetrofitClient.api.getVideoOptions(realId)
                                val m3u8Url = opt.videoBalancer?.m3u8
                                if (m3u8Url != null) {
                                    val isAudioTrack = tracked.filePath?.endsWith(".m4a") == true || tracked.filePath?.endsWith(".mp3") == true
                                    val task = DownloadTask(this@DownloadService, actionVideoId, m3u8Url, tracked.title, "1080", isAudioTrack, tracked.thumbnailUrl)
                                    activeTasks[actionVideoId] = task
                                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.DOWNLOADING)
                                    updateNotification(actionVideoId, tracked.title, tracked.progress, isAudioTrack, false)
                                    sendBroadcast(Intent(ACTION_DOWNLOAD_RESUMED).putExtra("video_id", actionVideoId))
                                    task.start()
                                } else {
                                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.ERROR, "Не удалось получить ссылку на поток")
                                }
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Error resuming task", e)
                                downloadTracker.updateStatus(actionVideoId, DownloadStatus.ERROR, e.message)
                            }
                        }
                    }
                }
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra("VIDEO_URL") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("TITLE") ?: "Video"
        val videoId = intent.getStringExtra("VIDEO_ID") ?: ""
        val quality = intent.getStringExtra("QUALITY") ?: "1080"
        val isAudioTrack = intent.getBooleanExtra("IS_AUDIO", false)
        val thumbnailUrl = intent.getStringExtra("THUMBNAIL_URL")

        if (videoId.isNotEmpty() && !activeTasks.containsKey(videoId)) {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!musicDir.exists()) musicDir.mkdirs()
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val path = if (isAudioTrack) {
                File(musicDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${videoId}.m4a").absolutePath
            } else {
                File(moviesDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${videoId}.mp4").absolutePath
            }

            downloadTracker.addDownload(DownloadItem(
                videoId = videoId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                filePath = path,
                status = DownloadStatus.DOWNLOADING
            ))

            val task = DownloadTask(this, videoId, url, title, quality, isAudioTrack, thumbnailUrl)
            activeTasks[videoId] = task
            
            val notifId = videoId.hashCode().let { if (it == 0) 1 else abs(it) }
            val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
            if (showNotif) {
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(notifId, createNotification(title, 0, videoId, isAudioTrack, false))
            }
            
            task.start()
        }

        return START_NOT_STICKY
    }

    internal fun checkAndStopService() {
        if (activeTasks.isEmpty()) {
            stopForegroundSafely(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }



    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
