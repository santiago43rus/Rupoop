package com.santiago43rus.rupoop.player

import android.os.Build
import androidx.media3.common.MediaItem
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.WatchHistoryItem
import com.santiago43rus.rupoop.util.PlayerState
import com.santiago43rus.rupoop.util.extractId

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlaybackController.playLocalFile(filePath: String, title: String, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
    videoLoadingJob?.cancel()
    val file = java.io.File(filePath)
    if (!file.exists()) return
    
    val video = SearchResult(videoUrl = filePath, title = title)
    currentVideo = video
    if (isPlaylist && list != null) {
        isPlaylistMode = true
        currentVideoList = list
        currentVideoIndex = list.indexOfFirst { it.videoUrl == filePath }.takeIf { it >= 0 } ?: 0
    } else {
        isPlaylistMode = false
        currentVideoList = listOf(video)
        currentVideoIndex = 0
    }
    relatedVideos = emptyList()

    val uniqueId = extractId(filePath) ?: filePath
    val historyItem = registryManager.registry.watchHistory.find { it.videoId == uniqueId }

    registryManager.addWatchHistory(WatchHistoryItem(
        videoId = uniqueId,
        timestamp = System.currentTimeMillis(),
        progress = historyItem?.progress ?: 0,
        totalDuration = historyItem?.totalDuration ?: 0,
        title = title,
        thumbnailUrl = null,
        authorName = "Локальный файл",
        authorAvatarUrl = null,
        authorId = null,
        videoUrl = filePath
    ))
    onRegistryUpdate(registryManager.registry)
    pushToGitHub()

    exoPlayer.stop()
    exoPlayer.clearMediaItems()
    
    val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .build()
    val mediaItem = MediaItem.Builder()
        .setUri(android.net.Uri.fromFile(file))
        .setMediaId(filePath)
        .setMediaMetadata(mediaMetadata)
        .apply {
            if (filePath.endsWith(".mp3") || filePath.endsWith(".m4a")) {
                setMimeType("audio/mp4")
            }
        }
        .build()
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()

    val historyProg = historyItem?.progress ?: 0
    val historyTotal = historyItem?.totalDuration ?: 0
    if (historyTotal > 0 && (historyProg.toFloat() / historyTotal) >= 0.95f) {
        exoPlayer.seekTo(0)
    } else if (historyProg > 0) {
        exoPlayer.seekTo(historyProg)
    }

    exoPlayer.play()
    playerState = PlayerState.FULL

    syncPlaybackService()
}
