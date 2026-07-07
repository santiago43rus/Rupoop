package com.santiago43rus.rupoop.player

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
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
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = (showControls || isSeeking) && !isTransitioning,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isSeeking) Color.Transparent else Color.Black.copy(alpha = 0.4f))
                .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
        ) {
            if (!isSeeking) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (isFullscreen) 14.dp else 8.dp, end = if (isFullscreen) 20.dp else 4.dp, top = 8.dp, bottom = 8.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isFullscreen) {
                            onToggleFullscreen()
                        } else {
                            onMinimize()
                        }
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.Start) {
                        Text(currentVideo?.title ?: "", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentVideo?.author?.name ?: "", color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1)
                    }

                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                }

                // Center controls
                if (!isBuffering) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = if (isFullscreen) 32.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = !isFirstVideo && !isPreviousDisliked
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious, 
                                null, 
                                tint = if (isPreviousDisliked) Color.Red else if (isFirstVideo) Color.Gray else Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.width(32.dp))
                        IconButton(onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0 && currentTime >= duration)) {
                                    exoPlayer.seekTo(0)
                                }
                                exoPlayer.play()
                            }
                        }) {
                            val icon = if (isPlaying) Icons.Default.Pause
                                       else if (exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0 && currentTime >= duration)) Icons.Default.Replay
                                       else Icons.Default.PlayArrow
                            Icon(icon, null, tint = Color.White, modifier = Modifier.size(70.dp))
                        }
                        Spacer(Modifier.width(32.dp))
                        IconButton(
                            onClick = onNext,
                            enabled = !isLastVideo
                        ) {
                            Icon(
                                Icons.Default.SkipNext, 
                                null, 
                                tint = if (isLastVideo) Color.Gray else Color.White, 
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }
            }

            // Bottom bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isFullscreen) 16.dp else 4.dp)
                    .padding(start = if (isFullscreen) 22.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // YouTube Style Seeking Bubble
                if (isSeeking && draggingPos != null) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(0.8f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${formatTime(draggingPos)} ${currentVideo?.title?.take(20) ?: ""}...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = 12.dp),
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
                
                Slider(
                    value = if (duration > 0) (draggingPos ?: currentTime).toFloat() / duration else 0f,
                    onValueChange = { 
                        onSeekStart()
                        onSeekChange((it * duration).toLong()) 
                    },
                    onValueChangeFinished = onSeekEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Red,
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color.White.copy(0.3f)
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
                            modifier = Modifier.height(2.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.White.copy(0.3f)
                            )
                        )
                    }
                )
            }
        }
    }
}


