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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
    modifier: Modifier = Modifier,
    seekStepDurationMs: Long = 10_000L,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onAudioTrackSelect: (AudioTrackSource) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseOverlay: () -> Unit = {},
    onOpenInfoPanel: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val playPauseFocusRequester = remember { FocusRequester() }

    var lastDownPressTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        playPauseFocusRequester.requestFocus()
    }

    // Выделение доступных сезонов
    val seasons = remember(episodes) {
        episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember(currentEpisodeIndex, episodes) {
        val currentEp = episodes.getOrNull(currentEpisodeIndex)
        mutableStateOf(currentEp?.seasonNumber ?: seasons.firstOrNull() ?: 1)
    }

    val seasonEpisodes = remember(episodes, selectedSeason, seasons) {
        if (seasons.size > 1) {
            episodes.filter { it.seasonNumber == selectedSeason }
        } else {
            episodes
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorScheme.background.copy(alpha = 0.5f),
                        colorScheme.background.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 32.dp)
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

            // Выбор сезона
            if (seasons.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Сезон",
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
                        items(seasons.size) { idx ->
                            val sNum = seasons[idx]
                            val isSelected = sNum == selectedSeason
                            Surface(
                                onClick = {
                                    selectedSeason = sNum
                                    val firstEpInSeason = episodes.indexOfFirst { it.seasonNumber == sNum }
                                    if (firstEpInSeason != -1) {
                                        onEpisodeSelect(firstEpInSeason)
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "$sNum сезон",
                                    color = colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Серии выбранного сезона
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
                        itemsIndexed(seasonEpisodes) { _, episode ->
                            val globalIndex = episodes.indexOf(episode)
                            val isSelected = globalIndex == currentEpisodeIndex
                            Surface(
                                onClick = { onEpisodeSelect(globalIndex) },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = episode.title.ifBlank { "Серия ${episode.episodeNumber}" },
                                    color = colorScheme.onSurface,
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
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = track.name,
                                    color = colorScheme.onSurface,
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
                            if (event.key == Key.DirectionUp) {
                                onCloseOverlay()
                                return@onPreviewKeyEvent true
                            }
                            val step = if (event.nativeKeyEvent.repeatCount > 2) seekStepDurationMs * 2 else seekStepDurationMs
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onSeekRelative(-step)
                                    true
                                }
                                Key.DirectionRight -> {
                                    onSeekRelative(step)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    .padding(top = 4.dp)
                    .focusRestorer(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayPrevious,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущее")
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(
                    onClick = { onSeekRelative(-seekStepDurationMs) },
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-$seekStepDurationMs мс")
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(
                    onClick = onPlayPause,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .focusRequester(playPauseFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                val now = System.currentTimeMillis()
                                if (now - lastDownPressTime < 600) {
                                    onOpenInfoPanel()
                                    true
                                } else {
                                    lastDownPressTime = now
                                    false
                                }
                            } else false
                        }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть"
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(
                    onClick = { onSeekRelative(seekStepDurationMs) },
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "+$seekStepDurationMs мс")
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(
                    onClick = onPlayNext,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующее")
                }
                Spacer(modifier = Modifier.width(28.dp))
                // Настройки видео (качество и скорость)
                IconButton(
                    onClick = onOpenSettings,
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Качество и скорость")
                }
            }
        }
    }
}
