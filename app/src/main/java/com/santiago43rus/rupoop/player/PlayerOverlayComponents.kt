package com.santiago43rus.rupoop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DoubleTapRipple(
    rippleKey: Int,
    position: Offset,
    modifier: Modifier = Modifier
) {
    if (rippleKey == 0) return
    val scale = remember(rippleKey) { Animatable(0f) }
    val alpha = remember(rippleKey) { Animatable(0.32f) }
    LaunchedEffect(rippleKey) {
        scale.snapTo(0f)
        alpha.snapTo(0.32f)
        coroutineScope {
            launch { scale.animateTo(1f, tween(450, easing = LinearEasing)) }
            launch { alpha.animateTo(0f, tween(450, easing = LinearEasing)) }
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val maxRadius = size.minDimension * 0.55f
        drawCircle(
            color = Color.White.copy(alpha = alpha.value),
            radius = maxRadius * scale.value,
            center = position
        )
    }
}

@Composable
fun UpNextOverlay(
    visible: Boolean,
    nextVideo: SearchResult?,
    countdownSeconds: Int,
    totalSeconds: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Следующее видео", color = Color.White.copy(0.65f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(150.dp, 96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.DarkGray)
                    ) {
                        if (nextVideo?.thumbnailUrl != null) {
                            AsyncImage(
                                model = nextVideo.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))
                    }
                    CircularProgressIndicator(
                        progress = if (totalSeconds > 0) countdownSeconds.toFloat() / totalSeconds else 0f,
                        modifier = Modifier.size(52.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    IconButton(onClick = onPlayNow, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = nextVideo?.title ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 230.dp)
                )
                nextVideo?.author?.name?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onCancel) {
                    Text("Отмена", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ErrorRetryOverlay(
    visible: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text("Не удалось загрузить видео. Повторите попытку", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.15f))
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Повторить", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SpeedIndicator(isFastForwarding: Boolean, isSpeedLocked: Boolean, modifier: Modifier = Modifier) {
    val visible = isFastForwarding || isSpeedLocked
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        ) + fadeIn(tween(120)),
        exit = scaleOut(targetScale = 0.6f, animationSpec = tween(150)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        // A small satisfying "pop" whenever the lock engages, and a color shift so the lock feels
        // distinct and alive — similar to TikTok's speed-lock feedback.
        val pulse by animateFloatAsState(
            targetValue = if (isSpeedLocked) 1.1f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "speedLockPulse"
        )
        val bgColor by animateColorAsState(
            targetValue = if (isSpeedLocked) Color(0xFFE62117).copy(alpha = 0.9f) else Color.Black.copy(0.75f),
            animationSpec = tween(280),
            label = "speedLockBg"
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "2x",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                AnimatedVisibility(
                    visible = isSpeedLocked,
                    enter = scaleIn(
                        initialScale = 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    ) + fadeIn(tween(150)),
                    exit = scaleOut(targetScale = 0f, animationSpec = tween(120)) + fadeOut(tween(120))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
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
    modifier: Modifier = Modifier,
    resizeMode: Int = 0 // RESIZE_MODE_FIT
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
                        this.resizeMode = resizeMode
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp
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
    onToggleFullscreen: () -> Unit,
    isFastForwarding: Boolean = false
): Modifier = pointerInput(isFullscreen, isFastForwarding) {
    if (!isFullscreen || isFastForwarding) return@pointerInput
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
            ignoreGesture = isFastForwarding || showMoreVideos.value || offset.y < edgeMargin || offset.y > size.height - edgeMargin

            if (!ignoreGesture) {
                wasControlsVisible = showControls.value
                showControls.value = false
            }
        },
        onDrag = { change, dragAmount ->
            if (ignoreGesture || isFastForwarding) return@detectDragGestures
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
