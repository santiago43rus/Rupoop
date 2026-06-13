package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.components.VideoDetails
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.CommentItem
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.util.extractId
import com.santiago43rus.rupoop.util.formatTimeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedVideosList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    currentVideo: SearchResult?,
    relatedVideos: List<SearchResult>,
    userRegistry: UserRegistry,
    onAuthorClick: (Author) -> Unit,
    onToggleSub: (Author) -> Unit,
    onLike: (SearchResult) -> Unit,
    onDislike: (SearchResult) -> Unit = {},
    onShare: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit,
    alphaProgress: Float,
    comments: List<CommentItem> = emptyList(),
    isLoadingComments: Boolean = false,
    commentsCount: Int = 0
) {
    var showCommentsSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.graphicsLayer {
            alpha = (1f - alphaProgress * 2f).coerceIn(0f, 1f)
        },
        state = listState
    ) {
        item {
            VideoDetails(
                currentVideo, userRegistry,
                onAuthorClick = onAuthorClick,
                onToggleSub = onToggleSub,
                onLike = onLike,
                onDislike = onDislike,
                onShare = onShare,
                onAddToPlaylist = onAddToPlaylist,
                onDownload = onDownload
            )
            
            CommentsPreviewSection(
                comments = comments,
                isLoading = isLoadingComments,
                count = commentsCount,
                onClick = { showCommentsSheet = true }
            )

            HorizontalDivider()
            Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
        }
        items(relatedVideos) { video ->
            val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
            VideoCardItem(
                video = video,
                history = history,
                onClick = { onVideoClick(video, relatedVideos) },
                onAuthorClick = onAuthorClick,
                onMoreClick = { action -> onMoreClick(video, action) }
            )
        }
    }

    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Комментарии (${commentsCount})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showCommentsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider()
                if (isLoadingComments && comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                } else if (comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет комментариев", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(comments) { comment ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = comment.author?.avatarUrl ?: "",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = comment.author?.name ?: "Аноним",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatTimeAgo(comment.createdTs),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = comment.text ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentsPreviewSection(
    comments: List<CommentItem>,
    isLoading: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Комментарии • $count",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Развернуть",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading && comments.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                    color = Color.Red
                )
            } else if (comments.isNotEmpty()) {
                val firstComment = comments.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = firstComment.author?.avatarUrl ?: "",
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = firstComment.text ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "Нет комментариев. Будьте первыми!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
