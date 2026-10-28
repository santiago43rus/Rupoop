package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.WatchHistoryItem

@Composable
fun TvRow(
    title: String,
    videos: List<SearchResult>,
    onVideoClick: (SearchResult) -> Unit,
    onVideoMenuClick: ((SearchResult) -> Unit)? = null,
    watchHistory: List<WatchHistoryItem>? = null,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            TvSectionTitle(title = title)
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            items(videos, key = { it.videoUrl }) { video ->
                TvVideoCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onMenuClick = { onVideoMenuClick?.invoke(video) },
                    watchHistory = watchHistory,
                    modifier = Modifier.width(260.dp)
                )
            }
        }
    }
}
