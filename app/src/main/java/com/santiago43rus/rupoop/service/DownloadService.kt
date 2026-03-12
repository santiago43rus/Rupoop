package com.santiago43rus.rupoop.service

import android.app.*
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadService : Service() {
    private val CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null
    
    private val client = OkHttpClient()
    
    private var currentTitle: String = ""
    private var currentUrl: String = ""
    private var targetQuality: String = "1080"
    private var isPaused = false
    private var downloadedSegments = 0
    private var totalSegments = 0
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("DownloadService", "onStartCommand action: $action")
        
        when (action) {
            "CANCEL" -> {
                cancelDownload()
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                pauseDownload()
                return START_NOT_STICKY
            }
            "RESUME" -> {
                resumeDownload()
                return START_NOT_STICKY
            }
        }

        currentUrl = intent?.getStringExtra("VIDEO_URL") ?: return START_NOT_STICKY
        currentTitle = intent.getStringExtra("TITLE") ?: "Video"
        targetQuality = intent.getStringExtra("QUALITY") ?: "1080"

        startForeground(NOTIFICATION_ID, createNotification(currentTitle, 0))
        startDownload()
        
        return START_NOT_STICKY
    }

    private fun startDownload() {
        isPaused = false
        downloadJob = serviceScope.launch {
            try {
                val mediaPlaylistUrl = resolveMediaPlaylist(currentUrl, targetQuality)
                val segments = fetchSegments(mediaPlaylistUrl)
                totalSegments = segments.size

                val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                if (!moviesDir.exists()) moviesDir.mkdirs()

                outputFile = File(moviesDir, "${currentTitle.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}.mp4")
                
                val fos = FileOutputStream(outputFile!!, downloadedSegments > 0)
                
                for (i in downloadedSegments until totalSegments) {
                    if (!isActive || isPaused) break
                    
                    val segmentUrl = segments[i]
                    downloadSegment(segmentUrl, fos)
                    
                    downloadedSegments++
                    val progress = (downloadedSegments * 100) / totalSegments
                    updateNotification(currentTitle, progress, false)
                }
                
                fos.close()
                
                if (downloadedSegments == totalSegments) {
                    MediaScannerConnection.scanFile(this@DownloadService, arrayOf(outputFile!!.absolutePath), arrayOf("video/mp4")) { path, uri ->
                        Log.d("DownloadService", "Scanned $path: uri=$uri")
                    }
                    onDownloadComplete()
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e("RupoopDownload", "Download error", e)
                updateNotification(currentTitle, 0, false, "Ошибка: ${e.message}")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun onDownloadComplete() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(currentTitle, 100, true))
        stopForeground(STOP_FOREGROUND_DETACH)
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
        updateNotification(currentTitle, (downloadedSegments * 100) / totalSegments.coerceAtLeast(1), false, "Приостановлено")
    }

    private fun resumeDownload() {
        if (isPaused) {
            startDownload()
        }
    }

    private fun cancelDownload() {
        Log.d("DownloadService", "Cancelling download...")
        downloadJob?.cancel()
        serviceJob.cancel()
        
        // Delete partial file
        outputFile?.let {
            if (it.exists()) it.delete()
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Download Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, progress: Int, isComplete: Boolean = false, statusText: String? = null): Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).apply { action = "CANCEL" }
        // Use a different request code and FLAG_CANCEL_CURRENT to ensure it's delivered promptly
        val cancelPendingIntent = PendingIntent.getService(this, System.currentTimeMillis().toInt(), cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (isComplete) "Загрузка завершена: $title" else statusText ?: "Загрузка: $title")
            .setOngoing(!isComplete && statusText == null)
            .setProgress(100, progress, false)
            .setSilent(true) // Reduce noise during updates

        if (!isComplete && statusText == null) {
            val pauseAction = if (isPaused) {
                val resumeIntent = Intent(this, DownloadService::class.java).apply { action = "RESUME" }
                val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                NotificationCompat.Action(android.R.drawable.ic_media_play, "Возобновить", resumePendingIntent)
            } else {
                val pauseIntent = Intent(this, DownloadService::class.java).apply { action = "PAUSE" }
                val pausePendingIntent = PendingIntent.getService(this, 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                NotificationCompat.Action(android.R.drawable.ic_media_pause, "Пауза", pausePendingIntent)
            }
            builder.addAction(pauseAction)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)
        }

        if (isComplete && outputFile != null) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", outputFile!!)
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val viewPendingIntent = PendingIntent.getActivity(this, 4, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                builder.setContentIntent(viewPendingIntent)
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setProgress(0, 0, false)
                builder.setAutoCancel(true)
                builder.setOngoing(false)
                builder.clearActions()
            } catch (e: Exception) {
                Log.e("DownloadService", "Error creating completion notification", e)
            }
        }

        return builder.build()
    }

    private fun updateNotification(title: String, progress: Int, isComplete: Boolean, statusText: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(title, progress, isComplete, statusText))
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
