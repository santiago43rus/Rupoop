package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок и автор видео
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = currentVideo?.title ?: "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentVideo?.author?.name ?: "",
                    color = Color(0xFF8B949E),
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
                        color = Color(0xFF8B949E),
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
                                    containerColor = if (isSelected) Color(0xFF58A6FF) else Color(0xFF21262D),
                                    focusedContainerColor = if (isSelected) Color(0xFF79B8FF) else Color(0xFF30363D)
                                )
                            ) {
                                Text(
                                    text = episode.title.ifBlank { "Серия ${episode.episodeNumber}" },
                                    color = if (isSelected) Color.Black else Color.White,
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
                        color = Color(0xFF8B949E),
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
                                    containerColor = if (isSelected) Color(0xFF238636) else Color(0xFF21262D),
                                    focusedContainerColor = if (isSelected) Color(0xFF2EA043) else Color(0xFF30363D)
                                )
                            ) {
                                Text(
                                    text = track.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Прогресс-бар с текущей позицией
            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF58A6FF),
                    trackColor = Color(0xFF30363D)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = currentPositionMs / 1000
                    val durSec = durationMs / 1000
                    Text(
                        text = formatDuration(posSec),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatDuration(durSec),
                        color = Color(0xFF8B949E),
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
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущее", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { onSeekRelative(-10_000L) }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-10 сек", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = onPlayPause,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color(0xFF58A6FF),
                        focusedContainerColor = Color(0xFF79B8FF)
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { onSeekRelative(10_000L) }) {
                    Icon(Icons.Default.FastForward, contentDescription = "+10 сек", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onPlayNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующее", tint = Color.White)
                }
            }
        }
    }
}
