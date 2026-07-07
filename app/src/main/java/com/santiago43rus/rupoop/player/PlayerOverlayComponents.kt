package com.santiago43rus.rupoop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult

@Composable
fun SpeedIndicator(isFastForwarding: Boolean, modifier: Modifier = Modifier) {
    if (isFastForwarding) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SeekAnimationOverlay(
    showSeekAnimation: Boolean,
    seekDirection: Int,
    accumulatedSeekAmount: Long,
    modifier: Modifier = Modifier
) {
    if (showSeekAnimation) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = seekDirection == -1,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.35f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FastRewind, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Text("-${accumulatedSeekAmount / 1000} сек", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = seekDirection == 1,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.35f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Text("+${accumulatedSeekAmount / 1000} сек", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun AudioOrVideoPlayerView(
    isAudio: Boolean,
    currentVideo: SearchResult?,
    exoPlayer: ExoPlayer,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (isAudio) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (currentVideo?.thumbnailUrl != null) {
                    AsyncImage(
                        model = currentVideo.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.Red,
                strokeWidth = 4.dp
            )
        }
    }
}

@UnstableApi
fun Modifier.playerDragGestures(
    isFullscreen: Boolean,
    isLocalFile: Boolean,
    isTransitioning: Boolean,
    showControls: MutableState<Boolean>,
    showMoreVideos: MutableState<Boolean>,
    moreVideosDragOffset: MutableState<Float>,
    swipeScale: MutableState<Float>,
    swipeOffsetX: MutableState<Float>,
    swipeOffsetY: MutableState<Float>,
    onToggleFullscreen: () -> Unit
): Modifier = pointerInput(isFullscreen) {
    if (!isFullscreen) return@pointerInput
    var totalDragY = 0f
    var totalDragX = 0f
    var isMoreVideosGesture = false
    var isScreenTransitionGesture = false
    var wasControlsVisible = false
    var ignoreGesture = false
    val dragMultiplier = 1.3f
    val maxDragDistanceVertical = 150f
    val maxDragDistanceFullScreen = 80f
    val edgeMargin = 150f

    detectDragGestures(
        onDragStart = { offset ->
            totalDragY = 0f
            totalDragX = 0f
            isMoreVideosGesture = false
            isScreenTransitionGesture = false
            ignoreGesture = showMoreVideos.value || offset.y < edgeMargin || offset.y > size.height - edgeMargin
            
            if (!ignoreGesture) {
                wasControlsVisible = showControls.value
                showControls.value = false
            }
        },
        onDrag = { change, dragAmount ->
            if (ignoreGesture) return@detectDragGestures
            change.consume()
            totalDragY += dragAmount.y
            totalDragX += dragAmount.x

            val absY = kotlin.math.abs(totalDragY)
            val absX = kotlin.math.abs(totalDragX)

            if (!isMoreVideosGesture && !isScreenTransitionGesture && absY > 20f && absY > absX) {
                if (isFullscreen && totalDragY < 0 && !showMoreVideos.value && !isLocalFile) {
                    isMoreVideosGesture = true
                } else if (isFullscreen && totalDragY > 0) {
                    isScreenTransitionGesture = true
                } else if (!isFullscreen && totalDragY < 0) {
                    isScreenTransitionGesture = true
                }
            }

            if (isMoreVideosGesture) {
                if (totalDragY < 0) {
                    moreVideosDragOffset.value = totalDragY * 1.25f
                } else {
                    moreVideosDragOffset.value = 0f
                }
            } else if (isScreenTransitionGesture) {
                val activeDrag = if (isFullscreen) totalDragY else -totalDragY
                
                if (activeDrag > 0) {
                    val maxDist = if (isFullscreen) maxDragDistanceFullScreen else maxDragDistanceVertical
                    val boundedDrag = activeDrag.coerceAtMost(maxDist)
                    val progress = boundedDrag / maxDist

                    if (isFullscreen) {
                        swipeScale.value = 1f - (0.2f * progress)
                        swipeOffsetX.value = 0f
                        swipeOffsetY.value = boundedDrag * dragMultiplier * 0.4f
                    } else {
                        swipeScale.value = 1f + (0.25f * progress)
                        swipeOffsetX.value = 0f
                        swipeOffsetY.value = -boundedDrag * dragMultiplier * 0.4f
                    }
                } else {
                    swipeScale.value = 1f
                    swipeOffsetX.value = 0f
                    swipeOffsetY.value = 0f
                }
            }
        },
        onDragEnd = {
            if (ignoreGesture) return@detectDragGestures
            var returnControls = false
            if (isMoreVideosGesture) {
                if (totalDragY < -75f) {
                    showMoreVideos.value = true
                } else {
                    returnControls = true
                }
            } else if (isScreenTransitionGesture) {
                val activeDrag = if (isFullscreen) totalDragY else -totalDragY
                val threshold = if (isFullscreen) 40f else 60f

                if (activeDrag > threshold) {
                    onToggleFullscreen()
                    showControls.value = false
                } else {
                    returnControls = true
                }
            } else {
                returnControls = true
            }

            if (returnControls && wasControlsVisible && !isTransitioning) {
                showControls.value = true
            }
            
            moreVideosDragOffset.value = 0f
            swipeScale.value = 1f
            swipeOffsetY.value = 0f
            swipeOffsetX.value = 0f
            isMoreVideosGesture = false
            isScreenTransitionGesture = false
        },
        onDragCancel = {
            if (ignoreGesture) return@detectDragGestures
            if (wasControlsVisible) showControls.value = true
            moreVideosDragOffset.value = 0f
            swipeScale.value = 1f
            swipeOffsetY.value = 0f
            swipeOffsetX.value = 0f
            isMoreVideosGesture = false
            isScreenTransitionGesture = false
        }
    )
}
