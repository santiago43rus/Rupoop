package com.santiago43rus.rupoop.screen

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.player.CustomVideoPlayer
import com.santiago43rus.rupoop.player.MiniPlayer
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubePlayerContainer(
    vm: AppViewModel,
    padding: PaddingValues
) {
    if (vm.playerState == PlayerState.CLOSED || vm.isSettingsVisible) return

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val relatedListState = rememberLazyListState()

    val topPadding = padding.calculateTopPadding()
    val bottomPadding = padding.calculateBottomPadding()

    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp
    
    val isTablet = screenWidth >= 600.dp
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = isTablet || isLandscape

    LaunchedEffect(config.orientation, vm.playerState) {
        if (vm.isFullscreenVideo && vm.isFullscreenTriggeredManually) {
            return@LaunchedEffect
        }
        if (vm.playerState == PlayerState.FULL && vm.currentVideo != null) {
            val isCurrentlyLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isCurrentlyLandscape) {
                if (!vm.isFullscreenVideo) {
                    vm.toggleFullscreen(true, false)
                }
            } else {
                if (vm.isFullscreenVideo) {
                    vm.toggleFullscreen(false, false)
                }
            }
        }
    }

    val statusBarsTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val miniPlayerHeight = 64.dp
    val maxDragDp = (screenHeight - statusBarsTopPadding - bottomPadding - miniPlayerHeight).coerceAtLeast(1.dp)
    val maxDragPx = with(density) { maxDragDp.toPx() }

    val dragOffsetY = remember { Animatable(if (vm.playerState == PlayerState.FULL) 0f else maxDragPx) }
    val fullscreenDragOffsetY = remember { Animatable(0f) }

    var lastPlayerState by remember { mutableStateOf(vm.playerState) }

    LaunchedEffect(vm.playerState, vm.isFullscreenVideo, maxDragPx) {
        if (vm.isFullscreenVideo) {
            dragOffsetY.snapTo(0f)
            fullscreenDragOffsetY.snapTo(0f)
        } else if (maxDragPx > 0f) {
            val target = if (vm.playerState == PlayerState.FULL) 0f else maxDragPx
            if (vm.playerState != lastPlayerState) {
                dragOffsetY.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                )
                lastPlayerState = vm.playerState
            } else {
                dragOffsetY.snapTo(target)
            }
        }
    }

    val realProgress = if (maxDragPx > 0f) (dragOffsetY.value / maxDragPx) else 0f
    LaunchedEffect(dragOffsetY.value, maxDragPx) {
        val progress = if (maxDragPx > 0f) (dragOffsetY.value / maxDragPx).coerceIn(0f, 1f) else 0f
        vm.playerTransitionProgress = progress
    }
    val isMiniThresholdReached = realProgress >= 1f

    val targetFullHeight = screenHeight - statusBarsTopPadding

    val playerHeight = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) {
        screenHeight
    } else {
        targetFullHeight - (targetFullHeight - miniPlayerHeight) * realProgress.coerceIn(0f, 1f)
    }

    val playerOffsetY = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) 0.dp else {
        statusBarsTopPadding + (screenHeight - bottomPadding - miniPlayerHeight - statusBarsTopPadding) * realProgress.coerceIn(0f, 1f) + if (realProgress > 1f) maxDragDp * (realProgress - 1f) else 0.dp
    }

    val playerWidthFraction = if (vm.isFullscreenVideo || isMiniThresholdReached) 1f else {
        1f - (1f - (100f / screenWidth.value)) * realProgress.coerceIn(0f, 1f)
    }

    val maxDragDistanceVertical = 150f
    val fsProgress = (fullscreenDragOffsetY.value / maxDragDistanceVertical).coerceIn(0f, 1f)
    val fsScale = 1f + (0.25f * fsProgress)
    val fsOffsetY = -fullscreenDragOffsetY.value * 0.4f

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = playerOffsetY)
                .fillMaxWidth(playerWidthFraction)
                .then(if (vm.playerState == PlayerState.FULL && vm.isFullscreenVideo) Modifier.fillMaxSize() else Modifier.height(playerHeight))
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(vm.playerState, vm.isFullscreenVideo) {
                    if (!vm.isFullscreenVideo) {
                        var dragStartedFrom = vm.playerState
                        var initialDragDirection = 0f
                        var touchStartY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragStartedFrom = vm.playerState
                                initialDragDirection = 0f
                                touchStartY = offset.y
                            },
                            onDragEnd = {
                                scope.launch {
                                    initialDragDirection = 0f
                                    if (fullscreenDragOffsetY.value > 0f) {
                                        if (fullscreenDragOffsetY.value > 50f) {
                                            vm.toggleFullscreen(true, true)
                                        }
                                        fullscreenDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    } else if (dragOffsetY.value > maxDragPx + 80f) {
                                        vm.closePlayer()
                                        dragOffsetY.snapTo(maxDragPx)
                                    } else {
                                        val threshold = if (dragStartedFrom == PlayerState.MINI) maxDragPx * 0.85f else maxDragPx * 0.15f
                                        if (dragOffsetY.value > threshold) {
                                            vm.playerState = PlayerState.MINI
                                            dragOffsetY.animateTo(maxDragPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                        } else {
                                            vm.playerState = PlayerState.FULL
                                            dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    initialDragDirection = 0f
                                    fullscreenDragOffsetY.animateTo(0f)
                                    dragOffsetY.animateTo(if (vm.playerState == PlayerState.FULL) 0f else maxDragPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                }
                            }
                        ) { change, dragAmount ->
                            if (initialDragDirection == 0f && kotlin.math.abs(dragAmount) > 1f) {
                                initialDragDirection = kotlin.math.sign(dragAmount)
                            }
                            
                            val activeDragAmount = dragAmount * 1.1f
                            val isAtTop = relatedListState.firstVisibleItemIndex == 0 && relatedListState.firstVisibleItemScrollOffset == 0
                            val isTouchOnPlayer = touchStartY < size.width * (9f / 16f)

                            if (dragStartedFrom == PlayerState.MINI) {
                                change.consume()
                                if (initialDragDirection > 0f) {
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceAtMost(maxDragPx + 300f).coerceAtLeast(maxDragPx)) }
                                } else if (initialDragDirection < 0f) {
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDragPx)) }
                                }
                            } else if (dragStartedFrom == PlayerState.FULL && (isAtTop || isTouchOnPlayer)) {
                                if (initialDragDirection > 0f) {
                                    change.consume()
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDragPx)) }
                                } else if (initialDragDirection < 0f) {
                                    change.consume()
                                    scope.launch { fullscreenDragOffsetY.snapTo((fullscreenDragOffsetY.value - activeDragAmount).coerceIn(0f, maxDragDistanceVertical)) }
                                }
                            } else if (dragOffsetY.value > 0 && dragOffsetY.value < maxDragPx) {
                                change.consume()
                                scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDragPx)) }
                            }
                        }
                    }
                }
        ) {
            if (!isMiniThresholdReached) {
                if (isWideScreen && !vm.isFullscreenVideo) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = fsScale
                                    scaleY = fsScale
                                    translationY = fsOffsetY
                                }
                            ) {
                                CustomVideoPlayer(
                                    exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isBuffering = vm.isBuffering, isFullscreen = vm.isFullscreenVideo,
                                    currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                                    onMinimize = { vm.playerState = PlayerState.MINI },
                                    onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo, true) },
                                    onNext = { vm.playNext() }, onPrevious = { vm.playPrevious() },
                                    isFirstVideo = vm.currentVideoIndex <= 0,
                                    isPreviousDisliked = vm.isPreviousVideoDislikedOrHidden(),
                                    isLastVideo = if (vm.isPlaylistMode) vm.currentVideoIndex >= vm.currentVideoList.size - 1 else (vm.currentVideoIndex >= vm.currentVideoList.size - 1 && vm.relatedVideos.isEmpty()),
                                    isTransitioning = fsProgress > 0f || realProgress > 0f,
                                    onPlayRelated = { vm.playVideo(it, vm.relatedVideos) }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            LaunchedEffect(vm.currentVideo) {
                                relatedListState.scrollToItem(0)
                            }
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
                                onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    ) {
                        Box(modifier = Modifier.graphicsLayer {
                            scaleX = fsScale
                            scaleY = fsScale
                            translationY = fsOffsetY
                        }) {
                            CustomVideoPlayer(
                                exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isBuffering = vm.isBuffering, isFullscreen = vm.isFullscreenVideo,
                                currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                                onMinimize = { vm.playerState = PlayerState.MINI },
                                onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo, true) },
                                onNext = { vm.playNext() }, onPrevious = { vm.playPrevious() },
                                isFirstVideo = vm.currentVideoIndex <= 0,
                                isPreviousDisliked = vm.isPreviousVideoDislikedOrHidden(),
                                isLastVideo = if (vm.isPlaylistMode) vm.currentVideoIndex >= vm.currentVideoList.size - 1 else (vm.currentVideoIndex >= vm.currentVideoList.size - 1 && vm.relatedVideos.isEmpty()),
                                isTransitioning = fsProgress > 0f || realProgress > 0f,
                                onPlayRelated = { vm.playVideo(it, vm.relatedVideos) }
                            )
                        }
                        if (!vm.isFullscreenVideo) {
                            LaunchedEffect(vm.currentVideo) {
                                relatedListState.scrollToItem(0)
                            }
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
                                onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() }
                            )
                        }
                    }
                }
            } else {
                MiniPlayer(vm.currentVideo, vm.isPlaying, vm.exoPlayer, onClose = { vm.closePlayer() }, onClick = { vm.playerState = PlayerState.FULL })
            }
        }
    }
}
