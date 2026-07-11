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
    internal var foregroundVideoId: String? = null
    internal var isServiceForeground = false

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
        val action = intent?.action
        Log.d("DownloadService", "onStartCommand action: $action")

        val actionVideoId = intent?.getStringExtra("VIDEO_ID") ?: ""
        
        when (action) {
            "CANCEL" -> {
                if (actionVideoId.isNotEmpty()) {
                    bumpGeneration(actionVideoId)
                    val task = activeTasks[actionVideoId]
                    if (task != null) {
                        task.isCancelled = true
                        task.job?.cancel()
                        task.outputFile?.let { if (it.exists()) it.delete() }
                        activeTasks.remove(actionVideoId)
                    } else {
                        val tracked = downloadTracker.downloads.value.find { it.videoId == actionVideoId }
                        tracked?.filePath?.let { path ->
                            val f = File(path)
                            if (f.exists()) f.delete()
                            val partFile = File("$path.part")
                            if (partFile.exists()) partFile.delete()
                        }
                    }
                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.CANCELLED)
                    sendBroadcast(Intent(ACTION_DOWNLOAD_CANCELLED).putExtra("video_id", actionVideoId))
                    val manager = getSystemService(NotificationManager::class.java)
                    val notifId = getOrCreateNotifId(actionVideoId)
                    if (foregroundVideoId == actionVideoId) {
                        // This notification id is the one currently bound via startForeground().
                        // NotificationManager.cancel() alone cannot remove a notification still
                        // pinned as the service's foreground notification - the system just keeps
                        // it showing. Detach (and actually remove it) first. This is exactly like
                        // the PAUSE branch above, which already does this correctly; CANCEL was
                        // missing it, which is why cancelling the currently-foreground download
                        // (most reliably reproduced with audio, since the extra extraction step
                        // makes it far more likely to still be foreground when you tap cancel)
                        // left its notification stuck on screen.
                        foregroundVideoId = null
                        isServiceForeground = false
                        stopForegroundSafely(STOP_FOREGROUND_REMOVE)
                    }
                    manager?.cancel(notifId)
                    checkAndStopService()
                }
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                if (actionVideoId.isNotEmpty()) {
                    bumpGeneration(actionVideoId)
                    val task = activeTasks[actionVideoId]
                    if (task != null) {
                        task.isPaused = true
                        downloadTracker.updateStatus(actionVideoId, DownloadStatus.PAUSED)
                        sendBroadcast(Intent(ACTION_DOWNLOAD_PAUSED).putExtra("video_id", actionVideoId))
                        if (foregroundVideoId == actionVideoId) {
                            foregroundVideoId = null
                            isServiceForeground = false
                            stopForegroundSafely(STOP_FOREGROUND_DETACH)
                        }
                        updateNotification(task.videoId, task.title, if (task.totalSegments > 0) (task.downloadedSegments * 100) / task.totalSegments else 0, task.isAudio, true, "Приостановлено")
                        task.job?.cancel()
                        activeTasks.remove(actionVideoId)
                        Log.d("DownloadService", "Paused active task: $actionVideoId")
                        checkAndStopService()
                    } else {
                        val tracked = downloadTracker.downloads.value.find { it.videoId == actionVideoId }
                        if (tracked != null) {
                            val isAudioTrack = tracked.filePath?.endsWith(".m4a") == true || tracked.filePath?.endsWith(".mp3") == true
                            if (foregroundVideoId == actionVideoId) {
                                foregroundVideoId = null
                                isServiceForeground = false
                                stopForegroundSafely(STOP_FOREGROUND_DETACH)
                            }
                            updateNotification(actionVideoId, tracked.title, tracked.progress, isAudioTrack, true, "Приостановлено")
                            downloadTracker.updateStatus(actionVideoId, DownloadStatus.PAUSED)
                            sendBroadcast(Intent(ACTION_DOWNLOAD_PAUSED).putExtra("video_id", actionVideoId))
                        }
                    }
                }
                return START_NOT_STICKY
            }
            "RESUME" -> {
                if (actionVideoId.isNotEmpty()) {
                    bumpGeneration(actionVideoId)
                    val activeTask = activeTasks[actionVideoId]
                    if (activeTask != null && activeTask.isPaused) {
                        activeTask.isPaused = false
                        downloadTracker.updateStatus(actionVideoId, DownloadStatus.DOWNLOADING)
                        updateNotification(actionVideoId, activeTask.title, activeTask.downloadedSegments.coerceAtLeast(0) * 100 / (activeTask.totalSegments.takeIf { it > 0 } ?: 1), activeTask.isAudio, false)
                        sendBroadcast(Intent(ACTION_DOWNLOAD_RESUMED).putExtra("video_id", actionVideoId))
                        Log.d("DownloadService", "Resuming paused active task: $actionVideoId")
                        activeTask.start()
                    } else if (!activeTasks.containsKey(actionVideoId)) {
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
                }
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra("VIDEO_URL") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("TITLE") ?: "Video"
        val requestedVideoId = intent.getStringExtra("VIDEO_ID") ?: ""
        val quality = intent.getStringExtra("QUALITY") ?: "1080"
        val isAudioTrack = intent.getBooleanExtra("IS_AUDIO", false)
        val thumbnailUrl = intent.getStringExtra("THUMBNAIL_URL")

        if (requestedVideoId.isNotEmpty()) {
            // Every download request gets its own unique upload id, built from the real video id
            // plus a type + random/time suffix ("realId___video_172..._483"). This is what
            // activeTasks, the notification id map, the tracker, and the output filename are all
            // keyed on from here down. Without this, downloading the same video twice - or
            // downloading it as both video and audio - reused the exact same id and filename and
            // one download would silently clobber the other (blocked from even starting, or
            // overwriting the other's file/notification). RESUME already expected this
            // "realId___..." shape (see actionVideoId.substringBefore("___") above), so this just
            // makes the id actually get created that way.
            val uploadId = generateUploadId(requestedVideoId, isAudioTrack)

            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!musicDir.exists()) musicDir.mkdirs()
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val path = if (isAudioTrack) {
                File(musicDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${uploadId}.m4a").absolutePath
            } else {
                File(moviesDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${uploadId}.mp4").absolutePath
            }

            downloadTracker.addDownload(DownloadItem(
                videoId = uploadId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                filePath = path,
                status = DownloadStatus.DOWNLOADING
            ))

            val task = DownloadTask(this, uploadId, url, title, quality, isAudioTrack, thumbnailUrl)
            activeTasks[uploadId] = task

            val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
            if (showNotif) {
                updateNotification(uploadId, title, 0, isAudioTrack, false)
            }

            task.start()
        }

        return START_NOT_STICKY
    }

    /**
     * Builds a unique upload id for a new download: "<realVideoId>___<type>_<timestamp>_<rand>".
     * The "___" separator matches the convention already relied on by the RESUME handler
     * (actionVideoId.substringBefore("___")) to recover the real video id for re-fetching the
     * stream URL after a process death.
     */
    private fun generateUploadId(realVideoId: String, isAudio: Boolean): String {
        val type = if (isAudio) "audio" else "video"
        val suffix = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        return "${realVideoId}___${type}_${suffix}"
    }

    internal fun checkAndStopService() {
        val nextTask = activeTasks.values.firstOrNull { !it.isPaused }

        if (nextTask == null) {
            foregroundVideoId = null
            isServiceForeground = false
            stopForegroundSafely(STOP_FOREGROUND_DETACH)
            if (activeTasks.isEmpty()) {
                stopSelf()
            }
            return
        }

        if (foregroundVideoId != nextTask.videoId) {
            foregroundVideoId = nextTask.videoId
            isServiceForeground = false
            updateNotification(
                nextTask.videoId,
                nextTask.title,
                if (nextTask.totalSegments > 0) (nextTask.downloadedSegments * 100) / nextTask.totalSegments else 0,
                nextTask.isAudio,
                false
            )
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
