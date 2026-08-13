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
    onLike: (SearchResult) -> Unit,
    onDislike: (SearchResult) -> Unit = {},
    onShare: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onVideoClick: (SearchResult, List<SearchResult>) -> Unit,
    onMoreClick: (SearchResult, String) -> Unit,
    alphaProgress: Float,
    isBackgroundEnabled: Boolean = false,
    onBackgroundPlayToggle: () -> Unit = {},
    showVideoDetails: Boolean = true,
    useTwoColumns: Boolean = false
) {
    val isLocalFile = currentVideo?.videoUrl != null && !currentVideo.videoUrl.startsWith("http")

    LazyColumn(
        modifier = modifier.graphicsLayer {
            alpha = (1f - alphaProgress * 2f).coerceIn(0f, 1f)
        },
        state = listState
    ) {
        if (showVideoDetails) {
            item {
                VideoDetails(
                    currentVideo, userRegistry,
                    onAuthorClick = onAuthorClick,
                    onToggleSub = onToggleSub,
                    onLike = onLike,
                    onDislike = onDislike,
                    onShare = onShare,
                    onAddToPlaylist = onAddToPlaylist,
                    onDownload = onDownload,
                    isBackgroundEnabled = isBackgroundEnabled,
                    onBackgroundPlayToggle = onBackgroundPlayToggle
                )
                if (!isLocalFile) {
                    HorizontalDivider()
                    Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }
        } else if (!isLocalFile) {
            item {
                Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }
        if (!isLocalFile) {
            if (relatedVideos.isEmpty()) {
                if (useTwoColumns) {
                    items(3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { com.santiago43rus.rupoop.components.VideoCardShimmer() }
                            Box(modifier = Modifier.weight(1f)) { com.santiago43rus.rupoop.components.VideoCardShimmer() }
                        }
                    }
                } else {
                    items(6) {
                        com.santiago43rus.rupoop.components.VideoCardShimmer()
                    }
                }
            } else if (useTwoColumns) {
                val pairs = relatedVideos.chunked(2)
                items(pairs) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (video in pair) {
                            val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                            Box(modifier = Modifier.weight(1f)) {
                                VideoCardItem(
                                    video = video,
                                    history = history,
                                    onClick = { onVideoClick(video, relatedVideos) },
                                    onAuthorClick = onAuthorClick,
                                    onMoreClick = { action -> onMoreClick(video, action) }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
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
    }
}
