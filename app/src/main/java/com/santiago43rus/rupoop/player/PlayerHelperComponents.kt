package com.santiago43rus.rupoop.player

import androidx.annotation.OptIn
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun SettingsDialog(
    exoPlayer: ExoPlayer,
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    isBackgroundEnabled: Boolean = false,
    onBackgroundToggle: () -> Unit = {}
) {
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
        OptionSelectionDialog("Качество", listOf("Авто", "1080p", "720p", "480p", "360p"), currentQuality, {
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
            onDismiss()
        }, { showQuality = false })
    }
    if (showSpeed) {
        OptionSelectionDialog("Скорость", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), exoPlayer.playbackParameters.speed, { speed ->
            exoPlayer.playbackParameters = PlaybackParameters(speed)
            onDismiss()
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

@UnstableApi
@Composable
fun MiniPlayer(video: SearchResult?, isPlaying: Boolean, exoPlayer: ExoPlayer, onClose: () -> Unit, onClick: () -> Unit) {
    val isAudio = video?.videoUrl?.endsWith(".mp3") == true || video?.videoUrl?.endsWith(".m4a") == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(100.dp, 56.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
            if (isAudio) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                AndroidView(factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; keepScreenOn = true } }, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(video?.author?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
        IconButton(onClick = {
            if (isPlaying) {
                exoPlayer.pause()
            } else {
                if (exoPlayer.playbackState == Player.STATE_ENDED || (exoPlayer.duration > 0 && exoPlayer.currentPosition >= exoPlayer.duration)) {
                    exoPlayer.seekTo(0)
                }
                exoPlayer.play()
            }
        }) {
            val icon = if (isPlaying) Icons.Default.Pause
                       else if (exoPlayer.playbackState == Player.STATE_ENDED || (exoPlayer.duration > 0 && exoPlayer.currentPosition >= exoPlayer.duration)) Icons.Default.Replay
                       else Icons.Default.PlayArrow
            Icon(icon, null)
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
    }
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}


