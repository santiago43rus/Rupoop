package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.components.VideoCardShimmer
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.util.extractId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    author: Author?,
    authorVideos: List<SearchResult>,
    userRegistry: UserRegistry,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMoreVideos: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onToggleSubscription: (Author) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit,
    currentSort: String = "-publication_ts",
    onSortChange: (String) -> Unit = {}
) {
    val config = LocalConfiguration.current
    val columns = when {
        config.screenWidthDp >= 900 -> 3
        config.screenWidthDp >= 600 -> 2
        else -> 1
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = rememberPullToRefreshState()
        ) {
            if (authorVideos.isEmpty() && isRefreshing) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        author?.let { a ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = a.avatarUrl ?: "", contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Gray)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    a.name, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                                )
                                val isSubbed = userRegistry.subscriptions.any { it.name.equals(a.name, ignoreCase = true) }
                                Button(
                                    onClick = { onToggleSubscription(a) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSubbed) Color.Gray else Color.Red),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (isSubbed) "Вы подписаны" else "Подписаться", fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    items(6) {
                        VideoCardShimmer()
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        author?.let { a ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = a.avatarUrl ?: "", contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Gray)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    a.name, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                                )
                                val isSubbed = userRegistry.subscriptions.any { it.name.equals(a.name, ignoreCase = true) }
                                Button(
                                    onClick = { onToggleSubscription(a) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSubbed) Color.Gray else Color.Red),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (isSubbed) "Вы подписаны" else "Подписаться", fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    // Sort chips
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { },
                                label = { Text("От новых") }
                            )
                        }
                    }
                    itemsIndexed(authorVideos) { index, video ->
                        val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                        VideoCardItem(
                            video = video, history = history,
                            onClick = { onVideoClick(video, authorVideos) },
                            onAuthorClick = onAuthorClick,
                            onMoreClick = { action -> onMoreClick(video, action) }
                        )
                        if (index == authorVideos.lastIndex && !isLoadingMore && hasMoreVideos) {
                            LaunchedEffect(video.videoUrl) { onLoadMore() }
                        }
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
