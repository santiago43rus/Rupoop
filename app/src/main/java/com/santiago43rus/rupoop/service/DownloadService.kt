package com.santiago43rus.rupoop.service

import android.app.*
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.santiago43rus.rupoop.data.DownloadItem
import com.santiago43rus.rupoop.data.DownloadStatus
import com.santiago43rus.rupoop.data.DownloadTracker
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
    private val CHANNEL_ID = "download_channel"
    private val CHANNEL_COMPLETE_ID = "download_complete_channel"
    private val CHANNEL_SILENT_ID = "download_silent_channel_v2"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null
    
    private val client = OkHttpClient()
    private lateinit var downloadTracker: DownloadTracker
    
    private var currentTitle: String = ""
    private var currentUrl: String = ""
    private var currentVideoId: String = ""
    private var targetQuality: String = "1080"
    private var isAudio = false
    private var isPaused = false
    private var downloadedSegments = 0
    private var totalSegments = 0
    private var outputFile: File? = null

    private fun getNotificationId(): Int {
        return if (currentVideoId.isNotEmpty()) currentVideoId.hashCode().let { if (it == 0) 1 else abs(it) } else 1
    }

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

        // Log all available encoders to understand what formats are natively supported on this device
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

        // Read videoId from action intents
        val actionVideoId = intent?.getStringExtra("VIDEO_ID")
        
        when (action) {
            "CANCEL" -> {
                // Must call startForeground before processing if service not yet foreground
                if (currentVideoId.isEmpty() && actionVideoId != null) {
                    currentVideoId = actionVideoId
                }
                startForegroundSafely(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0))
                if (actionVideoId == null || actionVideoId == currentVideoId) {
                    cancelDownload()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                startForegroundSafely(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0))
                if (actionVideoId == null || actionVideoId == currentVideoId) {
                    pauseDownload()
                }
                return START_NOT_STICKY
            }
            "RESUME" -> {
                startForegroundSafely(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0))
                if (actionVideoId == null || actionVideoId == currentVideoId) {
                    resumeDownload()
                }
                return START_NOT_STICKY
            }
        }

        currentUrl = intent?.getStringExtra("VIDEO_URL") ?: return START_NOT_STICKY
        currentTitle = intent.getStringExtra("TITLE") ?: "Video"
        currentVideoId = intent.getStringExtra("VIDEO_ID") ?: ""
        targetQuality = intent.getStringExtra("QUALITY") ?: "1080"
        isAudio = intent.getBooleanExtra("IS_AUDIO", false)
        downloadedSegments = 0
        totalSegments = 0
        isPaused = false

        // Check network before starting
        if (!isNetworkAvailable(this)) {
            downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, "Нет подключения к интернету")
            sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", currentTitle).putExtra("error", "Нет интернета"))
            stopSelf()
            return START_NOT_STICKY
        }

        // Track this download
        downloadTracker.addDownload(DownloadItem(
            videoId = currentVideoId,
            title = currentTitle,
            thumbnailUrl = intent.getStringExtra("THUMBNAIL_URL"),
            filePath = if (isAudio) {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                File(musicDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}.m4a").absolutePath
            } else null,
            status = DownloadStatus.DOWNLOADING
        ))

        startForegroundSafely(getNotificationId(), createNotification(currentTitle, 0))
        startDownload()

        return START_NOT_STICKY
    }

    private fun startDownload() {
        isPaused = false
        downloadJob = serviceScope.launch {
            try {
                // Re-check network
                if (!isNetworkAvailable(this@DownloadService)) {
                    downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, "Нет подключения к интернету")
                    sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", currentTitle).putExtra("error", "Нет интернета"))
                    stopForegroundSafely(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return@launch
                }

                val trackedItem = downloadTracker.downloads.value.find { it.videoId == currentVideoId }
                if (trackedItem != null && (trackedItem.filePath?.endsWith(".m4a") == true || trackedItem.filePath?.endsWith(".mp3") == true)) {
                    isAudio = true
                }

                val mediaPlaylistUrl = resolveMediaPlaylist(currentUrl, if (isAudio) "LOWEST" else targetQuality)
                val segments = fetchSegments(mediaPlaylistUrl)
                totalSegments = segments.size

                val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                if (!moviesDir.exists()) moviesDir.mkdirs()
                if (!musicDir.exists()) musicDir.mkdirs()

                val finalFile = if (isAudio) {
                    File(musicDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}.m4a")
                } else {
                    File(moviesDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}.mp4")
                }

                val downloadFile = if (isAudio) {
                    File(applicationContext.cacheDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_temp.mp4")
                } else {
                    finalFile
                }
                outputFile = downloadFile

                val fos = FileOutputStream(downloadFile, downloadedSegments > 0)

                for (i in downloadedSegments until totalSegments) {
                    if (!isActive || isPaused) break

                    // Check network periodically
                    if (i % 10 == 0 && !isNetworkAvailable(this@DownloadService)) {
                        fos.close()
                        downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, "Соединение потеряно")
                        sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", currentTitle).putExtra("error", "Соединение потеряно"))
                        if (com.santiago43rus.rupoop.data.SettingsManager(this@DownloadService).showDownloadNotifications) {
                            updateNotification(currentTitle, 0, false, "Ошибка: Нет интернета")
                        }
                        stopForegroundSafely(STOP_FOREGROUND_DETACH)
                        stopSelf()
                        return@launch
                    }

                    val segmentUrl = segments[i]
                    downloadSegment(segmentUrl, fos)

                    downloadedSegments++
                    val progress = (downloadedSegments * 100) / totalSegments
                    updateNotification(currentTitle, progress, false)
                    downloadTracker.updateProgress(currentVideoId, progress)
                }

                fos.close()
                
                if (downloadedSegments == totalSegments) {
                    if (isAudio) {
                        try {
                            extractAudioTrack(downloadFile, finalFile)
                        } finally {
                            if (downloadFile.exists()) {
                                downloadFile.delete()
                            }
                        }
                        outputFile = finalFile
                    }
                    MediaScannerConnection.scanFile(this@DownloadService, arrayOf(outputFile!!.absolutePath), arrayOf(if (isAudio) "audio/mp4" else "video/mp4")) { path, uri ->
                        Log.d("DownloadService", "Scanned $path: uri=$uri")
                    }
                    downloadTracker.updateStatus(currentVideoId, DownloadStatus.COMPLETED, filePath = outputFile!!.absolutePath)
                    sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).putExtra("title", currentTitle))
                    onDownloadComplete()
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e("RupoopDownload", "Download error", e)
                downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, e.message)
                sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", currentTitle).putExtra("error", e.message))
                if (com.santiago43rus.rupoop.data.SettingsManager(this@DownloadService).showDownloadNotifications) {
                    updateNotification(currentTitle, 0, false, "Ошибка: ${e.message}")
                }
                stopForegroundSafely(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun extractAudioTrack(inputFile: File, outputFile: File) {
        suspendCancellableCoroutine { continuation ->
            try {
                val mediaItem = MediaItem.fromUri(inputFile.toURI().toString())

                // Настраиваем элемент: говорим, что нам нужно вырезать видео
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                // Настраиваем Трансформер
                val transformer = Transformer.Builder(applicationContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC) // Жестко задаем AAC (.m4a)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Log.d("DownloadService", "Audio extraction completed successfully via Media3")
                            continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e("DownloadService", "Media3 Export error", exportException)
                            // В случае ошибки пробуем просто скопировать файл, чтобы не оставлять пользователя ни с чем
                            try {
                                inputFile.copyTo(outputFile, overwrite = true)
                                continuation.resume(Unit)
                            } catch (e: Exception) {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()

                // Запускаем процесс конвертации
                transformer.start(editedMediaItem, outputFile.absolutePath)

                // Если корутину отменят (например, пользователь нажал Отмена), останавливаем трансформер
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Failed to setup Media3 Transformer", e)
                // Фолбэк на прямое копирование, если Трансформер вообще не смог завестись
                inputFile.copyTo(outputFile, overwrite = true)
                continuation.resume(Unit)
            }
        }
    }

    private fun onDownloadComplete() {
        // Remove the progress notification
        stopForegroundSafely(STOP_FOREGROUND_REMOVE)

        if (com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications) {
            // Post a separate completion notification with a different ID
            val manager = getSystemService(NotificationManager::class.java)
            val completeNotifId = getNotificationId() + 10000

            val builder = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Загрузка завершена")
                .setContentText(currentTitle)
                .setAutoCancel(true)
                .setOngoing(false)

            // Create intent to open app's Downloads screen
            try {
                val playIntent = Intent(this, Class.forName("com.santiago43rus.rupoop.MainActivity")).apply {
                    action = "OPEN_DOWNLOADS"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val viewPendingIntent = PendingIntent.getActivity(
                    this,
                    completeNotifId,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(viewPendingIntent)
            } catch (e: Exception) {
                Log.e("DownloadService", "Error creating play intent for completion notification", e)
            }

            manager?.notify(completeNotifId, builder.build())
        }
        stopSelf()
    }

    private suspend fun resolveMediaPlaylist(url: String, quality: String): String {
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val body = response.body?.string() ?: throw Exception("Empty playlist")

        if (body.contains("#EXT-X-STREAM-INF")) {
            val lines = body.lines()
            var bestUrl: String? = null
            var maxRes = 0
            var minRes = Int.MAX_VALUE
            var worstUrl: String? = null

            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val info = lines[i]
                    val resMatch = "RESOLUTION=(\\d+)x(\\d+)".toRegex().find(info)
                    val h = resMatch?.groupValues?.get(2)?.toInt() ?: 0

                    val streamUrl = lines.getOrNull(i + 1)?.let {
                        if (it.startsWith("http")) it
                        else url.substringBeforeLast("/") + "/" + it
                    }

                    if (streamUrl != null) {
                        if (quality != "LOWEST" && quality != "LOW_RESOLUTION" && quality != "LOWEST_RESOLUTION" && h.toString() == quality) return streamUrl
                        if (h > maxRes) {
                            maxRes = h
                            bestUrl = streamUrl
                        }
                        if (h < minRes && h > 0) {
                            minRes = h
                            worstUrl = streamUrl
                        }
                    }
                }
            }
            if (quality == "LOWEST" || isAudio) {
                return worstUrl ?: bestUrl ?: url
            }
            return bestUrl ?: url
        }
        return url
    }

    private suspend fun fetchSegments(url: String): List<String> {
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val body = response.body?.string() ?: throw Exception("Empty media playlist")

        val baseUrl = url.substringBeforeLast("/")
        return body.lines().filter { it.isNotEmpty() && !it.startsWith("#") }.map {
            if (it.startsWith("http")) it else "$baseUrl/$it"
        }
    }

    private suspend fun downloadSegment(url: String, fos: FileOutputStream) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: return@withContext
            fos.write(bytes)
        }
    }

    private fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
        downloadTracker.updateStatus(currentVideoId, DownloadStatus.PAUSED)
        sendBroadcast(Intent(ACTION_DOWNLOAD_PAUSED).putExtra("title", currentTitle))
        updateNotification(currentTitle, (downloadedSegments * 100) / totalSegments.coerceAtLeast(1), false, "Приостановлено")
    }

    private fun resumeDownload() {
        if (isPaused) {
            if (!isNetworkAvailable(this)) {
                downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, "Нет подключения к интернету")
                return
            }
            sendBroadcast(Intent(ACTION_DOWNLOAD_RESUMED).putExtra("title", currentTitle))
            startDownload()
        }
    }

    private fun cancelDownload() {
        Log.d("DownloadService", "Cancelling download...")
        downloadJob?.cancel()

        outputFile?.let {
            if (it.exists()) it.delete()
        }

        downloadTracker.updateStatus(currentVideoId, DownloadStatus.CANCELLED)
        sendBroadcast(Intent(ACTION_DOWNLOAD_CANCELLED).putExtra("title", currentTitle))

        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(getNotificationId())

        stopForegroundSafely(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
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

    private fun createNotification(title: String, progress: Int, isComplete: Boolean = false, statusText: String? = null): Notification {
        val vidHash = currentVideoId.hashCode()

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL"
            putExtra("VIDEO_ID", currentVideoId)
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

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(statusText ?: (if (isAudio) "Загрузка аудио: $title" else "Загрузка: $title"))
            .setOngoing(statusText == null)
            .setProgress(100, progress, false)
            .setPriority(if (showNotif) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .setSilent(true)

        if (statusText == null) {
            val pauseAction = if (isPaused) {
                val resumeIntent = Intent(this, DownloadService::class.java).apply {
                    action = "RESUME"
                    putExtra("VIDEO_ID", currentVideoId)
                }
                val resumePendingIntent = PendingIntent.getService(this, vidHash + 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                NotificationCompat.Action(android.R.drawable.ic_media_play, "Возобновить", resumePendingIntent)
            } else {
                val pauseIntent = Intent(this, DownloadService::class.java).apply {
                    action = "PAUSE"
                    putExtra("VIDEO_ID", currentVideoId)
                }
                val pausePendingIntent = PendingIntent.getService(this, vidHash + 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                NotificationCompat.Action(android.R.drawable.ic_media_pause, "Пауза", pausePendingIntent)
            }
            builder.addAction(pauseAction)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)
        }


        return builder.build()
    }

    private fun updateNotification(title: String, progress: Int, isComplete: Boolean, statusText: String? = null) {
        val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
        if (!showNotif) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(getNotificationId(), createNotification(title, progress, isComplete, statusText))
    }

    private fun startForegroundSafely(id: Int, notification: Notification) {
        if (com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications) {
            try {
                startForeground(id, notification)
            } catch (e: Exception) {
                Log.e("DownloadService", "Error starting foreground", e)
            }
        }
    }

    private fun stopForegroundSafely(flags: Int) {
        if (com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications) {
            try {
                stopForeground(flags)
            } catch (e: Exception) {
                Log.e("DownloadService", "Error stopping foreground", e)
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
