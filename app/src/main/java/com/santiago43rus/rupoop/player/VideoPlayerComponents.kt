package com.santiago43rus.rupoop.player

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    moreVideosDragOffsetState: MutableState<Float> = remember { mutableStateOf(0f) },
    isExpandingToFullscreen: Boolean = false,
    onRequestPip: (() -> Unit)? = null,
    onZoomedChange: (Boolean) -> Unit = {}
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
    var showSpeedLockHint by remember { mutableStateOf(false) }
    var showSpeedUnlockHint by remember { mutableStateOf(false) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(showSpeedLockHint) {
        if (showSpeedLockHint) {
            delay(2200)
            showSpeedLockHint = false
        }
    }

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
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldFillMax = (isFullscreen && (isLandscape || isTablet)) || (isLandscape && !isTablet && !isTransitioning)

    LaunchedEffect(shouldFillMax) {
        if (!shouldFillMax) {
            zoomScale = 1f
            zoomOffsetX = 0f
            zoomOffsetY = 0f
        }
    }

    val isZoomed = remember(zoomScale, videoAspectRatio, shouldFillMax, configuration.screenWidthDp, configuration.screenHeightDp) {
        val containerAspect = if (shouldFillMax) {
            val w = configuration.screenWidthDp.toFloat()
            val h = configuration.screenHeightDp.toFloat()
            if (h > 0f) w / h else 16f / 9f
        } else {
            16f / 9f
        }
        val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
        zoomScale > fillScale * 1.03f
    }
    LaunchedEffect(isZoomed) {
        onZoomedChange(isZoomed)
    }

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
                onToggleFullscreen = onToggleFullscreen,
                isFastForwarding = isFastForwarding || showSpeedUnlockHint || showSpeedLockHint,
                isTablet = isTablet,
                isLandscape = isLandscape,
                isZoomed = isZoomed,
                hasRelatedVideos = relatedVideos.isNotEmpty()
            )
            // Pinch-to-zoom + pan (moves like on maps when zoomed).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isPinching = false
                    var didPinch = false
                    var wasTwoFingerGesture = false
                    var prevDistance = 0f
                    var prevCenter = Offset.Zero
                    var basePinchScale = 1f
                    var hasBrokenFreeFromFillScale = false
                    var prevSinglePos = down.position
                    var totalDragDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            wasTwoFingerGesture = true
                            didPinch = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val distance = (p1 - p2).getDistance().coerceAtLeast(1f)
                            val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                            if (!isPinching) {
                                isPinching = true
                                basePinchScale = zoomScale
                                val containerAspect = size.width.toFloat() / size.height.toFloat()
                                val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                                hasBrokenFreeFromFillScale = basePinchScale >= fillScale * 0.98f
                            } else if (prevDistance > 0f) {
                                val zoomDelta = distance / prevDistance
                                val panDelta = center - prevCenter

                                val containerAspect = size.width.toFloat() / size.height.toFloat()
                                val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                                val hasBars = abs(fillScale - 1f) > 0.03f
                                val maxScale = (fillScale * 8f).coerceAtLeast(8f)
                                val rawScale = (zoomScale * zoomDelta).coerceIn(1f, maxScale)

                                val newScale = if (hasBars) {
                                    if (basePinchScale < fillScale * 0.98f) {
                                        if (rawScale >= fillScale && rawScale <= fillScale * 1.15f && !hasBrokenFreeFromFillScale) {
                                            fillScale
                                        } else {
                                            if (rawScale > fillScale * 1.15f) {
                                                hasBrokenFreeFromFillScale = true
                                            }
                                            rawScale
                                        }
                                    } else {
                                        hasBrokenFreeFromFillScale = true
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
                                    hasBars && (abs(newScale - fillScale) / fillScale <= 0.03f || (!hasBrokenFreeFromFillScale && newScale <= fillScale * 1.03f)) -> "Без полей"
                                    newScale > fillScale * 1.03f -> {
                                        val percent = ((newScale / fillScale) * 100).toInt()
                                        "$percent%"
                                    }
                                    else -> null
                                }
                                pressed.forEach { it.consume() }
                            }
                            prevDistance = distance
                            prevCenter = center
                            prevSinglePos = pressed[0].position
                        } else if (pressed.size == 1) {
                            if (!wasTwoFingerGesture) {
                                val containerAspect = size.width.toFloat() / size.height.toFloat()
                                val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                                val isCurrentlyZoomed = zoomScale > fillScale * 1.03f

                                if (isCurrentlyZoomed) {
                                    val change = pressed[0]
                                    val dragDelta = change.position - prevSinglePos
                                    totalDragDistance += dragDelta.getDistance()
                                    prevSinglePos = change.position

                                    val maxOffsetX = (size.width * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = (size.height * (zoomScale - 1f) / 2f).coerceAtLeast(0f)

                                    if (maxOffsetX > 0f || maxOffsetY > 0f) {
                                        zoomOffsetX = (zoomOffsetX + dragDelta.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        zoomOffsetY = (zoomOffsetY + dragDelta.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    }
                                    change.consume()
                                }
                            } else {
                                // Transitioning release from two fingers: do not drag with the last remaining finger!
                                pressed[0].consume()
                            }
                            isPinching = false
                            prevDistance = 0f
                        } else {
                            isPinching = false
                            prevDistance = 0f
                        }
                        if (pressed.isEmpty()) break
                    }

                    val containerAspect = size.width.toFloat() / size.height.toFloat()
                    val fillScale = if (videoAspectRatio > containerAspect) videoAspectRatio / containerAspect else containerAspect / videoAspectRatio
                    val isCurrentlyZoomed = zoomScale > fillScale * 1.03f

                    if (!wasTwoFingerGesture && isCurrentlyZoomed && totalDragDistance < 15f) {
                        showControls = !showControls
                    }

                    // Only snap zoom scale when the user was actively pinching to zoom/unzoom.
                    // Single finger panning or tapping must NEVER snap or reset the zoom!
                    if (didPinch) {
                        val hasBars = abs(fillScale - 1f) > 0.03f
                        val snapTarget = when {
                            zoomScale < 1.05f -> 1f
                            hasBars && !hasBrokenFreeFromFillScale -> fillScale
                            hasBars && (abs(zoomScale - fillScale) / fillScale < 0.08f) -> fillScale
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
                        } else if (zoomScale > fillScale * 1.03f) {
                            val percent = ((zoomScale / fillScale) * 100).toInt()
                            showZoomToastMessage = "$percent%"
                        }
                    }
                }
            }
            .pointerInput(showUpNext, isZoomed) {
                if (showUpNext || isZoomed) return@pointerInput

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
                    val initiallyLocked = isSpeedLocked
                    var pendingLockState = initiallyLocked

                    if (!initiallyLocked) {
                        originalSpeed = exoPlayer.playbackParameters.speed
                        showControls = false // Popups/controls automatically disappear when holding 2x!
                        isFastForwarding = true
                        onFastForwardingChange(true)
                        exoPlayer.playbackParameters = PlaybackParameters(2f)
                        showSpeedLockHint = true
                        showSpeedUnlockHint = false
                    } else {
                        // When speed is locked and user long-presses screen:
                        // Controls do NOT hide automatically (user can still toggle and use them).
                        // Show unlock hint while user holds finger on screen!
                        showSpeedUnlockHint = true
                        showSpeedLockHint = false
                        onFastForwardingChange(true)
                    }

                    val swipeDownThreshold = 75f
                    val cancelThreshold = 35f

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val currentDragY = change.position.y - downPos.y
                            if (currentDragY > 15f) {
                                change.consume()
                            }

                            if (!initiallyLocked) {
                                if (!pendingLockState && currentDragY > swipeDownThreshold) {
                                    pendingLockState = true
                                    isSpeedLocked = true
                                    showSpeedLockHint = false
                                } else if (pendingLockState && currentDragY < cancelThreshold) {
                                    // User changed mind and moved finger back up before releasing
                                    pendingLockState = false
                                    isSpeedLocked = false
                                    showSpeedLockHint = true
                                }
                            } else {
                                if (pendingLockState && currentDragY > swipeDownThreshold) {
                                    // Moving down to unlock
                                    pendingLockState = false
                                    isSpeedLocked = false
                                    showSpeedUnlockHint = false
                                } else if (!pendingLockState && currentDragY < cancelThreshold) {
                                    // Moving back up cancels unlocking
                                    pendingLockState = true
                                    isSpeedLocked = true
                                    showSpeedUnlockHint = true
                                }
                            }
                        }
                    } catch (_: Exception) { }

                    isFastForwarding = false
                    onFastForwardingChange(false)
                    isSpeedLocked = pendingLockState
                    if (!isSpeedLocked) {
                        exoPlayer.playbackParameters = PlaybackParameters(originalSpeed)
                    }
                    showSpeedLockHint = false
                    showSpeedUnlockHint = false
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
                showLockHint = showSpeedLockHint,
                showUnlockHint = showSpeedUnlockHint,
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
            isExpandingToFullscreen = isExpandingToFullscreen,
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
            onRequestPip = onRequestPip,
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
