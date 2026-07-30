package com.santiago43rus.rupoop.screen

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.VideoDetails
import com.santiago43rus.rupoop.player.AudioOrVideoPlayerView
import com.santiago43rus.rupoop.player.CustomVideoPlayer
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun lerp(start: Dp, stop: Dp, fraction: Float): Dp = start + (stop - start) * fraction

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubePlayerContainer(vm: AppViewModel, padding: PaddingValues) {
    if (vm.playerState == PlayerState.CLOSED || vm.isSettingsVisible) return

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val relatedListState = rememberLazyListState()
    
    val showMoreVideosState = remember { mutableStateOf(false) }
    val moreVideosDragOffsetState = remember { mutableStateOf(0f) }

    val bottomPadding = padding.calculateBottomPadding()
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp
    
    val isTablet = config.smallestScreenWidthDp >= 600
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = isTablet && isLandscape

    LaunchedEffect(config.orientation, vm.playerState) {
        if (vm.isFullscreenVideo && vm.isFullscreenTriggeredManually) return@LaunchedEffect
        if (vm.playerState == PlayerState.FULL && vm.currentVideo != null) {
            val isCurrentlyLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
            vm.toggleFullscreen(isCurrentlyLandscape, false)
        }
    }

    val statusBarsTopPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    
    var isLargeMiniPlayer by remember { mutableStateOf(false) }
    val miniWidthDp = if (isLandscape && !isTablet) 140.dp else if (isLargeMiniPlayer) 240.dp else 180.dp
    val miniHeightDp = miniWidthDp * 9f / 16f

    val miniWidthPx = with(density) { miniWidthDp.toPx() }
    val miniHeightPx = with(density) { miniHeightDp.toPx() }
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val statusBarsTopPaddingPx = with(density) { statusBarsTopPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }

    // Retrieve system safe area padding constraints
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val safeLeft = safeDrawing.calculateStartPadding(LocalLayoutDirection.current)
    val safeRight = safeDrawing.calculateEndPadding(LocalLayoutDirection.current)
    val safeTop = safeDrawing.calculateTopPadding()
    val safeBottom = safeDrawing.calculateBottomPadding()

    val safeLeftPx = with(density) { safeLeft.toPx() }
    val safeRightPx = with(density) { safeRight.toPx() }
    val safeTopPx = with(density) { safeTop.toPx() }
    val safeBottomPx = with(density) { safeBottom.toPx() }

    val marginDp = 16.dp
    val marginPx = with(density) { marginDp.toPx() }
    val minX = safeLeftPx + marginPx
    val maxX = screenWidthPx - safeRightPx - miniWidthPx - marginPx
    val minY = safeTopPx + marginPx
    // Use the larger of the app's bottom bar and the system nav bar inset, plus a small fixed gap.
    // This keeps the clearance consistent across devices instead of relying only on the app's own bottom padding.
    val bottomClearancePx = kotlin.math.max(bottomPaddingPx, safeBottomPx) + with(density) { 6.dp.toPx() }
    val maxY = screenHeightPx - bottomClearancePx - miniHeightPx

    val floatingX = remember { Animatable(maxX) }
    val floatingY = remember { Animatable(maxY) }

    val maxDragDp = (screenHeight - statusBarsTopPadding - bottomPadding - miniHeightDp).coerceAtLeast(1.dp)
    val maxDragPx = with(density) { maxDragDp.toPx() }

    val dragOffsetY = remember { Animatable(if (vm.playerState == PlayerState.FULL) 0f else maxDragPx) }
    val fullscreenDragOffsetY = remember { Animatable(0f) }
    var lastPlayerState by remember { mutableStateOf(vm.playerState) }

    LaunchedEffect(config.orientation, screenWidthPx, screenHeightPx, bottomClearancePx, isLargeMiniPlayer) {
        if (vm.playerState == PlayerState.MINI) {
            floatingX.snapTo(maxX)
            floatingY.snapTo(maxY)
        } else if (vm.playerState == PlayerState.FULL) {
            floatingX.snapTo(floatingX.value.coerceIn(minX, maxX))
            floatingY.snapTo(floatingY.value.coerceIn(minY, maxY))
        }
    }

    LaunchedEffect(vm.playerState, maxDragPx) {
        if (maxDragPx > 0f) {
            val target = if (vm.playerState == PlayerState.FULL) 0f else maxDragPx
            if (vm.playerState != lastPlayerState) {
                if (vm.playerState == PlayerState.MINI) {
                    floatingX.snapTo(maxX)
                    floatingY.snapTo(maxY)
                }
                dragOffsetY.animateTo(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                lastPlayerState = vm.playerState
            } else {
                dragOffsetY.snapTo(target)
            }
        }
    }

    val realProgress by remember {
        derivedStateOf {
            if (maxDragPx > 0f) (dragOffsetY.value / maxDragPx).coerceIn(0f, 1f) else 0f
        }
    }
    LaunchedEffect(realProgress) {
        vm.playerTransitionProgress = realProgress
    }

    val startWidth = if (isWideScreen) {
        screenWidth * 0.6f
    } else {
        screenWidth
    }

    val startHeight = if (isWideScreen) {
        (screenWidth * 0.6f) * 9f / 16f
    } else if (isLandscape) {
        screenHeight
    } else {
        screenWidth * 9f / 16f
    }

    val currentWidth = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) screenWidth else {
        lerp(startWidth, miniWidthDp, realProgress)
    }

    val currentHeight = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) screenHeight else {
        lerp(startHeight, miniHeightDp, realProgress)
    }

    val startY = if (isWideScreen || (!vm.isFullscreenVideo && !isLandscape)) {
        statusBarsTopPadding
    } else {
        0.dp
    }

    val currentX = with(density) { (realProgress * floatingX.value).toDp() }
    val currentY = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) 0.dp else {
        val startYPx = with(density) { startY.toPx() }
        with(density) { (startYPx + (floatingY.value - startYPx) * realProgress).toDp() }
    }

    val cornerRadius = 16.dp * realProgress

    val maxDragDistanceVertical = 150f
    val fsProgress = (fullscreenDragOffsetY.value / maxDragDistanceVertical).coerceIn(0f, 1f)
    val fsScale = 1f + (0.15f * fsProgress)
    val fsOffsetY = -fullscreenDragOffsetY.value * 0.3f

    val fullPlayerDragModifier = if (vm.playerState == PlayerState.FULL && !vm.isFullscreenVideo && !vm.isFastForwarding) {
        var touchStartY = 0f
        var initialDragDirection = 0f
        Modifier.pointerInput(vm.playerState, vm.isFullscreenVideo, vm.isFastForwarding) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touchStartY = down.position.y
                initialDragDirection = 0f
                var isDragging = false
                var cumulativeDragY = 0f

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty() || vm.isFastForwarding) {
                        break
                    }

                    val change = pressed.first()
                    val dragAmount = change.position.y - change.previousPosition.y
                    cumulativeDragY += dragAmount

                    if (!isDragging && kotlin.math.abs(cumulativeDragY) > 60f) {
                        isDragging = true
                        initialDragDirection = kotlin.math.sign(cumulativeDragY)
                    }

                    if (isDragging && initialDragDirection > 0f) {
                        change.consume()
                        val activeDragAmount = dragAmount * 1.1f
                        val isAtTop = relatedListState.firstVisibleItemIndex == 0 && relatedListState.firstVisibleItemScrollOffset == 0
                        val isTouchOnPlayer = touchStartY < size.width * (9f / 16f)

                        if (isAtTop || isTouchOnPlayer || (dragOffsetY.value > 0 && dragOffsetY.value < maxDragPx)) {
                            scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDragPx)) }
                        }
                    } else if (isDragging && initialDragDirection < 0f) {
                        val isTouchOnPlayer = touchStartY < size.width * (9f / 16f)
                        if (isTouchOnPlayer) {
                            change.consume()
                            val activeDragAmount = dragAmount * 1.1f
                            scope.launch { fullscreenDragOffsetY.snapTo((fullscreenDragOffsetY.value - activeDragAmount).coerceIn(0f, maxDragDistanceVertical)) }
                        }
                    }
                }

                scope.launch {
                    initialDragDirection = 0f
                    if (fullscreenDragOffsetY.value > 50f) {
                        vm.toggleFullscreen(true, true)
                    }
                    fullscreenDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))

                    if (isDragging) {
                        if (dragOffsetY.value > maxDragPx + 80f) {
                            vm.closePlayer()
                            dragOffsetY.snapTo(maxDragPx)
                        } else if (dragOffsetY.value > 0f) {
                            val toMini = dragOffsetY.value > maxDragPx * 0.30f
                            vm.playerState = if (toMini) PlayerState.MINI else PlayerState.FULL
                            dragOffsetY.animateTo(if (toMini) maxDragPx else 0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                        } else {
                            dragOffsetY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                        }
                    } else {
                        val target = if (vm.playerState == PlayerState.FULL) 0f else maxDragPx
                        dragOffsetY.snapTo(target)
                    }
                }
            }
        }
    } else Modifier

    val miniPlayerGesturesModifier = if (vm.playerState == PlayerState.MINI) {
        Modifier.pointerInput(config.orientation, minX, maxX, minY, maxY, screenWidthPx, miniWidthPx) {
            detectDragGestures(
                onDragEnd = {
                    scope.launch {
                        val fx = floatingX.value
                        if (fx < -miniWidthPx * 0.4f || fx > screenWidthPx - miniWidthPx * 0.6f) {
                            floatingX.animateTo(if (fx < screenWidthPx / 2f) -miniWidthPx - 50f else screenWidthPx + 50f, spring())
                            vm.closePlayer()
                        } else {
                            launch { floatingX.animateTo(if (fx + miniWidthPx / 2f < screenWidthPx / 2f) minX else maxX, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
                            launch { floatingY.animateTo(floatingY.value.coerceIn(minY, maxY), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    scope.launch { floatingX.snapTo(floatingX.value + dragAmount.x); floatingY.snapTo(floatingY.value + dragAmount.y) }
                }
            )
        }
    } else Modifier

    Box(modifier = Modifier.fillMaxSize()) {
        val relatedListAlpha = if (vm.playerState == PlayerState.MINI || vm.isFullscreenVideo) 0f else (1f - realProgress * 1.5f).coerceIn(0f, 1f)
        val relatedListTranslationY = if (vm.isFullscreenVideo) 0f else with(density) { realProgress * 250.dp.toPx() }

        if (relatedListAlpha > 0f && !vm.isFullscreenVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = relatedListAlpha
                        translationY = relatedListTranslationY
                    }
            ) {
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                        // Left Column (60%): Space for video player at top + scrollable VideoDetails below
                        Column(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                        ) {
                            Spacer(modifier = Modifier.height(startHeight))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                VideoDetails(
                                    video = vm.currentVideo,
                                    registry = vm.userRegistry,
                                    onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                    onToggleSub = { vm.toggleSubscription(it) },
                                    onLike = { vm.toggleLike(it) },
                                    onDislike = { vm.toggleDislike(it) },
                                    onShare = { vm.shareVideo(it) },
                                    onAddToPlaylist = { vm.showPlaylistDialog = it },
                                    onDownload = { vm.showDownloadDialog = it },
                                    isBackgroundEnabled = vm.isBackgroundPlaybackEnabled,
                                    onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() }
                                )
                            }
                        }

                        // Right Column (40%): Recommendations list
                        Box(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End))
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            RelatedVideosList(
                                modifier = Modifier.fillMaxSize(),
                                listState = relatedListState,
                                currentVideo = vm.currentVideo,
                                relatedVideos = vm.relatedVideos,
                                userRegistry = vm.userRegistry,
                                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                onToggleSub = { vm.toggleSubscription(it) },
                                onLike = { vm.toggleLike(it) },
                                onDislike = { vm.toggleDislike(it) },
                                onShare = { vm.shareVideo(it) },
                                onAddToPlaylist = { vm.showPlaylistDialog = it },
                                onDownload = { vm.showDownloadDialog = it },
                                onVideoClick = { v, list -> vm.playVideo(v, list, false) },
                                onMoreClick = { item, action -> vm.handleVideoMoreAction(item, action) },
                                alphaProgress = realProgress,
                                isBackgroundEnabled = vm.isBackgroundPlaybackEnabled,
                                onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() },
                                showVideoDetails = false
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                        Spacer(modifier = Modifier.height(statusBarsTopPadding + screenWidth * 9f / 16f))
                        RelatedVideosList(
                            modifier = Modifier.fillMaxSize(),
                            listState = relatedListState,
                            currentVideo = vm.currentVideo,
                            relatedVideos = vm.relatedVideos,
                            userRegistry = vm.userRegistry,
                            onAuthorClick = { vm.loadAuthorVideos(it, false) },
                            onToggleSub = { vm.toggleSubscription(it) },
                            onLike = { vm.toggleLike(it) },
                            onDislike = { vm.toggleDislike(it) },
                            onShare = { vm.shareVideo(it) },
                            onAddToPlaylist = { vm.showPlaylistDialog = it },
                            onDownload = { vm.showDownloadDialog = it },
                            onVideoClick = { v, list -> vm.playVideo(v, list, false) },
                            onMoreClick = { item, action -> vm.handleVideoMoreAction(item, action) },
                            alphaProgress = realProgress,
                            isBackgroundEnabled = vm.isBackgroundPlaybackEnabled,
                            onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() },
                            showVideoDetails = true,
                            useTwoColumns = isTablet
                        )
                    }
                }
            }
        }

        val cardElevation = lerp(0.dp, 8.dp, realProgress)
        Card(
            shape = RoundedCornerShape(cornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
            border = if (realProgress > 0.4f) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f * realProgress)) else null,
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.offset(x = currentX, y = currentY).width(currentWidth).height(currentHeight).then(fullPlayerDragModifier).then(miniPlayerGesturesModifier)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val isMiniLayout = vm.playerState == PlayerState.MINI || realProgress >= 0.7f
                val miniOverlayAlpha = if (vm.playerState == PlayerState.MINI) 1f else ((realProgress - 0.7f) / 0.3f).coerceIn(0f, 1f)

                // Persistent CustomVideoPlayer across both FULL and MINI states to avoid unmounting/re-binding ExoPlayer surface
                Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = fsScale
                    scaleY = fsScale
                    translationY = fsOffsetY
                }) {
                    CustomVideoPlayer(
                        exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isBuffering = vm.isBuffering, isFullscreen = vm.isFullscreenVideo, currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                        onMinimize = {
                            vm.toggleFullscreen(false, false)
                            vm.playerState = PlayerState.MINI
                        }, 
                        onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo, true) }, onNext = { vm.playNext() }, onPrevious = { vm.playPrevious() },
                        isFirstVideo = vm.currentVideoIndex <= 0, isPreviousDisliked = vm.isPreviousVideoDislikedOrHidden(),
                        isLastVideo = if (vm.isPlaylistMode) vm.currentVideoIndex >= vm.currentVideoList.size - 1 else (vm.currentVideoIndex >= vm.currentVideoList.size - 1 && vm.relatedVideos.isEmpty()),
                        isTransitioning = realProgress > 0f || vm.playerState == PlayerState.MINI, onPlayRelated = { vm.playVideo(it, vm.relatedVideos) },
                        onFastForwardingChange = { vm.isFastForwarding = it },
                        showMoreVideosState = showMoreVideosState,
                        moreVideosDragOffsetState = moreVideosDragOffsetState
                    )
                }

                if (isMiniLayout) {
                    var miniPlayerProgress by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(vm.isPlaying, vm.currentVideo) {
                        while (true) {
                            val duration = vm.exoPlayer.duration
                            miniPlayerProgress = if (duration > 0) vm.exoPlayer.currentPosition.toFloat() / duration else 0f
                            delay(500)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = miniOverlayAlpha }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { vm.playerState = PlayerState.FULL },
                                        onDoubleTap = { isLargeMiniPlayer = !isLargeMiniPlayer }
                                    )
                                }
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.BottomCenter)) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(miniPlayerProgress).background(Color.Red))
                        }

                        // Close (top-right) and Play/Pause (top-left) are always visible in the mini player
                        IconButton(
                            onClick = { if (vm.isPlaying) vm.exoPlayer.pause() else vm.exoPlayer.play() },
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp)
                        ) {
                            Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = { vm.closePlayer() },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        val currVideo = vm.currentVideo
        val isLocalFile = currVideo?.videoUrl != null && !currVideo.videoUrl.startsWith("http")
        if ((showMoreVideosState.value || moreVideosDragOffsetState.value != 0f) && (isLandscape || vm.isFullscreenVideo)) {
            com.santiago43rus.rupoop.player.MoreVideosOverlay(
                showMoreVideos = showMoreVideosState.value,
                onClose = { showMoreVideosState.value = false },
                isFullscreen = vm.isFullscreenVideo,
                isLocalFile = isLocalFile,
                moreVideosDragOffset = moreVideosDragOffsetState.value,
                currentVideo = currVideo,
                relatedVideos = vm.relatedVideos,
                onPlayRelated = { vm.playVideo(it, vm.relatedVideos) }
            )
        }
    }
}
