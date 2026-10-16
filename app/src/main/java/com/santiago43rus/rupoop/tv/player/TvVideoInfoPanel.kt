package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.tv.components.TvRow

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
    onShare: (SearchResult) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onRelatedVideoClick: (SearchResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentVideo == null) return

    val colorScheme = MaterialTheme.colorScheme
    val isLiked = registry.likedVideos.any { it.videoUrl == currentVideo.videoUrl }
    val isDisliked = registry.dislikedVideos.contains(currentVideo.videoUrl)
    val isWatchLater = registry.watchLater.any { it.videoUrl == currentVideo.videoUrl }

    val firstButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.92f))
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .onPreviewKeyEvent { event ->
                // Стрелка вверх на самом верхнем уровне закрывает панель инфо
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onClose()
                    true
                } else false
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Заголовок и автор
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentVideo.title,
                        color = colorScheme.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    currentVideo.author?.let { author ->
                        Surface(
                            onClick = { onAuthorClick(author) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Канал: ${author.name}",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Ряд быстрых действий
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Лайк
                    Button(
                        onClick = { onLike(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isLiked) colorScheme.primary else colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier.focusRequester(firstButtonFocusRequester)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Нравится"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isLiked) "Понравилось" else "Нравится")
                    }

                    // Дизлайк / Скрыть
                    Button(
                        onClick = { onDislike(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isDisliked) colorScheme.tertiary else colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Скрыть"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isDisliked) "Скрыто" else "Не нравится")
                    }

                    // Смотреть позже
                    Button(
                        onClick = { onWatchLater(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isWatchLater) colorScheme.primary else colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.WatchLater,
                            contentDescription = "Смотреть позже"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isWatchLater) "В списке" else "Позже")
                    }

                    // В плейлист
                    Button(
                        onClick = { onAddToPlaylist(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "В плейлист"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Плейлист")
                    }

                    // Поделиться (копировать ссылку)
                    Button(
                        onClick = { onShare(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Поделиться")
                    }
                }
            }

            // Ряд рекомендаций «Далее»
            if (relatedVideos.isNotEmpty()) {
                item {
                    TvRow(
                        title = "Далее / Похожие видео",
                        videos = relatedVideos,
                        onVideoClick = onRelatedVideoClick
                    )
                }
            }
        }
    }
}
