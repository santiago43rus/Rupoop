@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.rupoop

import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit


@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomVideoPlayer(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    currentVideo: SearchResult?,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    var draggingPos by remember { mutableStateOf<Long?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            .background(Color.Black)
            .pointerInput(isFullscreen) {
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (totalDragY < -150 && !isFullscreen) onToggleFullscreen()
                        else if (totalDragY > 150) {
                            if (isFullscreen) onToggleFullscreen() else onMinimize()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        } else {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
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
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2x Speed Indicator
        if (isFastForwarding) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 48.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Controls Overlay
        if (showControls || isSeeking) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(if (isSeeking) Color.Transparent else Color.Black.copy(alpha = 0.4f))
            ) {
                if (!isSeeking) {
                    // Top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                            .padding(8.dp), 
                        Arrangement.SpaceBetween, 
                        Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (isFullscreen) onToggleFullscreen() else onMinimize() }) {
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        
                        // Title and Author overlay (Center-ish top)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.Start) {
                            Text(currentVideo?.title ?: "", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentVideo?.author?.name ?: "", color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1)
                        }

                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, null, tint = Color.White)
                        }
                    }

                    // Center controls
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                            Icon(Icons.Default.FastRewind, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                        Spacer(Modifier.width(32.dp))
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.width(32.dp))
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                            Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                }

                // Bottom bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(bottom = if (isFullscreen) 12.dp else 4.dp)
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
                                text = "${formatTime(draggingPos!!)} ${currentVideo?.title?.take(20) ?: ""}...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth(if (isFullscreen) 0.9f else 1f)
                            .padding(horizontal = if (isFullscreen) 0.dp else 16.dp)
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
                        onValueChange = { isSeeking = true; draggingPos = (it * duration).toLong() },
                        onValueChangeFinished = { isSeeking = false; exoPlayer.seekTo(draggingPos!!); draggingPos = null },
                        modifier = Modifier
                            .fillMaxWidth(if (isFullscreen) 0.9f else 1f)
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

    if (showSettings) {
        SettingsDialog(exoPlayer, selectedQuality, { selectedQuality = it }, { showSettings = false })
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SettingsDialog(exoPlayer: ExoPlayer, currentQuality: String, onQualitySelected: (String) -> Unit, onDismiss: () -> Unit) {
    var showQuality by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Настройки видео", style = MaterialTheme.typography.titleLarge)
                ListItem(headlineContent = { Text("Качество") }, trailingContent = { Text(currentQuality) }, modifier = Modifier.clickable { showQuality = true })
                ListItem(headlineContent = { Text("Скорость") }, trailingContent = { Text("${exoPlayer.playbackParameters.speed}x") }, modifier = Modifier.clickable { showSpeed = true })
            }
        }
    }
    if (showQuality) {
        OptionSelectionDialog("Качество", listOf("Авто", "1080p", "720p", "480p", "360p"), currentQuality, { it ->
            onQualitySelected(it)
            val params = exoPlayer.trackSelectionParameters.buildUpon()
            when(it) {
                "1080p" -> params.setMaxVideoSize(1920, 1080).setForceHighestSupportedBitrate(true)
                "720p" -> params.setMaxVideoSize(1280, 720).setForceHighestSupportedBitrate(true)
                "480p" -> params.setMaxVideoSize(854, 480).setForceHighestSupportedBitrate(true)
                "360p" -> params.setMaxVideoSize(640, 360).setForceHighestSupportedBitrate(true)
                else -> params.clearVideoSizeConstraints().setForceHighestSupportedBitrate(false)
            }
            exoPlayer.trackSelectionParameters = params.build()
            showQuality = false; onDismiss()
        }, { showQuality = false })
    }
    if (showSpeed) {
        OptionSelectionDialog("Скорость", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), exoPlayer.playbackParameters.speed, { speed ->
            exoPlayer.playbackParameters = PlaybackParameters(speed)
            showSpeed = false; onDismiss()
        }, { showSpeed = false })
    }
}

@Composable
fun <T> OptionSelectionDialog(title: String, options: List<T>, currentValue: T, onOptionSelected: (T) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onOptionSelected(option) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == currentValue, onClick = null)
                        Text(option.toString(), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(video: SearchResult?, isPlaying: Boolean, exoPlayer: ExoPlayer, onClose: () -> Unit, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(100.dp, 56.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
            AndroidView(factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video?.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(video?.author?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
    }
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms); val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60; val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
