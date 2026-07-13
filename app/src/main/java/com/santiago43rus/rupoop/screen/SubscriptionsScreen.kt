package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.components.VideoCardShimmer
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.util.extractId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    userRegistry: UserRegistry,
    subscriptionVideos: List<SearchResult>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMoreVideos: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit,
    listState: LazyGridState = rememberLazyGridState()
) {
    val config = LocalConfiguration.current
    val columns = when {
        config.screenWidthDp >= 900 -> 3
        config.screenWidthDp >= 600 -> 2
        else -> 1
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState()
    ) {
        if (userRegistry.subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("У вас пока нет подписок")
            }
        } else {
            if (subscriptionVideos.isEmpty() && isRefreshing) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            items(userRegistry.subscriptions) { author ->
                                Column(
                                    Modifier.padding(end = 16.dp).width(70.dp).clickable { onAuthorClick(author) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = author.avatarUrl ?: "", contentDescription = null,
                                        modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray)
                                    )
                                    Text(author.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    items(6) {
                        VideoCardShimmer()
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    // Stories (Authors row) spans across all columns!
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            items(userRegistry.subscriptions) { author ->
                                Column(
                                    Modifier.padding(end = 16.dp).width(70.dp).clickable { onAuthorClick(author) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = author.avatarUrl ?: "", contentDescription = null,
                                        modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray)
                                    )
                                    Text(author.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    itemsIndexed(subscriptionVideos) { index, video ->
                        val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                        VideoCardItem(
                            video = video, history = history,
                            onClick = { onVideoClick(video, subscriptionVideos) },
                            onAuthorClick = onAuthorClick,
                            onMoreClick = { action -> onMoreClick(video, action) }
                        )
                        if (index == subscriptionVideos.lastIndex && !isLoadingMore && hasMoreVideos) {
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
