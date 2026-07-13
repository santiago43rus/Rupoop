package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.components.VideoCardShimmer
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.util.extractId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeedScreen(
    videos: List<SearchResult>,
    userRegistry: UserRegistry,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
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
        if (videos.isEmpty() && isRefreshing) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
            ) {
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
                itemsIndexed(videos) { index, video ->
                    val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                    VideoCardItem(
                        video = video,
                        history = history,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onVideoClick(video, videos) },
                        onAuthorClick = onAuthorClick,
                        onMoreClick = { action -> onMoreClick(video, action) }
                    )
                    if (index == videos.lastIndex && !isLoadingMore) {
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
