package com.santiago43rus.rupoop.tv.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvVideoCard

@Composable
fun TvHomeScreen(
    homeVideos: List<SearchResult>,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onVideoMenuClick: ((SearchResult) -> Unit)? = null,
    onLoadMoreHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(homeVideos, key = { _, v -> v.videoUrl }) { index, video ->
            TvVideoCard(
                video = video,
                onClick = { onVideoSelected(video, homeVideos) },
                onMenuClick = { onVideoMenuClick?.invoke(video) }
            )

            if (index == homeVideos.lastIndex) {
                LaunchedEffect(Unit) {
                    onLoadMoreHome()
                }
            }
        }
    }
}
