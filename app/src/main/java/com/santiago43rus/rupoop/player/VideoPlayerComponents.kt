package com.santiago43rus.rupoop.player

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.math.abs

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
    onBackgroundToggle: () -> Unit = {},
    onFastForwardingChange: (Boolean) -> Unit = {},
    showMoreVideosState: MutableState<Boolean> = remember { mutableStateOf(false) },
    moreVideosDragOffsetState: MutableState<Float> = remember { mutableStateOf(0f) }
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
    var showMoreVideos by showMoreVideosState
    var seekDirection by remember { mutableIntStateOf(0) }
    var showSeekAnimation by remember { mutableStateOf(false) }
    var accumulatedSeekAmount by remember { mutableLongStateOf(0L) }
    var seekAnimationJob by remember { mutableStateOf<Job?>(null) }
    var moreVideosDragOffset by moreVideosDragOffsetState

    val swipeScaleState = remember { mutableStateOf(1f) }
    var swipeScale by swipeScaleState
    val swipeOffsetYState = remember { mutableStateOf(0f) }
    var swipeOffsetY by swipeOffsetYState
    var originalSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSpeedLocked by remember { mutableStateOf(false) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    // Continuous pinch-to-zoom + pan (crops into the video like YouTube, never stretches it).
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }
    var showZoomToastMessage by remember { mutableStateOf<String?>(null) }
    val swipeOffsetXState = remember { mutableStateOf(0f) }
    var swipeOffsetX by swipeOffsetXState
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // AwaitPointerEventScope (used inside the pinch-zoom gesture below) is a restricted suspension
    // scope — it can only call its own member/extension suspend functions. animate() isn't one of
    // those, so the zoom snap-back animation has to be launched on a separate, unrestricted scope.
    val zoomAnimScope = rememberCoroutineScope()

    // Tracks the video's native aspect ratio so pinch-zoom can snap to an intermediate
    // "crop to fill" step (no black bars) before continuing into free custom zoom.
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        val current = exoPlayer.videoSize
        if (current.width > 0 && current.height > 0) {
            videoAspectRatio = (current.width.toFloat() * current.pixelWidthHeightRatio) / current.height.toFloat()
        }
        onDispose { exoPlayer.removeListener(listener) }
    }

    // --- Captions ---
    var hasTextTracks by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                hasTextTracks = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            }
        }
        exoPlayer.addListener(listener)
        hasTextTracks = exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
        onDispose { exoPlayer.removeListener(listener) }
    }

    fun toggleCaptions() {
        captionsEnabled = !captionsEnabled
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
            .build()
    }

    // --- Double-tap ripple ---
    var doubleTapRippleKey by remember { mutableIntStateOf(0) }
    var doubleTapPosition by remember { mutableStateOf(Offset.Zero) }

    // Performs one seek "tick" (used for both the initial double-tap and any subsequent chained taps).
    fun performSeek(side: Int, position: Offset) {
        doubleTapPosition = position
        doubleTapRippleKey += 1
        showSeekAnimation = true
        val baseSeek = settingsManager.doubleTapSeekDuration * 1000L

        seekAnimationJob?.cancel()

        if (side < 0) {
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
    }

    // --- Buffered range (for the seek bar's buffered-progress indicator) ---
    var bufferedPercent by remember { mutableIntStateOf(0) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            bufferedPercent = exoPlayer.bufferedPercentage
            delay(500)
        }
    }

    // --- Playback error / retry ---
    var playerError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "Не удалось воспроизвести видео"
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // --- Up Next / autoplay (always on, matching YouTube's default behavior) ---
    var showUpNext by remember { mutableStateOf(false) }
    var upNextCountdown by remember { mutableIntStateOf(5) }
    var upNextJob by remember { mutableStateOf<Job?>(null) }
    val upNextTotalSeconds = 5
    val nextVideoPreview = relatedVideos.firstOrNull()

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !isLastVideo) {
                    showUpNext = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(showUpNext) {
        if (showUpNext) {
            upNextCountdown = upNextTotalSeconds
            upNextJob?.cancel()
            upNextJob = CoroutineScope(Dispatchers.Main).launch {
                while (upNextCountdown > 0) {
                    delay(1000)
                    upNextCountdown -= 1
                }
                showUpNext = false
                onNext()
            }
        } else {
            upNextJob?.cancel()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                moreVideosDragOffset = 0f
                swipeScale = 1f
                swipeOffsetY = 0f
                swipeOffsetX = 0f
                if (isFastForwarding) {
                    isFastForwarding = false
                    onFastForwardingChange(false)
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
    val shouldFillMax = isFullscreen || (isLandscape && !isTransitioning)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (shouldFillMax) Modifier.fillMaxSize() else Modifier.aspectRatio(16 / 9f))
            .then(if (!isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier)
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
                onToggleFullscreen = onToggleFullscreen,
                isFastForwarding = isFastForwarding
            )
            // Pinch-to-zoom + pan. Deliberately hand-rolled instead of detectTransformGestures: that
            // helper can start consuming pointer events on a single finger once touch-slop is crossed,
            // which was stealing taps from the gesture detector below and made 2x fire on any tap.
            // This version never consumes anything until a genuine second finger is down.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isPinching = false
                    var prevDistance = 0f
                    var prevCenter = Offset.Zero
                    var basePinchScale = 1f
                    var hasBrokenFreeFromFillScale = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val distance = (p1 - p2).getDistance().coerceAtLeast(1f)
                            val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            if (!isPinching) {
                                isPinching = true
                                basePinchScale = zoomScale
                                hasBrokenFreeFromFillScale = false
                            } else if (prevDistance > 0f) {
                                val zoomDelta = distance / prevDistance
                                val panDelta = center - prevCenter
                                val rawScale = (zoomScale * zoomDelta).coerceIn(1f, 3f)

                                val containerAspect = size.width.toFloat() / size.height.toFloat()
                                val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                                val hasBars = abs(fillScale - 1f) > 0.03f

                                val newScale = if (hasBars) {
                                    if (basePinchScale < fillScale) {
                                        if (rawScale >= fillScale) {
                                            if (hasBrokenFreeFromFillScale) {
                                                rawScale
                                            } else if (rawScale > fillScale * 1.22f) {
                                                hasBrokenFreeFromFillScale = true
                                                rawScale
                                            } else {
                                                fillScale
                                            }
                                        } else {
                                            rawScale
                                        }
                                    } else {
                                        rawScale
                                    }
                                } else {
                                    rawScale
                                }

                                zoomScale = newScale
                                val maxOffsetX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (size.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                zoomOffsetX = (zoomOffsetX + panDelta.x).coerceIn(-maxOffsetX, maxOffsetX)
                                zoomOffsetY = (zoomOffsetY + panDelta.y).coerceIn(-maxOffsetY, maxOffsetY)

                                showZoomToastMessage = when {
                                    newScale <= 1.02f -> null
                                    hasBars && (!hasBrokenFreeFromFillScale || abs(newScale - fillScale) < 0.02f) -> "Без полей"
                                    else -> "${(newScale * 100).toInt()}%"
                                }
                                pressed.forEach { it.consume() }
                            }
                            prevDistance = distance
                            prevCenter = center
                        } else {
                            isPinching = false
                            prevDistance = 0f
                        }
                        if (pressed.isEmpty()) break
                    }

                    // Gesture fully ended: snap to a natural resting point — original (no zoom), the
                    // "crop to fill" step (removes black bars exactly), or leave it at a custom zoom.
                    val containerAspect = size.width.toFloat() / size.height.toFloat()
                    val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                    val hasBars = abs(fillScale - 1f) > 0.03f
                    val snapTarget = when {
                        zoomScale < 1.05f -> 1f
                        hasBars && (!hasBrokenFreeFromFillScale || zoomScale in (fillScale * 0.85f)..(fillScale * 1.15f)) -> fillScale
                        else -> null
                    }
                    if (snapTarget != null && abs(zoomScale - snapTarget) > 0.001f) {
                        val startScale = zoomScale
                        val startOffsetX = zoomOffsetX
                        val startOffsetY = zoomOffsetY
                        val containerW = size.width
                        val containerH = size.height
                        zoomAnimScope.launch {
                            animate(0f, 1f, animationSpec = tween(200)) { value, _ ->
                                zoomScale = startScale + (snapTarget - startScale) * value
                                val maxOX = (containerW * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOY = (containerH * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                zoomOffsetX = (startOffsetX * (1f - value)).coerceIn(-maxOX, maxOX)
                                zoomOffsetY = (startOffsetY * (1f - value)).coerceIn(-maxOY, maxOY)
                            }
                        }
                        showZoomToastMessage = if (snapTarget == 1f) null else "Без полей"
                    }
                }
            }
            .pointerInput(showUpNext) {
                if (showUpNext) return@pointerInput

                val doubleTapTimeoutMs = 260L
                val longPressTimeoutMs = 480L
                val seekChainWindowMs = 900L

                var chainActive = false
                var chainSide = 0
                var chainExpireJob: Job? = null

                fun extendChain(side: Int) {
                    chainActive = true
                    chainSide = side
                    chainExpireJob?.cancel()
                    chainExpireJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(seekChainWindowMs)
                        chainActive = false
                    }
                }

                // Distinguishes a genuine timeout (still held -> long press) from the pointer being
                // cancelled/consumed by a sibling gesture detector (abort, do nothing) from a normal
                // release (proceed as a tap). withTimeoutOrNull alone can't tell these apart because
                // waitForUpOrCancellation() also returns null on cancellation — conflating the two
                // was making 2x fire on every ordinary tap.
                suspend fun AwaitPointerEventScope.awaitReleaseOutcome(timeoutMs: Long): Boolean? {
                    return withTimeoutOrNull(timeoutMs) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            true
                        } else {
                            false
                        }
                    }
                }

                suspend fun AwaitPointerEventScope.trackLongPressHold(down: PointerInputChange, downPos: Offset) {
                    // Only capture the "base" speed if we're not already speed-locked at 2x — otherwise
                    // we'd overwrite the real base speed (e.g. 1.5x) with the current locked 2x value,
                    // and releasing/unlocking would incorrectly settle on 2x instead of the original speed.
                    if (!isSpeedLocked) {
                        originalSpeed = exoPlayer.playbackParameters.speed
                    }
                    isFastForwarding = true
                    onFastForwardingChange(true)
                    exoPlayer.playbackParameters = PlaybackParameters(2f)
                    var hasMovedDown = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position.y - downPos.y > 100f) hasMovedDown = true
                        }
                    } catch (e: Exception) { }
                    isFastForwarding = false
                    onFastForwardingChange(false)
                    if (hasMovedDown) isSpeedLocked = !isSpeedLocked
                    if (!isSpeedLocked) {
                        exoPlayer.playbackParameters = PlaybackParameters(originalSpeed)
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val side = if (downPos.x < size.width / 2f) -1 else 1

                    if (chainActive && side == chainSide) {
                        // Continuing an already-active seek chain: a single tap is enough, no need
                        // to double-tap again, matching YouTube's rapid re-seek behavior.
                        when (awaitReleaseOutcome(longPressTimeoutMs)) {
                            null -> {
                                chainActive = false
                                trackLongPressHold(down, downPos)
                            }
                            true -> {
                                extendChain(side)
                                performSeek(side, downPos)
                            }
                            false -> { /* cancelled by a sibling gesture — ignore */ }
                        }
                        return@awaitEachGesture
                    }

                    // Not continuing a chain: disambiguate single tap / double tap / long press.
                    when (awaitReleaseOutcome(longPressTimeoutMs)) {
                        null -> {
                            trackLongPressHold(down, downPos)
                            return@awaitEachGesture
                        }
                        false -> return@awaitEachGesture
                        true -> { /* released normally, continue below */ }
                    }

                    // Brief deliberate wait to see if a second tap follows — this is the same small
                    // delay YouTube has before a lone tap toggles the controls.
                    val secondDown = withTimeoutOrNull(doubleTapTimeoutMs) { awaitFirstDown(requireUnconsumed = false) }
                    if (secondDown == null) {
                        showControls = !showControls
                    } else {
                        val secondSide = if (secondDown.position.x < size.width / 2f) -1 else 1
                        withTimeoutOrNull(longPressTimeoutMs) { waitForUpOrCancellation()?.consume() }
                        extendChain(secondSide)
                        performSeek(secondSide, secondDown.position)
                    }
                }
            }
    ) {
        val isAudio = currentVideo?.videoUrl?.endsWith(".mp3") == true || currentVideo?.videoUrl?.endsWith(".m4a") == true
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffsetX
                        translationY = zoomOffsetY
                    }
            ) {
                AudioOrVideoPlayerView(
                    isAudio = isAudio,
                    currentVideo = currentVideo,
                    exoPlayer = exoPlayer,
                    isBuffering = isBuffering
                )
            }
        }

        if (!isTransitioning) {
            SpeedIndicator(
                isFastForwarding = isFastForwarding,
                isSpeedLocked = isSpeedLocked,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
            )
        }

        LaunchedEffect(showZoomToastMessage) {
            if (showZoomToastMessage != null) {
                delay(2000)
                showZoomToastMessage = null
            }
        }

        if (showZoomToastMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(showZoomToastMessage!!, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SeekAnimationOverlay(
            showSeekAnimation = showSeekAnimation,
            seekDirection = seekDirection,
            accumulatedSeekAmount = accumulatedSeekAmount
        )

        DoubleTapRipple(
            rippleKey = doubleTapRippleKey,
            position = doubleTapPosition
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
            hasCaptions = hasTextTracks,
            captionsEnabled = captionsEnabled,
            onMinimize = onMinimize,
            onToggleFullscreen = onToggleFullscreen,
            onNext = onNext,
            onPrevious = onPrevious,
            onShowSettings = { showSettings = true },
            onSeekStart = {
                isSeeking = true
                // Frame-accurate seeking while actively scrubbing, so the main video view shows the
                // real frame at the drag position (like YouTube), not just the nearest keyframe.
                exoPlayer.setSeekParameters(SeekParameters.EXACT)
            },
            onSeekChange = {
                draggingPos = it
                exoPlayer.seekTo(it)
            },
            onSeekEnd = {
                isSeeking = false
                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                draggingPos?.let {
                    exoPlayer.seekTo(it)
                    currentTime = it
                }
                draggingPos = null
            },
            onToggleCaptions = { toggleCaptions() },
            bufferedPercent = bufferedPercent
        )

        UpNextOverlay(
            visible = showUpNext,
            nextVideo = nextVideoPreview,
            countdownSeconds = upNextCountdown,
            totalSeconds = upNextTotalSeconds,
            onCancel = { showUpNext = false },
            onPlayNow = { showUpNext = false; onNext() }
        )

        ErrorRetryOverlay(
            visible = playerError != null,
            onRetry = {
                playerError = null
                exoPlayer.prepare()
                exoPlayer.play()
            }
        )


    }

    if (showSettings) {
        SettingsDialog(
            exoPlayer = exoPlayer,
            currentQuality = selectedQuality,
            onQualitySelected = { selectedQuality = it },
            onDismiss = { showSettings = false },
            isBackgroundEnabled = isBackgroundEnabled,
            onBackgroundToggle = onBackgroundToggle,
            displaySpeed = if (isFastForwarding || isSpeedLocked) originalSpeed else exoPlayer.playbackParameters.speed,
            onSpeedSelected = { newSpeed ->
                originalSpeed = newSpeed
                if (!isFastForwarding && !isSpeedLocked) {
                    exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                }
            }
        )
    }
}
