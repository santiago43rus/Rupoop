package com.santiago43rus.rupoop.tv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.parser.AudioTrackSource
import com.santiago43rus.rupoop.parser.Episode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    currentVideo: SearchResult?,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    registry: UserRegistry,
    relatedVideos: List<SearchResult> = emptyList(),
    episodes: List<Episode> = emptyList(),
    currentEpisodeIndex: Int = 0,
    audioTracks: List<AudioTrackSource> = emptyList(),
    selectedAudioTrackId: String? = null,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onClosePlayer: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onEpisodeSelect: (Int) -> Unit = {},
    onAudioTrackSelect: (AudioTrackSource) -> Unit = {},
    onLike: (SearchResult) -> Unit = {},
    onDislike: (SearchResult) -> Unit = {},
    onWatchLater: (SearchResult) -> Unit = {},
    onAddToPlaylist: (SearchResult) -> Unit = {},
    onShare: (SearchResult) -> Unit = {},
    onAuthorClick: (Author) -> Unit = {},
    onRelatedVideoClick: (SearchResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    var currentPositionMs by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var durationMs by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }

    // Ускоренная перемотка при удержании кнопок влево/вправо
    var repeatCount by remember { mutableIntStateOf(0) }
    var lastKeyEventTime by remember { mutableLongStateOf(0L) }

    fun scheduleAutoHide() {
        autoHideJob?.cancel()
        if (isPlaying && !showInfoPanel) {
            autoHideJob = coroutineScope.launch {
                delay(4000)
                showControls = false
            }
        }
    }

    // Периодическое обновление прогресса во время показа панели
    LaunchedEffect(showControls, isPlaying, showInfoPanel) {
        if (showControls) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (isPlaying && !showInfoPanel) {
                scheduleAutoHide()
                while (true) {
                    delay(500)
                    currentPositionMs = exoPlayer.currentPosition
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            } else {
                autoHideJob?.cancel()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        scheduleAutoHide()
    }

    BackHandler {
        if (showInfoPanel) {
            showInfoPanel = false
            showControls = true
            scheduleAutoHide()
        } else if (showControls) {
            showControls = false
        } else {
            onClosePlayer()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val now = System.currentTimeMillis()
                    if (now - lastKeyEventTime < 300) {
                        repeatCount++
                    } else {
                        repeatCount = 0
                    }
                    lastKeyEventTime = now

                    // Коэффициент ускорения при удержании стрелок
                    val stepMultiplier = when {
                        repeatCount > 8 -> 4L
                        repeatCount > 3 -> 2L
                        else -> 1L
                    }

                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                            if (!showInfoPanel) {
                                onPlayPause()
                                showControls = true
                                scheduleAutoHide()
                                true
                            } else false
                        }
                        Key.DirectionLeft, Key.MediaRewind -> {
                            if (!showInfoPanel) {
                                val seekStep = -10_000L * stepMultiplier
                                onSeekRelative(seekStep)
                                currentPositionMs = exoPlayer.currentPosition
                                showControls = true
                                scheduleAutoHide()
                                true
                            } else false
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            if (!showInfoPanel) {
                                val seekStep = 10_000L * stepMultiplier
                                onSeekRelative(seekStep)
                                currentPositionMs = exoPlayer.currentPosition
                                showControls = true
                                scheduleAutoHide()
                                true
                            } else false
                        }
                        Key.MediaNext -> {
                            onPlayNext()
                            showControls = true
                            scheduleAutoHide()
                            true
                        }
                        Key.MediaPrevious -> {
                            onPlayPrevious()
                            showControls = true
                            scheduleAutoHide()
                            true
                        }
                        Key.DirectionUp -> {
                            if (showInfoPanel) {
                                showInfoPanel = false
                                showControls = true
                                scheduleAutoHide()
                                true
                            } else {
                                showControls = true
                                scheduleAutoHide()
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (!showInfoPanel) {
                                showInfoPanel = true
                                showControls = false
                                autoHideJob?.cancel()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Видеоповерхность ExoPlayer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Панель информации, прогресса и управления
        if (showControls && !showInfoPanel) {
            TvPlayerOverlay(
                currentVideo = currentVideo,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                episodes = episodes,
                currentEpisodeIndex = currentEpisodeIndex,
                audioTracks = audioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                onPlayPause = onPlayPause,
                onSeekRelative = onSeekRelative,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onEpisodeSelect = onEpisodeSelect,
                onAudioTrackSelect = onAudioTrackSelect
            )
        }

        // Панель информации, действий (лайк/скрыть/плейлист) и рекомендаций «Далее» (вызывается ВНИЗ)
        if (showInfoPanel) {
            TvVideoInfoPanel(
                currentVideo = currentVideo,
                registry = registry,
                relatedVideos = relatedVideos,
                onLike = onLike,
                onDislike = onDislike,
                onWatchLater = onWatchLater,
                onAddToPlaylist = onAddToPlaylist,
                onShare = onShare,
                onAuthorClick = onAuthorClick,
                onRelatedVideoClick = onRelatedVideoClick
            )
        }
    }
}
