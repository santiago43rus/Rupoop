package com.santiago43rus.rupoop.service

import android.app.*
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
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

class DownloadService : Service() {
    private val CHANNEL_ID = "download_channel"
    private val CHANNEL_COMPLETE_ID = "download_complete_channel"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null
    
    private val client = OkHttpClient()
    private lateinit var downloadTracker: DownloadTracker
    
    private var currentTitle: String = ""
    private var currentUrl: String = ""
    private var currentVideoId: String = ""
    private var targetQuality: String = "1080"
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
                try { startForeground(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0)) } catch (_: Exception) {}
                if (actionVideoId == null || actionVideoId == currentVideoId) {
                    cancelDownload()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                try { startForeground(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0)) } catch (_: Exception) {}
                if (actionVideoId == null || actionVideoId == currentVideoId) {
                    pauseDownload()
                }
                return START_NOT_STICKY
            }
            "RESUME" -> {
                try { startForeground(getNotificationId(), createNotification(currentTitle.ifEmpty { "Загрузка" }, 0)) } catch (_: Exception) {}
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
            status = DownloadStatus.DOWNLOADING
        ))

        startForeground(getNotificationId(), createNotification(currentTitle, 0))
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
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return@launch
                }

                val mediaPlaylistUrl = resolveMediaPlaylist(currentUrl, targetQuality)
                val segments = fetchSegments(mediaPlaylistUrl)
                totalSegments = segments.size

                val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                if (!moviesDir.exists()) moviesDir.mkdirs()

                outputFile = File(moviesDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}.mp4")
                
                val fos = FileOutputStream(outputFile!!, downloadedSegments > 0)
                
                for (i in downloadedSegments until totalSegments) {
                    if (!isActive || isPaused) break

                    // Check network periodically
                    if (i % 10 == 0 && !isNetworkAvailable(this@DownloadService)) {
                        fos.close()
                        downloadTracker.updateStatus(currentVideoId, DownloadStatus.ERROR, "Соединение потеряно")
                        sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).putExtra("title", currentTitle).putExtra("error", "Соединение потеряно"))
                        updateNotification(currentTitle, 0, false, "Ошибка: Нет интернета")
                        stopForeground(STOP_FOREGROUND_DETACH)
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
                    MediaScannerConnection.scanFile(this@DownloadService, arrayOf(outputFile!!.absolutePath), arrayOf("video/mp4")) { path, uri ->
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
                updateNotification(currentTitle, 0, false, "Ошибка: ${e.message}")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun onDownloadComplete() {
        // Remove the progress notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Post a separate completion notification with a different ID
        val manager = getSystemService(NotificationManager::class.java)
        val completeNotifId = getNotificationId() + 10000

        val builder = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Загрузка завершена")
            .setContentText(currentTitle)
            .setAutoCancel(true)
            .setOngoing(false)

        // Create intent to open app and play the local file
        try {
            val playIntent = Intent(this, Class.forName("com.santiago43rus.rupoop.MainActivity")).apply {
                action = "PLAY_LOCAL_FILE"
                putExtra("FILE_PATH", outputFile?.absolutePath)
                putExtra("TITLE", currentTitle)
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
                        if (h.toString() == quality) return streamUrl
                        if (h > maxRes) {
                            maxRes = h
                            bestUrl = streamUrl
                        }
                    }
                }
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
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Download Service", NotificationManager.IMPORTANCE_LOW)
            manager?.createNotificationChannel(serviceChannel)
            val completeChannel = NotificationChannel(CHANNEL_COMPLETE_ID, "Download Complete", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Уведомления о завершении загрузки"
            }
            manager?.createNotificationChannel(completeChannel)
        }
    }

    private fun createNotification(title: String, progress: Int, isComplete: Boolean = false, statusText: String? = null): Notification {
        val vidHash = currentVideoId.hashCode()
        
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL"
            putExtra("VIDEO_ID", currentVideoId)
        }
        val cancelPendingIntent = PendingIntent.getService(this, vidHash + 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(statusText ?: "Загрузка: $title")
            .setOngoing(statusText == null)
            .setProgress(100, progress, false)
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
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(getNotificationId(), createNotification(title, progress, isComplete, statusText))
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
