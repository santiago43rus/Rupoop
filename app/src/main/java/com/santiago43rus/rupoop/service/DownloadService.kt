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
    private val CHANNEL_ID = "download_channel"
    private val CHANNEL_COMPLETE_ID = "download_complete_channel"
    private val CHANNEL_SILENT_ID = "download_silent_channel_v2"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private val client = OkHttpClient()
    private lateinit var downloadTracker: DownloadTracker
    
    private val activeTasks = java.util.concurrent.ConcurrentHashMap<String, DownloadTask>()

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
                                    val task = DownloadTask(actionVideoId, m3u8Url, tracked.title, "1080", isAudioTrack, tracked.thumbnailUrl)
                                    activeTasks[actionVideoId] = task
                                    downloadTracker.updateStatus(actionVideoId, DownloadStatus.DOWNLOADING)
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

            val task = DownloadTask(videoId, url, title, quality, isAudioTrack, thumbnailUrl)
            activeTasks[videoId] = task
            
            val notifId = videoId.hashCode().let { if (it == 0) 1 else abs(it) }
            val showNotif = com.santiago43rus.rupoop.data.SettingsManager(this).showDownloadNotifications
            if (showNotif) {
                startForegroundSafely(notifId, createNotification(title, 0, videoId, isAudioTrack, false))
            }
            
            task.start()
        }

        return START_NOT_STICKY
    }

    private fun checkAndStopService() {
        if (activeTasks.isEmpty()) {
            stopForegroundSafely(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private inner class DownloadTask(
        val videoId: String,
        val url: String,
        val title: String,
        val quality: String,
        var isAudio: Boolean,
        val thumbnailUrl: String?
    ) {
        var job: Job? = null
        var isPaused = false
        var downloadedSegments = 0
        var totalSegments = 0
        var outputFile: File? = null

        fun start() {
            isPaused = false
            job = serviceScope.launch {
                try {
                    if (!isNetworkAvailable(this@DownloadService)) {
                        downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, "Нет подключения к интернету")
                        sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", "Нет интернета"))
                        updateNotification(videoId, title, 0, isAudio, false, "Ошибка: Нет интернета")
                        activeTasks.remove(videoId)
                        checkAndStopService()
                        return@launch
                    }

                    val mediaPlaylistUrl = resolveMediaPlaylist(url, if (isAudio) "LOWEST" else quality)
                    val segments = fetchSegments(mediaPlaylistUrl)
                    totalSegments = segments.size

                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    if (!musicDir.exists()) musicDir.mkdirs()
                    if (!moviesDir.exists()) moviesDir.mkdirs()

                    val finalFile = if (isAudio) {
                        File(musicDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${videoId}.m4a")
                    } else {
                        File(moviesDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_rupoop_${videoId}.mp4")
                    }

                    val tempFile = if (isAudio) {
                        File(applicationContext.cacheDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_temp_${videoId}.mp4")
                    } else {
                        finalFile
                    }
                    outputFile = tempFile

                    val tracked = downloadTracker.downloads.value.find { it.videoId == videoId }
                    val startSegment = if (tracked != null && tracked.status == DownloadStatus.PAUSED && tempFile.exists()) {
                        val est = (tracked.progress * totalSegments) / 100
                        est.coerceIn(0, totalSegments)
                    } else {
                        0
                    }
                    downloadedSegments = startSegment

                    val fos = FileOutputStream(tempFile, downloadedSegments > 0)

                    for (i in downloadedSegments until totalSegments) {
                        if (!isActive || isPaused) break

                        if (i % 10 == 0 && !isNetworkAvailable(this@DownloadService)) {
                            fos.close()
                            downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, "Соединение потеряно")
                            sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", "Соединение потеряно"))
                            updateNotification(videoId, title, 0, isAudio, false, "Ошибка: Соединение потеряно")
                            activeTasks.remove(videoId)
                            checkAndStopService()
                            return@launch
                        }

                        val segmentUrl = segments[i]
                        downloadSegment(segmentUrl, fos)

                        downloadedSegments++
                        val progress = if (totalSegments > 0) (downloadedSegments * 100) / totalSegments else 0
                        updateNotification(videoId, title, progress, isAudio, false)
                        downloadTracker.updateProgress(videoId, progress)
                    }

                    fos.close()

                    if (downloadedSegments == totalSegments) {
                        if (isAudio) {
                            try {
                                extractAudioTrack(tempFile, finalFile)
                            } finally {
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                            }
                            outputFile = finalFile
                        }
                        MediaScannerConnection.scanFile(this@DownloadService, arrayOf(outputFile!!.absolutePath), arrayOf(if (isAudio) "audio/mp4" else "video/mp4")) { path, uri ->
                            Log.d("DownloadService", "Scanned $path: uri=$uri")
                        }
                        downloadTracker.updateStatus(videoId, DownloadStatus.COMPLETED, filePath = outputFile!!.absolutePath)
                        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).putExtra("title", title))
                        
                        showCompleteNotification(title, isAudio, videoId)
                    }
                    activeTasks.remove(videoId)
                    checkAndStopService()
                } catch (e: Exception) {
                    if (e is CancellationException) return@launch
                    Log.e("RupoopDownload", "Download error in task $videoId", e)
                    downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, e.message)
                    sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", e.message))
                    updateNotification(videoId, title, 0, isAudio, false, "Ошибка: ${e.message}")
                    activeTasks.remove(videoId)
                    checkAndStopService()
                }
            }
        }
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
                        if (quality != "LOWEST" && h.toString() == quality) return streamUrl
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
            if (quality == "LOWEST") {
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

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun extractAudioTrack(inputFile: File, outputFile: File) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val mediaItem = MediaItem.fromUri(inputFile.toURI().toString())

                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .build()

                    val transformer = Transformer.Builder(applicationContext)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
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
                                try {
                                    inputFile.copyTo(outputFile, overwrite = true)
                                    continuation.resume(Unit)
                                } catch (e: Exception) {
                                    continuation.resumeWithException(exportException)
                                }
                            }
                        })
                        .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
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

    private fun createNotification(
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

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(statusText ?: (if (isAudioTrack) "Загрузка аудио: $title" else "Загрузка: $title"))
            .setOngoing(statusText == null)
            .setProgress(100, progress, false)
            .setPriority(if (showNotif) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .setSilent(true)

        if (statusText == null) {
            val pauseAction = if (isPaused) {
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
            builder.addAction(pauseAction)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(
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

    private fun showCompleteNotification(title: String, isAudioTrack: Boolean, videoId: String) {
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
