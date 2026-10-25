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
    currentQuality: String = "Авто",
    seekStepDurationSeconds: Int = 10,
    onQualitySelected: (String) -> Unit = {},
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
    onAuthorClick: (Author) -> Unit = {},
    onToggleSubscription: (Author) -> Unit = {},
    onRelatedVideoClick: (SearchResult) -> Unit = {},
    onVideoMenuClick: (SearchResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    var currentPositionMs by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var durationMs by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }

    val seekStepDurationMs = (seekStepDurationSeconds * 1000L).coerceAtLeast(5000L)

    fun scheduleAutoHide() {
        autoHideJob?.cancel()
        if (isPlaying && !showInfoPanel && !showSettingsDialog) {
            autoHideJob = coroutineScope.launch {
                delay(4000)
                showControls = false
            }
        }
    }

    LaunchedEffect(currentVideo) {
        showControls = true
        scheduleAutoHide()
    }

    LaunchedEffect(showControls, isPlaying, showInfoPanel, showSettingsDialog) {
        if (showControls) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (isPlaying && !showInfoPanel && !showSettingsDialog) {
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
    }

    BackHandler {
        if (showSettingsDialog) {
            showSettingsDialog = false
            showControls = true
            scheduleAutoHide()
        } else if (showInfoPanel) {
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
                    when (event.key) {
                        Key.MediaPlayPause -> {
                            onPlayPause()
                            showControls = true
                            scheduleAutoHide()
                            true
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
                        else -> {
                            // Если оверлей и панель скрыты: пульт управляет плеером
                            if (!showControls && !showInfoPanel && !showSettingsDialog) {
                                val repeatMultiplier = if (event.nativeKeyEvent.repeatCount > 2) 2 else 1
                                val effectiveStep = seekStepDurationMs * repeatMultiplier
                                when (event.key) {
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                        onPlayPause()
                                        showControls = true
                                        scheduleAutoHide()
                                        true
                                    }
                                    Key.DirectionLeft, Key.MediaRewind -> {
                                        onSeekRelative(-effectiveStep)
                                        showControls = true
                                        scheduleAutoHide()
                                        true
                                    }
                                    Key.DirectionRight, Key.MediaFastForward -> {
                                        onSeekRelative(effectiveStep)
                                        showControls = true
                                        scheduleAutoHide()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        showControls = true
                                        scheduleAutoHide()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        showInfoPanel = true
                                        autoHideJob?.cancel()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
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
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Панель информации, прогресса и управления (сезоны, серии, звук, кнопки, настройки)
        if (showControls && !showInfoPanel && !showSettingsDialog) {
            TvPlayerOverlay(
                currentVideo = currentVideo,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                episodes = episodes,
                currentEpisodeIndex = currentEpisodeIndex,
                audioTracks = audioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                seekStepDurationMs = seekStepDurationMs,
                onPlayPause = onPlayPause,
                onSeekRelative = onSeekRelative,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onEpisodeSelect = onEpisodeSelect,
                onAudioTrackSelect = onAudioTrackSelect,
                onOpenSettings = {
                    showSettingsDialog = true
                    autoHideJob?.cancel()
                },
                onCloseOverlay = {
                    showControls = false
                },
                onOpenInfoPanel = {
                    showControls = false
                    showInfoPanel = true
                    autoHideJob?.cancel()
                }
            )
        }

        // Диалог настроек качества и скорости видео
        if (showSettingsDialog) {
            TvVideoSettingsDialog(
                exoPlayer = exoPlayer,
                currentQuality = currentQuality,
                onQualitySelected = onQualitySelected,
                onDismiss = {
                    showSettingsDialog = false
                    showControls = true
                    scheduleAutoHide()
                }
            )
        }

        // Панель информации, действий (лайк/подписка/плейлист) и рекомендаций «Далее»
        if (showInfoPanel) {
            TvVideoInfoPanel(
                currentVideo = currentVideo,
                registry = registry,
                relatedVideos = relatedVideos,
                onLike = onLike,
                onDislike = onDislike,
                onWatchLater = onWatchLater,
                onAddToPlaylist = onAddToPlaylist,
                onAuthorClick = { author ->
                    exoPlayer.pause()
                    onAuthorClick(author)
                },
                onToggleSubscription = onToggleSubscription,
                onRelatedVideoClick = onRelatedVideoClick,
                onVideoMenuClick = onVideoMenuClick,
                onClose = {
                    showInfoPanel = false
                    showControls = true
                    scheduleAutoHide()
                }
            )
        }
    }
}
