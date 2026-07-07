package com.santiago43rus.rupoop.player

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@UnstableApi
@ExperimentalMaterial3Api
@Composable
fun CustomVideoPlayer(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFullscreen: Boolean,
    currentVideo: SearchResult?,
    relatedVideos: List<SearchResult> = emptyList(),
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    isFirstVideo: Boolean = false,
    isPreviousDisliked: Boolean = false,
    isLastVideo: Boolean = false,
    isTransitioning: Boolean = false,
    onPlayRelated: (SearchResult) -> Unit = {},
    isBackgroundEnabled: Boolean = false,
    onBackgroundToggle: () -> Unit = {}
) {
    val showControlsState = remember { mutableStateOf(true) }
    var showControls by showControlsState
    val isLocalFile = currentVideo?.videoUrl != null && !currentVideo.videoUrl.startsWith("http")
    var currentTime by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var showSettings by remember { mutableStateOf(false) }
    var draggingPos by remember { mutableStateOf<Long?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    val showMoreVideosState = remember { mutableStateOf(false) }
    var showMoreVideos by showMoreVideosState
    var seekDirection by remember { mutableIntStateOf(0) }
    var showSeekAnimation by remember { mutableStateOf(false) }
    var accumulatedSeekAmount by remember { mutableLongStateOf(0L) }
    var seekAnimationJob by remember { mutableStateOf<Job?>(null) }
    val moreVideosDragOffsetState = remember { mutableStateOf(0f) }
    var moreVideosDragOffset by moreVideosDragOffsetState

    val swipeScaleState = remember { mutableStateOf(1f) }
    var swipeScale by swipeScaleState
    val swipeOffsetYState = remember { mutableStateOf(0f) }
    var swipeOffsetY by swipeOffsetYState
    val swipeOffsetXState = remember { mutableStateOf(0f) }
    var swipeOffsetX by swipeOffsetXState
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                moreVideosDragOffset = 0f
                swipeScale = 1f
                swipeOffsetY = 0f
                swipeOffsetX = 0f
                if (isFastForwarding) {
                    isFastForwarding = false
                    exoPlayer.playbackParameters = PlaybackParameters(1f)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isTransitioning) {
        if (isTransitioning) {
            showControls = false
        }
    }

    LaunchedEffect(showControls, isPlaying, isSeeking) {
        if (showControls && isPlaying && !isSeeking) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    var selectedQuality by remember { mutableStateOf("Авто") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldFillMax = isFullscreen && isLandscape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (shouldFillMax) Modifier.fillMaxSize() else Modifier.aspectRatio(16 / 9f))
            .offset { IntOffset(swipeOffsetX.roundToInt(), swipeOffsetY.roundToInt()) }
            .graphicsLayer {
                scaleX = swipeScale
                scaleY = swipeScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (shouldFillMax) 0.5f else 0f)
            }
            .background(Color.Black)
            .playerDragGestures(
                isFullscreen = isFullscreen,
                isLocalFile = isLocalFile,
                isTransitioning = isTransitioning,
                showControls = showControlsState,
                showMoreVideos = showMoreVideosState,
                moreVideosDragOffset = moreVideosDragOffsetState,
                swipeScale = swipeScaleState,
                swipeOffsetX = swipeOffsetXState,
                swipeOffsetY = swipeOffsetYState,
                onToggleFullscreen = onToggleFullscreen
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        showSeekAnimation = true
                        val baseSeek = settingsManager.doubleTapSeekDuration * 1000L

                        seekAnimationJob?.cancel()

                        if (offset.x < size.width / 2) {
                            if (seekDirection == 1) accumulatedSeekAmount = 0L
                            seekDirection = -1
                            accumulatedSeekAmount += baseSeek
                            val target = (exoPlayer.currentPosition - baseSeek).coerceAtLeast(0)
                            currentTime = target
                            exoPlayer.seekTo(target)
                        } else {
                            if (seekDirection == -1) accumulatedSeekAmount = 0L
                            seekDirection = 1
                            accumulatedSeekAmount += baseSeek
                            val target = (exoPlayer.currentPosition + baseSeek).coerceAtMost(exoPlayer.duration)
                            currentTime = target
                            exoPlayer.seekTo(target)
                        }

                        seekAnimationJob = CoroutineScope(Dispatchers.Main).launch {
                            delay(700)
                            showSeekAnimation = false
                            accumulatedSeekAmount = 0L
                        }
                    },
                    onLongPress = {
                        isFastForwarding = true
                        exoPlayer.playbackParameters = PlaybackParameters(2f)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isFastForwarding) {
                            isFastForwarding = false
                            exoPlayer.playbackParameters = PlaybackParameters(1f)
                        }
                    }
                )
            }
    ) {
        val isAudio = currentVideo?.videoUrl?.endsWith(".mp3") == true || currentVideo?.videoUrl?.endsWith(".m4a") == true
        AudioOrVideoPlayerView(
            isAudio = isAudio,
            currentVideo = currentVideo,
            exoPlayer = exoPlayer,
            isBuffering = isBuffering
        )

        SpeedIndicator(
            isFastForwarding = isFastForwarding,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        )

        SeekAnimationOverlay(
            showSeekAnimation = showSeekAnimation,
            seekDirection = seekDirection,
            accumulatedSeekAmount = accumulatedSeekAmount
        )

        ControlsOverlay(
            showControls = showControls,
            isSeeking = isSeeking,
            isTransitioning = isTransitioning,
            isFullscreen = isFullscreen,
            draggingPos = draggingPos,
            currentTime = currentTime,
            duration = duration,
            currentVideo = currentVideo,
            exoPlayer = exoPlayer,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isFirstVideo = isFirstVideo,
            isPreviousDisliked = isPreviousDisliked,
            isLastVideo = isLastVideo,
            onMinimize = onMinimize,
            onToggleFullscreen = onToggleFullscreen,
            onNext = onNext,
            onPrevious = onPrevious,
            onShowSettings = { showSettings = true },
            onSeekStart = { isSeeking = true },
            onSeekChange = { draggingPos = it },
            onSeekEnd = {
                isSeeking = false
                draggingPos?.let {
                    exoPlayer.seekTo(it)
                    currentTime = it
                }
                draggingPos = null
            }
        )

        MoreVideosOverlay(
            showMoreVideos = showMoreVideos,
            onClose = { showMoreVideos = false },
            isFullscreen = isFullscreen,
            isLocalFile = isLocalFile,
            moreVideosDragOffset = moreVideosDragOffset,
            currentVideo = currentVideo,
            relatedVideos = relatedVideos,
            onPlayRelated = onPlayRelated
        )
    }

    if (showSettings) {
        SettingsDialog(exoPlayer, selectedQuality, { selectedQuality = it }, { showSettings = false }, isBackgroundEnabled, onBackgroundToggle)
    }
}
