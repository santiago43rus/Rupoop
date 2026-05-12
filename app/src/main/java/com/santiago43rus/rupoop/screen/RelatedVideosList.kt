package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.components.VideoDetails
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.util.extractId

@Composable
fun RelatedVideosList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    currentVideo: SearchResult?,
    relatedVideos: List<SearchResult>,
    userRegistry: UserRegistry,
    onAuthorClick: (Author) -> Unit,
    onToggleSub: (Author) -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit,
    alphaProgress: Float
) {
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
                onShare = onShare,
                onAddToPlaylist = onAddToPlaylist,
                onDownload = onDownload
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
}
