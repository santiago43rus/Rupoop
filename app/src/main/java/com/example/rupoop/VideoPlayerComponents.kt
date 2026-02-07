package com.example.rupoop

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun CustomVideoPlayer(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }

    // Обновление прогресса видео
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .clickable { showControls = !showControls }) {

        // Исправленный AndroidView без ошибок factory
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))) {

                // ВЕРХ: Кнопка сворачивания (вниз) и Настройки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        // Иконка стрелки вниз для сворачивания
                        Icon(Icons.Default.KeyboardArrowDown, "Свернуть", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Настройки", tint = Color.White)
                    }
                }

                // ЦЕНТР: Перемотка и Пауза
                Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                        // Используем FastRewind вместо Replay10
                        Icon(Icons.Default.FastRewind, "Назад", tint = Color.White, modifier = Modifier.size(55.dp))
                    }
                    Spacer(Modifier.width(30.dp))
                    IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза", tint = Color.White, modifier = Modifier.size(80.dp))
                    }
                    Spacer(Modifier.width(30.dp))
                    IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                        // Используем FastForward вместо Forward10
                        Icon(Icons.Default.FastForward, "Вперед", tint = Color.White, modifier = Modifier.size(55.dp))
                    }
                }

                // НИЗ: Слайдер и Полноэкранный режим
                Column(modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 15.dp)) {

                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("${formatTime(currentTime)} / ${formatTime(duration)}", color = Color.White)
                        IconButton(onClick = onToggleFullscreen) {
                            // Иконки расширения/уменьшения экрана
                            Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Экран", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    Slider(
                        value = if (duration > 0) currentTime.toFloat() / duration else 0f,
                        onValueChange = { newValue ->
                            val seekTo = (newValue * duration).toLong()
                            exoPlayer.seekTo(seekTo)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.Gray
                        )
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentSpeed = exoPlayer.playbackParameters.speed,
            onSpeedSelected = { exoPlayer.playbackParameters = PlaybackParameters(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun MiniPlayer(currentVideo: SearchResult?, isPlaying: Boolean, exoPlayer: ExoPlayer, onClose: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = currentVideo?.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.width(110.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Text(
                currentVideo?.title ?: "Нет названия",
                modifier = Modifier.weight(1f).padding(8.dp),
                maxLines = 1
            )
            IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                // В мини плеере теперь тоже иконка паузы/плея
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null)
            }
        }
    }
}

@Composable
fun SettingsDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column {
                Text("Скорость воспроизведения", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                    speeds.forEach { speed ->
                        TextButton(onClick = { onSpeedSelected(speed); onDismiss() }) {
                            Text("${speed}x", color = if (speed == currentSpeed) Color.Red else Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return "%02d:%02d".format(minutes, seconds)
}