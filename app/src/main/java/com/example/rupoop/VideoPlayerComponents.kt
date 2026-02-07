package com.example.rupoop

import androidx.annotation.OptIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
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

    // Состояние качества и скорости
    var selectedQuality by remember { mutableStateOf("Авто") }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .then(
                    if (isFullscreen) Modifier.fillMaxSize()
                    else Modifier
                        .statusBarsPadding()
                        .padding(top = 32.dp)
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f)
                )
                .background(Color.Black)
                .pointerInput(isFullscreen) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -40 && !isFullscreen) onToggleFullscreen()
                        else if (dragAmount > 40) onMinimize()
                    }
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

            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .then(if (isFullscreen) Modifier.safeDrawingPadding() else Modifier)
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.SpaceBetween) {
                        IconButton(onClick = onMinimize) {
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, null, tint = Color.White)
                        }
                    }

                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                            Icon(Icons.Default.FastRewind, null, tint = Color.White, modifier = Modifier.size(50.dp))
                        }
                        Spacer(Modifier.width(40.dp))
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(70.dp))
                        }
                        Spacer(Modifier.width(40.dp))
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                            Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(50.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isFullscreen) 50.dp else 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(
                                text = "${formatTime(draggingPos ?: currentTime)} / ${formatTime(duration)}",
                                color = Color.White, style = MaterialTheme.typography.labelSmall
                            )
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null, tint = Color.White)
                            }
                        }
                        Slider(
                            value = if (duration > 0) (draggingPos ?: currentTime).toFloat() / duration else 0f,
                            onValueChange = {
                                draggingPos = (it * duration).toLong()
                                exoPlayer.seekTo(draggingPos!!)
                            },
                            onValueChangeFinished = { draggingPos = null },
                            modifier = Modifier.height(32.dp).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                        )
                    }
                }
            }
        }

        if (!isFullscreen) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = currentVideo?.title ?: "",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            exoPlayer = exoPlayer,
            currentQuality = selectedQuality,
            onQualitySelected = { selectedQuality = it },
            onDismiss = { showSettings = false }
        )
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
                ListItem(
                    headlineContent = { Text("Качество") },
                    trailingContent = { Text(currentQuality) },
                    modifier = Modifier.clickable { showQuality = true }
                )
                ListItem(
                    headlineContent = { Text("Скорость") },
                    trailingContent = { Text("${exoPlayer.playbackParameters.speed}x") },
                    modifier = Modifier.clickable { showSpeed = true }
                )
            }
        }
    }

    if (showQuality) {
        OptionSelectionDialog(
            title = "Качество",
            options = listOf("Авто", "1080p", "720p", "480p", "360p"),
            currentValue = currentQuality,
            onOptionSelected = { it ->
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
            },
            onDismiss = { showQuality = false }
        )
    }

    if (showSpeed) {
        OptionSelectionDialog(
            title = "Скорость воспроизведения",
            options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
            currentValue = exoPlayer.playbackParameters.speed,
            onOptionSelected = { speed ->
                exoPlayer.playbackParameters = PlaybackParameters(speed)
                showSpeed = false; onDismiss()
            },
            onDismiss = { showSpeed = false }
        )
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

// ... Остальные компоненты (MiniPlayer, formatTime) без изменений
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(video: SearchResult?, isPlaying: Boolean, exoPlayer: ExoPlayer, onClose: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(100.dp, 60.dp).background(Color.Black)) {
            AndroidView(factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(video?.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(video?.author?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
    }
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}