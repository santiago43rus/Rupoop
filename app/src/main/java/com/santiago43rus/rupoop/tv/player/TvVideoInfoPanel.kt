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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.tv.components.TvRow
import com.santiago43rus.rupoop.tv.components.TvSectionTitle

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
    modifier: Modifier = Modifier
) {
    if (currentVideo == null) return

    val isLiked = registry.likedVideos.any { it.videoUrl == currentVideo.videoUrl }
    val isDisliked = registry.dislikedVideos.contains(currentVideo.videoUrl)
    val isWatchLater = registry.watchLater.any { it.videoUrl == currentVideo.videoUrl }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60D1117))
            .padding(horizontal = 48.dp, vertical = 32.dp)
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
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    currentVideo.author?.let { author ->
                        Surface(
                            onClick = { onAuthorClick(author) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF21262D),
                                focusedContainerColor = Color(0xFF30363D)
                            )
                        ) {
                            Text(
                                text = "Канал: ${author.name}",
                                color = Color(0xFF58A6FF),
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
                            containerColor = if (isLiked) Color(0xFF238636) else Color(0xFF21262D),
                            focusedContainerColor = if (isLiked) Color(0xFF2EA043) else Color(0xFF30363D)
                        )
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Нравится",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isLiked) "Понравилось" else "Нравится", color = Color.White)
                    }

                    // Дизлайк / Скрыть
                    Button(
                        onClick = { onDislike(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isDisliked) Color(0xFFDA3633) else Color(0xFF21262D),
                            focusedContainerColor = if (isDisliked) Color(0xFFF85149) else Color(0xFF30363D)
                        )
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Скрыть",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isDisliked) "Скрыто" else "Не нравится", color = Color.White)
                    }

                    // Смотреть позже
                    Button(
                        onClick = { onWatchLater(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isWatchLater) Color(0xFF1F6FEB) else Color(0xFF21262D),
                            focusedContainerColor = if (isWatchLater) Color(0xFF388BFD) else Color(0xFF30363D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.WatchLater,
                            contentDescription = "Смотреть позже",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isWatchLater) "В списке" else "Позже", color = Color.White)
                    }

                    // В плейлист
                    Button(
                        onClick = { onAddToPlaylist(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF21262D),
                            focusedContainerColor = Color(0xFF30363D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "В плейлист",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Плейлист", color = Color.White)
                    }

                    // Поделиться (копировать ссылку)
                    Button(
                        onClick = { onShare(currentVideo) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF21262D),
                            focusedContainerColor = Color(0xFF30363D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Поделиться", color = Color.White)
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
