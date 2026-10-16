package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.parser.AudioTrackSource
import com.santiago43rus.rupoop.parser.Episode
import com.santiago43rus.rupoop.util.formatDuration

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerOverlay(
    currentVideo: SearchResult?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    episodes: List<Episode>,
    currentEpisodeIndex: Int,
    audioTracks: List<AudioTrackSource>,
    selectedAudioTrackId: String?,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onAudioTrackSelect: (AudioTrackSource) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.85f))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок и автор видео
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = currentVideo?.title ?: "",
                    color = colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentVideo?.author?.name ?: "",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Серии (для Lordfilm и сериалов)
            if (episodes.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Серии",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val isSelected = index == currentEpisodeIndex
                            Surface(
                                onClick = { onEpisodeSelect(index) },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = episode.title.ifBlank { "Серия ${episode.episodeNumber}" },
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Аудиодорожки / Озвучки
            if (audioTracks.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Озвучка / Дорожка",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(audioTracks.size) { idx ->
                            val track = audioTracks[idx]
                            val isSelected = track.id == selectedAudioTrackId || (selectedAudioTrackId == null && idx == 0)
                            Surface(
                                onClick = { onAudioTrackSelect(track) },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = track.name,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Фокусируемый Прогресс-бар с текущей позицией
            var isProgressFocused by remember { mutableStateOf(false) }
            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isProgressFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (isProgressFocused && event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onSeekRelative(-10_000L)
                                    true
                                }
                                Key.DirectionRight -> {
                                    onSeekRelative(10_000L)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isProgressFocused) 10.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.border.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = currentPositionMs / 1000
                    val durSec = durationMs / 1000
                    Text(
                        text = formatDuration(posSec),
                        color = if (isProgressFocused) colorScheme.primary else colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = if (isProgressFocused) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = formatDuration(durSec),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // Кнопки управления транспортом
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущее", tint = colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { onSeekRelative(-10_000L) }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-10 сек", tint = colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = onPlayPause,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.primary,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { onSeekRelative(10_000L) }) {
                    Icon(Icons.Default.FastForward, contentDescription = "+10 сек", tint = colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onPlayNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующее", tint = colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(24.dp))
                // Настройки видео (качество и скорость)
                IconButton(
                    onClick = onOpenSettings,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Качество и скорость", tint = colorScheme.onBackground)
                }
            }
        }
    }
}
