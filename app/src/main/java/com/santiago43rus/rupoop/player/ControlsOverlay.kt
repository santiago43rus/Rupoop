package com.santiago43rus.rupoop.player

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount
import kotlin.math.roundToInt

@UnstableApi
@ExperimentalMaterial3Api
@Composable
fun ControlsOverlay(
    showControls: Boolean,
    isSeeking: Boolean,
    isTransitioning: Boolean,
    isFullscreen: Boolean,
    draggingPos: Long?,
    currentTime: Long,
    duration: Long,
    currentVideo: SearchResult?,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFirstVideo: Boolean,
    isPreviousDisliked: Boolean,
    isLastVideo: Boolean,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShowSettings: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    bufferedPercent: Int = 0,
    isExpandingToFullscreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = (showControls || isSeeking) && !isTransitioning && !isExpandingToFullscreen,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (isTransitioning || isExpandingToFullscreen) 0 else 300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val config = LocalConfiguration.current
            val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isCompactLandscape = isLandscape && !isFullscreen

            val topGradientHeight = if (isFullscreen) 72.dp else 44.dp
            val bottomGradientHeight = if (isFullscreen) 88.dp else 48.dp
            val gradientAlpha = if (isFullscreen) 0.45f else 0.25f

            if (!isSeeking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topGradientHeight)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(gradientAlpha), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomGradientHeight)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(gradientAlpha))))
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(if (isFullscreen) 90.dp else 50.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(gradientAlpha)))))
            }

            val topBarPaddingTop = if (isFullscreen) 12.dp else 2.dp
            val topBarPaddingBottom = if (isFullscreen) 8.dp else 2.dp
            val topBarPaddingStart = if (isFullscreen) 14.dp else 8.dp
            val topBarPaddingEnd = if (isFullscreen) 20.dp else 4.dp

            val bottomBarPaddingBottom = if (isFullscreen) 16.dp else 0.dp
            val bottomBarPaddingStart = if (isFullscreen) 22.dp else 12.dp
            val bottomBarPaddingEnd = if (isFullscreen) 32.dp else 12.dp
            val bottomRowOffsetY = if (isFullscreen) 12.dp else 4.dp

            if (!isSeeking) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(
                            start = topBarPaddingStart,
                            end = topBarPaddingEnd,
                            top = topBarPaddingTop,
                            bottom = topBarPaddingBottom
                        ),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    if (isFullscreen) {
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.Start) {
                            Text(currentVideo?.title ?: "", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentVideo?.author?.name ?: "", color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                }

                // Center controls
                if (!isBuffering) {
                    val iconScale = if (isCompactLandscape) 0.7f else 1f
                    Row(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = if (isFullscreen) 32.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = !isFirstVideo && !isPreviousDisliked,
                            modifier = Modifier.size((52 * iconScale).dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious, 
                                null, 
                                tint = if (isPreviousDisliked) Color.Red else if (isFirstVideo) Color.Gray else Color.White,
                                modifier = Modifier.size((40 * iconScale).dp)
                            )
                        }
                        Spacer(Modifier.width((32 * iconScale).dp))
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0 && currentTime >= duration)) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size((70 * iconScale).dp)
                        ) {
                            val icon = if (isPlaying) Icons.Default.Pause
                                       else if (exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0 && currentTime >= duration)) Icons.Default.Replay
                                       else Icons.Default.PlayArrow
                            Icon(icon, null, tint = Color.White, modifier = Modifier.size((56 * iconScale).dp))
                        }
                        Spacer(Modifier.width((32 * iconScale).dp))
                        IconButton(
                            onClick = onNext,
                            enabled = !isLastVideo,
                            modifier = Modifier.size((52 * iconScale).dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext, 
                                null, 
                                tint = if (isLastVideo) Color.Gray else Color.White, 
                                modifier = Modifier.size((40 * iconScale).dp)
                            )
                        }
                    }
                }
            }

            // Bottom bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = bottomBarPaddingBottom)
                    .padding(start = bottomBarPaddingStart, end = bottomBarPaddingEnd)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Seeking time bubble (no thumbnail preview — just the target time)
                if (isSeeking && draggingPos != null) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(0.85f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = formatTime(draggingPos),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = bottomRowOffsetY),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTime(draggingPos ?: currentTime)} / ${formatTime(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (!isSeeking) {
                        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth().height(if (isCompactLandscape) 20.dp else 32.dp)) {
                    val progress = if (duration > 0) ((draggingPos ?: currentTime).toFloat() / duration).coerceIn(0f, 1f) else 0f
                    // Unwatched, not-yet-buffered: clearly gray
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFF6E6E6E))
                    )
                    // Unwatched but buffered ahead: lighter gray
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((bufferedPercent / 100f).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFFAFAFAF))
                    )
                    // Played/Watched part: vibrant YouTube red!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.Red)
                    )
                    Slider(
                        value = progress,
                        onValueChange = { 
                            onSeekStart()
                            onSeekChange((it * duration).toLong()) 
                        },
                        onValueChangeFinished = onSeekEnd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompactLandscape) 20.dp else 32.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.Transparent
                        ),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(12.dp, 12.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.Red)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}


