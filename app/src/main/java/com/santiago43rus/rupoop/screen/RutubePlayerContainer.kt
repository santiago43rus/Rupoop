package com.santiago43rus.rupoop.screen

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

    val topPaddingPx = with(density) { topPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }
    val maxDrag = with(density) { config.screenHeightDp.dp.toPx() - topPaddingPx - bottomPaddingPx - 64.dp.toPx() }

    val dragOffsetY = remember { Animatable(if (vm.playerState == PlayerState.FULL) 0f else maxDrag) }
    val fullscreenDragOffsetY = remember { Animatable(0f) }

    LaunchedEffect(vm.playerState, vm.isFullscreenVideo, maxDrag) {
        if (vm.isFullscreenVideo) {
            dragOffsetY.snapTo(0f)
            fullscreenDragOffsetY.snapTo(0f)
        } else if (maxDrag > 0) {
            dragOffsetY.animateTo(
                targetValue = if (vm.playerState == PlayerState.FULL) 0f else maxDrag,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    val realProgress = if (maxDrag > 0) (dragOffsetY.value / maxDrag) else 0f
    LaunchedEffect(dragOffsetY.value, maxDrag) {
        val progress = if (maxDrag > 0) (dragOffsetY.value / maxDrag).coerceIn(0f, 1f) else 0f
        vm.playerTransitionProgress = progress
    }
    val isMiniThresholdReached = realProgress >= 1f

    val targetFullHeight = config.screenHeightDp.dp - topPadding
    val miniPlayerHeight = 64.dp

    val playerHeight = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) {
        config.screenHeightDp.dp
    } else {
        targetFullHeight - (targetFullHeight - miniPlayerHeight) * realProgress.coerceIn(0f, 1f)
    }

    val playerOffsetY = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) 0.dp else {
        topPadding + (config.screenHeightDp.dp - bottomPadding - miniPlayerHeight - topPadding) * realProgress.coerceIn(0f, 1f) + if (realProgress > 1f) ((realProgress - 1f) * maxDrag).dp else 0.dp
    }

    val playerWidthFraction = if (vm.isFullscreenVideo || isMiniThresholdReached) 1f else {
        1f - (1f - (100f / config.screenWidthDp)) * realProgress.coerceIn(0f, 1f)
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
                                            vm.toggleFullscreen(true)
                                        }
                                        fullscreenDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    } else if (dragOffsetY.value > maxDrag + 80f) {
                                        vm.closePlayer()
                                        dragOffsetY.snapTo(maxDrag)
                                    } else {
                                        val threshold = if (dragStartedFrom == PlayerState.MINI) maxDrag * 0.85f else maxDrag * 0.15f
                                        if (dragOffsetY.value > threshold) {
                                            vm.playerState = PlayerState.MINI
                                            dragOffsetY.animateTo(maxDrag, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
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
                                    dragOffsetY.animateTo(if (vm.playerState == PlayerState.FULL) 0f else maxDrag, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
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
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceAtMost(maxDrag + 300f).coerceAtLeast(maxDrag)) }
                                } else if (initialDragDirection < 0f) {
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                                }
                            } else if (dragStartedFrom == PlayerState.FULL && (isAtTop || isTouchOnPlayer)) {
                                if (initialDragDirection > 0f) {
                                    change.consume()
                                    scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                                } else if (initialDragDirection < 0f) {
                                    change.consume()
                                    scope.launch { fullscreenDragOffsetY.snapTo((fullscreenDragOffsetY.value - activeDragAmount).coerceIn(0f, maxDragDistanceVertical)) }
                                }
                            } else if (dragOffsetY.value > 0 && dragOffsetY.value < maxDrag) {
                                change.consume()
                                scope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                            }
                        }
                    }
                }
        ) {
            if (!isMiniThresholdReached) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.graphicsLayer {
                        scaleX = fsScale
                        scaleY = fsScale
                        translationY = fsOffsetY
                    }) {
                        CustomVideoPlayer(
                            exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isBuffering = vm.isBuffering, isFullscreen = vm.isFullscreenVideo,
                            currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                            onMinimize = { vm.playerState = PlayerState.MINI },
                            onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo) },
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
            } else {
                MiniPlayer(vm.currentVideo, vm.isPlaying, vm.exoPlayer, onClose = { vm.closePlayer() }, onClick = { vm.playerState = PlayerState.FULL })
            }
        }
    }
}
