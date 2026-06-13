package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.santiago43rus.rupoop.components.VideoCardItem
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
    listState: LazyListState = rememberLazyListState()
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState()
    ) {
        if (isLandscape) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(videos) { index, video ->
                    val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                    VideoCardItem(
                        video = video,
                        history = history,
                        modifier = Modifier.padding(4.dp),
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
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(videos) { index, video ->
                    val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                    VideoCardItem(
                        video = video,
                        history = history,
                        modifier = Modifier,
                        onClick = { onVideoClick(video, videos) },
                        onAuthorClick = onAuthorClick,
                        onMoreClick = { action -> onMoreClick(video, action) }
                    )
                    if (index == videos.lastIndex && !isLoadingMore) {
                        LaunchedEffect(video.videoUrl) { onLoadMore() }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
