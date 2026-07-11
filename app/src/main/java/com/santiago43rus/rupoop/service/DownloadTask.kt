package com.santiago43rus.rupoop.service

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.santiago43rus.rupoop.data.DownloadStatus
import com.santiago43rus.rupoop.util.isNetworkAvailable
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadTask(
    val service: DownloadService,
    val videoId: String,
    val url: String,
    val title: String,
    val quality: String,
    var isAudio: Boolean,
    val thumbnailUrl: String?
) {
    var job: Job? = null
    var isPaused = false
    var isCancelled = false
    var downloadedSegments = 0
    var totalSegments = 0
    var outputFile: File? = null

    // The generation this run of the task belongs to. Every explicit pause/resume/cancel bumps
    // the shared generation counter for this upload id; progress notifications tagged with an
    // older generation get dropped instead of overwriting more recent state (see
    // DownloadServiceNotifications.updateNotification).
    private var myGeneration = 0

    // Only push a notification when the rounded percentage actually changes. Segment-level
    // updates can fire many times a second on playlists with lots of small segments, and posting
    // a notification for every single one is both wasteful and prone to being silently dropped by
    // Android's per-notification-id rate limiting - which looked like "progress isn't moving".
    private var lastNotifiedProgress = -1

    fun start() {
        isPaused = false
        isCancelled = false
        myGeneration = bumpGeneration(videoId)
        job = service.serviceScope.launch {
            try {
                if (!isNetworkAvailable(service)) {
                    service.downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, "Нет подключения к интернету")
                    service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", "Нет интернета"))
                    service.updateNotification(videoId, title, 0, isAudio, false, "Ошибка: Нет интернета")
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
                    File(service.applicationContext.cacheDir, "${title.replace("[^a-zA-Z0-9А-Яа-я]".toRegex(), "_")}_temp_${videoId}.mp4")
                } else {
                    finalFile
                }
                outputFile = tempFile

                val tracked = service.downloadTracker.downloads.value.find { it.videoId == videoId }
                val startSegment = if (tracked != null && tempFile.exists()) {
                    if (tracked.downloadedSegments > 0) {
                        tracked.downloadedSegments.coerceAtMost(totalSegments)
                    } else if (tracked.progress > 0) {
                        val est = (tracked.progress * totalSegments) / 100
                        est.coerceIn(0, totalSegments)
                    } else {
                        0
                    }
                } else {
                    0
                }
                downloadedSegments = startSegment

                val fos = FileOutputStream(tempFile, downloadedSegments > 0)

                for (i in downloadedSegments until totalSegments) {
                    if (!isActive || isPaused) break

                    if (i % 10 == 0 && !isNetworkAvailable(service)) {
                        fos.close()
                        service.downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, "Соединение потеряно")
                        service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", "Соединение потеряно"))
                        service.updateNotification(videoId, title, 0, isAudio, false, "Ошибка: Соединение потеряно")
                        return@launch
                    }

                    val segmentUrl = segments[i]
                    downloadSegment(segmentUrl, fos)

                    if (!isActive || isPaused || isCancelled) {
                        break
                    }

                    downloadedSegments++
                    val progress = if (totalSegments > 0) (downloadedSegments * 100) / totalSegments else 0
                    if (isActive && !isPaused && !isCancelled) {
                        if (progress != lastNotifiedProgress) {
                            lastNotifiedProgress = progress
                            service.updateNotification(videoId, title, progress, isAudio, false, generation = myGeneration)
                        }
                        // The in-app tracker still updates every segment for fine-grained UI progress.
                        service.downloadTracker.updateProgress(videoId, progress, downloadedSegments, totalSegments)
                    }
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
                    }

                    // extractAudioTrack hands off to Media3's Transformer, which runs on its own
                    // thread and can finish (calling continuation.resume normally) at essentially
                    // the same moment the user hits Stop/Cancel. None of the calls below are
                    // suspend calls, so cooperative cancellation alone won't stop them from
                    // running even though the job was already cancelled - which is what let a
                    // "download complete" notification get posted right after Cancel had already
                    // cleared it, and left a finished .m4a behind despite being "cancelled".
                    if (!isActive || isPaused || isCancelled) {
                        finalFile.delete()
                        return@launch
                    }

                    outputFile = finalFile
                    MediaScannerConnection.scanFile(service, arrayOf(outputFile!!.absolutePath), arrayOf(if (isAudio) "audio/mp4" else "video/mp4")) { path, uri ->
                        Log.d("DownloadService", "Scanned $path: uri=$uri")
                    }
                    service.downloadTracker.updateStatus(videoId, DownloadStatus.COMPLETED, filePath = outputFile!!.absolutePath)
                    service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_COMPLETE).putExtra("title", title))

                    service.showCompleteNotification(title, isAudio, videoId)
                }
            } catch (e: Exception) {
                if (isCancelled) {
                    service.downloadTracker.updateStatus(videoId, DownloadStatus.CANCELLED)
                    service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_CANCELLED).putExtra("video_id", videoId))
                    Log.d("RupoopDownload", "Task cancelled: $videoId")
                    return@launch
                } else if (isPaused) {
                    service.downloadTracker.updateStatus(videoId, DownloadStatus.PAUSED)
                    val prog = if (totalSegments > 0) (downloadedSegments * 100) / totalSegments else 0
                    service.updateNotification(videoId, title, prog, isAudio, true, "Приостановлено")
                    service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_PAUSED).putExtra("video_id", videoId))
                    Log.d("RupoopDownload", "Task paused: $videoId progress=$prog")
                    return@launch
                } else if (e is CancellationException) {
                    service.downloadTracker.updateStatus(videoId, DownloadStatus.CANCELLED)
                    service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_CANCELLED).putExtra("video_id", videoId))
                    Log.d("RupoopDownload", "Task cancelled (CancellationException): $videoId")
                    return@launch
                }
                Log.e("RupoopDownload", "Download error in task $videoId", e)
                service.downloadTracker.updateStatus(videoId, DownloadStatus.ERROR, e.message)
                service.sendBroadcast(Intent(DownloadService.ACTION_DOWNLOAD_ERROR).putExtra("title", title).putExtra("error", e.message))
                service.updateNotification(videoId, title, 0, isAudio, false, "Ошибка: ${e.message}")
            } finally {
                // Ensure task cleanup always happens once coroutine finishes
                if (!isPaused || isCancelled) {
                    service.activeTasks.remove(videoId)
                    service.checkAndStopService()
                }
            }
        }
    }

    private suspend fun resolveMediaPlaylist(url: String, quality: String): String {
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) { service.client.newCall(request).execute() }
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
        val response = withContext(Dispatchers.IO) { service.client.newCall(request).execute() }
        val body = response.body?.string() ?: throw Exception("Empty media playlist")

        val baseUrl = url.substringBeforeLast("/")
        return body.lines().filter { it.isNotEmpty() && !it.startsWith("#") }.map {
            if (it.startsWith("http")) it else "$baseUrl/$it"
        }
    }

    private suspend fun downloadSegment(url: String, fos: FileOutputStream) {
        val request = Request.Builder().url(url).build()
        val call = service.client.newCall(request)
        
        coroutineScope {
            val job = coroutineContext[Job]
            val handler = job?.invokeOnCompletion {
                call.cancel()
            }
            try {
                withContext(Dispatchers.IO) {
                    val response = call.execute()
                    if (!response.isSuccessful) throw Exception("Unexpected code $response")
                    val body = response.body ?: throw Exception("Empty body")
                    val buffer = ByteArray(8192)
                    body.byteStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive || isPaused) {
                                call.cancel()
                                throw CancellationException("Download paused or cancelled")
                            }
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } finally {
                handler?.dispose()
            }
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

                    val transformer = Transformer.Builder(service.applicationContext)
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
}