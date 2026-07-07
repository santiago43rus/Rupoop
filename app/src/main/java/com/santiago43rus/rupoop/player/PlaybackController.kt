package com.santiago43rus.rupoop.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.auth.GistSyncManager
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.data.WatchHistoryItem
import com.santiago43rus.rupoop.data.RelatedVideoRecommendationStrategy
import com.santiago43rus.rupoop.util.PlayerState
import com.santiago43rus.rupoop.util.extractId
import com.santiago43rus.rupoop.util.isNetworkAvailable
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackController(
    internal val context: Context,
    internal val scope: CoroutineScope,
    internal val registryManager: UserRegistryManager,
    internal val settingsManager: SettingsManager,
    private val relatedVideoRecommender: RelatedVideoRecommendationStrategy,
    internal val pushToGitHub: () -> Unit,
    private val filterHiddenAndDisliked: (List<SearchResult>) -> List<SearchResult>,
    private val removeVideoFromUiLists: (SearchResult) -> Unit,
    private val snackbarMessage: MutableSharedFlow<String>,
    internal val onRegistryUpdate: (UserRegistry) -> Unit
) {

    // ── Playback queue ──
    var currentVideoList by mutableStateOf<List<SearchResult>>(emptyList())
    var currentVideoIndex by mutableIntStateOf(-1)
    var isPlaylistMode by mutableStateOf(false)

    // ── Player state ──
    var playerState by mutableStateOf(PlayerState.CLOSED)
    var playerTransitionProgress by mutableStateOf(1f)
    var currentVideo by mutableStateOf<SearchResult?>(null)
    var isFullscreenVideo by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var isBackgroundPlaybackEnabled by mutableStateOf(false)

    var relatedVideos by mutableStateOf<List<SearchResult>>(emptyList())

    // ── ExoPlayer ──
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().also { player ->
        player.playWhenReady = true
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { this@PlaybackController.isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("Rupoop", "ExoPlayer error", error)
                scope.launch {
                    snackbarMessage.emit("Ошибка воспроизведения: видео недоступно или отсутствует подключение")
                }
                playerState = PlayerState.CLOSED
            }
            override fun onPlaybackStateChanged(state: Int) {
                this@PlaybackController.isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && currentVideo != null) {
                    extractId(currentVideo!!.videoUrl)?.let { id ->
                        registryManager.updateWatchProgress(id, player.contentPosition, player.duration)
                        onRegistryUpdate(registryManager.registry)
                    }
                }
                if (state == Player.STATE_ENDED && currentVideo != null) {
                    extractId(currentVideo!!.videoUrl)?.let { id ->
                        registryManager.updateWatchProgress(id, player.duration, player.duration)
                        onRegistryUpdate(registryManager.registry)
                        pushToGitHub()
                    }
                }
            }
        })
    }

    internal var videoLoadingJob: Job? = null
    internal var progressSavingJob: Job? = null



    // ── Play video ──
    fun playVideo(video: SearchResult, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
        val isLocal = video.videoUrl.isNotEmpty() && !video.videoUrl.startsWith("http")
        if (isLocal) {
            val file = java.io.File(video.videoUrl)
            if (!file.exists()) {
                scope.launch {
                    snackbarMessage.emit("Видео не найдено")
                }
                playerState = PlayerState.CLOSED
                return
            }
            playLocalFile(video.videoUrl, video.title, list, isPlaylist)
            return
        }

        videoLoadingJob?.cancel()

        if (isPlaylist && list != null) {
            isPlaylistMode = true
            currentVideoList = list
            currentVideoIndex = list.indexOfFirst { it.videoUrl == video.videoUrl }.takeIf { it >= 0 } ?: 0
        } else {
            if (!isPlaylistMode) {
                if (list == relatedVideos || list == currentVideoList) {
                    val existingIndex = currentVideoList.indexOfFirst { it.videoUrl == video.videoUrl }
                    if (existingIndex != -1 && list == currentVideoList) {
                        currentVideoIndex = existingIndex
                    } else {
                        val newHistory = if (currentVideoIndex >= 0) {
                            currentVideoList.take(currentVideoIndex + 1).toMutableList()
                        } else mutableListOf()
                        newHistory.add(video)
                        currentVideoList = newHistory
                        currentVideoIndex = newHistory.size - 1
                    }
                } else {
                    isPlaylistMode = false
                    currentVideoList = listOf(video)
                    currentVideoIndex = 0
                }
            } else {
                isPlaylistMode = false
                currentVideoList = listOf(video)
                currentVideoIndex = 0
            }
        }

        currentVideo = video

        extractId(video.videoUrl)?.let { id ->
            val historyItem = registryManager.registry.watchHistory.find { it.videoId == id }

            registryManager.addWatchHistory(WatchHistoryItem(
                videoId = id,
                timestamp = System.currentTimeMillis(),
                progress = historyItem?.progress ?: 0,
                totalDuration = video.duration?.toLong()?.times(1000) ?: historyItem?.totalDuration ?: 0,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                authorName = video.author?.name,
                authorAvatarUrl = video.author?.avatarUrl,
                authorId = video.author?.id,
                videoUrl = video.videoUrl
            ))
            onRegistryUpdate(registryManager.registry)
            pushToGitHub()

            videoLoadingJob = scope.launch {
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    val url = opt.videoBalancer?.m3u8
                    if (url != null) {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        
                        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(video.title)
                            .setArtist(video.author?.name)
                            .setArtworkUri(video.thumbnailUrl?.let { android.net.Uri.parse(it) })
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(id)
                            .setMediaMetadata(mediaMetadata)
                            .build()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        
                        syncPlaybackService()
                        
                        val historyProg = historyItem?.progress ?: 0
                        val historyTotal = historyItem?.totalDuration ?: 0
                        if (historyTotal > 0 && (historyProg.toFloat() / historyTotal) >= 0.95f) {
                            exoPlayer.seekTo(0)
                        } else if (historyProg > 0) {
                            exoPlayer.seekTo(historyProg)
                        }
                        
                        exoPlayer.play()
                        playerState = PlayerState.FULL
                    } else {
                        snackbarMessage.emit("Видео не найдено")
                        playerState = PlayerState.CLOSED
                    }

                    val relatedResults = withContext(Dispatchers.IO) {
                        val queries = relatedVideoRecommender.getSearchQueries(video)
                        val allResults = mutableListOf<SearchResult>()
                        for (q in queries) {
                            try {
                                val res = RetrofitClient.api.searchVideos(q).results
                                allResults.addAll(res)
                                if (allResults.size > 20) break
                            } catch (_: Exception) {}
                        }
                        allResults.distinctBy { it.videoUrl }
                    }
                    val filteredRelated = relatedVideoRecommender.recommendRelated(video, relatedResults)
                    val finalRelated = filterHiddenAndDisliked(filteredRelated)
                    relatedVideos = finalRelated

                    if (!isPlaylistMode) {
                        currentVideoList = currentVideoList.take(currentVideoIndex + 1) + finalRelated
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Play error", e)
                    snackbarMessage.emit("Видео не найдено")
                    playerState = PlayerState.CLOSED
                }
            }
        }
    }

    fun playNext() {
        if (isPlaylistMode) {
            if (currentVideoIndex < currentVideoList.size - 1) {
                playVideo(currentVideoList[currentVideoIndex + 1], currentVideoList, true)
            }
        } else {
            if (currentVideoIndex < currentVideoList.size - 1) {
                playVideo(currentVideoList[currentVideoIndex + 1], currentVideoList, false)
            } else if (relatedVideos.isNotEmpty()) {
                playVideo(relatedVideos.first(), relatedVideos, false)
            }
        }
    }

    fun playPrevious() {
        if (currentVideoIndex > 0) {
            playVideo(currentVideoList[currentVideoIndex - 1], currentVideoList, isPlaylistMode)
        }
    }



    fun closePlayer() {
        playerState = PlayerState.CLOSED
        exoPlayer.stop()
        syncPlaybackService()
        pushToGitHub()
    }

    fun release() {
        exoPlayer.release()
        progressSavingJob?.cancel()
    }
}
