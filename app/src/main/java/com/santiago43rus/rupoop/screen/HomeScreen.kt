package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.components.VideoItem
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.util.extractId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeVideos: List<SearchResult>,
    userRegistry: UserRegistry,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState()
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(homeVideos) { index, video ->
                val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                VideoItem(
                    video, history,
                    onClick = { onVideoClick(video, homeVideos) },
                    onAuthorClick = onAuthorClick,
                    onMoreClick = { action -> onMoreClick(video, action) }
                )
                if (index == homeVideos.lastIndex && !isLoadingMore) {
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

