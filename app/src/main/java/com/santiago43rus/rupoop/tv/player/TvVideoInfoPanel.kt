package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.parser.AudioTrackSource
import com.santiago43rus.rupoop.parser.Episode
import com.santiago43rus.rupoop.tv.components.TvRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoInfoPanel(
    currentVideo: SearchResult?,
    registry: UserRegistry,
    relatedVideos: List<SearchResult>,
    onLike: (SearchResult) -> Unit,
    onDislike: (SearchResult) -> Unit,
    onWatchLater: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onToggleSubscription: (Author) -> Unit,
    onRelatedVideoClick: (SearchResult) -> Unit,
    onVideoMenuClick: (SearchResult) -> Unit,
    episodes: List<Episode> = emptyList(),
    currentEpisodeIndex: Int = 0,
    audioTracks: List<AudioTrackSource> = emptyList(),
    selectedAudioTrackId: String? = null,
    onEpisodeSelect: (Int) -> Unit = {},
    onAudioTrackSelect: (AudioTrackSource) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentVideo == null) return

    val colorScheme = MaterialTheme.colorScheme
    val videoId = currentVideo.videoUrl.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?") ?: ""
    val isLiked = registry.likedVideos.any { it.videoUrl == currentVideo.videoUrl }
    val isDisliked = registry.dislikedVideos.contains(videoId) ||
            registry.dislikedVideos.contains(currentVideo.videoUrl) ||
            registry.dislikedVideosList.any { it.videoUrl == currentVideo.videoUrl }
    val isWatchLater = registry.watchLater.any { it.videoUrl == currentVideo.videoUrl }
    val isSubscribed = currentVideo.author?.let { author ->
        registry.subscriptions.any { it.name == author.name }
    } ?: false

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

    val firstButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorScheme.background.copy(alpha = 0.6f),
                        colorScheme.background.copy(alpha = 0.96f)
                    )
                )
            )
            .padding(horizontal = 40.dp, vertical = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок и автор
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentVideo.title,
                        color = colorScheme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    currentVideo.author?.let { author ->
                        Row(
                            modifier = Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    onClose()
                                    true
                                } else false
                            },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { onAuthorClick(author) },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    focusedContentColor = colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = "Канал: ${author.name}",
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            // Кнопка подписки
                            Button(
                                onClick = { onToggleSubscription(author) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSubscribed) colorScheme.surfaceVariant else colorScheme.surface,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.surfaceVariant,
                                    focusedContentColor = colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSubscribed) "Подписка оформлена" else "Подписаться")
                            }
                        }
                    }
                }
            }

            // Ряд быстрых действий
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer()
                        .then(
                            if (currentVideo.author == null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        onClose()
                                        true
                                    } else false
                                }
                            } else Modifier
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Лайк
                    Button(
                        onClick = { onLike(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isLiked) colorScheme.surfaceVariant else colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            focusedContentColor = colorScheme.onSurface
                        ),
                        modifier = Modifier.focusRequester(firstButtonFocusRequester)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Нравится"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isLiked) "Понравилось" else "Нравится")
                    }

                    // Дизлайк / Скрыть
                    Button(
                        onClick = { onDislike(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isDisliked) colorScheme.surfaceVariant else colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            focusedContentColor = colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Не нравится"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isDisliked) "Скрыто" else "Не нравится")
                    }

                    // Смотреть позже
                    Button(
                        onClick = { onWatchLater(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isWatchLater) colorScheme.surfaceVariant else colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            focusedContentColor = colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.WatchLater, contentDescription = "Позже")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isWatchLater) "В списке" else "Позже")
                    }

                    // В плейлист
                    Button(
                        onClick = { onAddToPlaylist(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            focusedContentColor = colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "В плейлист")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Плейлист")
                    }
                }
            }

            // Серии и сезоны (Lordfilm и сериалы)
            if (episodes.isNotEmpty()) {
                if (seasons.size > 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Сезоны",
                                color = colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().focusRestorer(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                            containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                            focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                            focusedContentColor = androidx.compose.ui.graphics.Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "$sNum сезон",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (seasons.size > 1) "Серии ($selectedSeason сезон)" else "Серии",
                            color = colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(seasonEpisodes) { _, episode ->
                                val globalIndex = episodes.indexOf(episode)
                                val isSelected = globalIndex == currentEpisodeIndex
                                Surface(
                                    onClick = { onEpisodeSelect(globalIndex) },
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                        focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                        focusedContentColor = androidx.compose.ui.graphics.Color.White
                                    )
                                ) {
                                    Text(
                                        text = episode.title.ifBlank { "Серия ${episode.episodeNumber}" },
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Озвучка / Перевод (Lordfilm и балансеры)
            if (audioTracks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Озвучка / Перевод (${audioTracks.size})",
                            color = colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(audioTracks) { idx, track ->
                                val isSelected = track.id == selectedAudioTrackId || (selectedAudioTrackId == null && idx == 0)
                                val labelText = if (track.playerName != null) "${track.name} [${track.playerName}]" else track.name
                                Surface(
                                    onClick = { onAudioTrackSelect(track) },
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                        focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                        focusedContentColor = androidx.compose.ui.graphics.Color.White
                                    )
                                ) {
                                    Text(
                                        text = labelText,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ряд рекомендаций «Далее»
            if (relatedVideos.isNotEmpty()) {
                item {
                    TvRow(
                        title = "Далее / Похожие видео",
                        videos = relatedVideos,
                        onVideoClick = onRelatedVideoClick,
                        onVideoMenuClick = onVideoMenuClick
                    )
                }
            }
        }
    }
}
