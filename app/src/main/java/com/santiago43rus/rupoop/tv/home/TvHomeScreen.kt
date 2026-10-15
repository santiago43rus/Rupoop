package com.santiago43rus.rupoop.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvRow

@Composable
fun TvHomeScreen(
    homeVideos: List<SearchResult>,
    historyVideos: List<SearchResult>,
    likedVideos: List<SearchResult>,
    liveVideos: List<SearchResult>,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onLoadMoreHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
    ) {
        // Ряд 1: История просмотра / Продолжить просмотр
        if (historyVideos.isNotEmpty()) {
            item {
                TvRow(
                    title = "История просмотра",
                    videos = historyVideos,
                    onVideoClick = { onVideoSelected(it, historyVideos) }
                )
            }
        }

        // Ряд 2: Прямые трансляции
        if (liveVideos.isNotEmpty()) {
            item {
                TvRow(
                    title = "Прямые трансляции",
                    videos = liveVideos,
                    onVideoClick = { onVideoSelected(it, liveVideos) }
                )
            }
        }

        // Ряд 3: Рекомендации (разбитые на ряды по категориям / частям)
        if (homeVideos.isNotEmpty()) {
            val chunks = homeVideos.chunked(4)
            chunks.forEachIndexed { index, rowVideos ->
                item {
                    if (index == chunks.size - 1) {
                        LaunchedEffect(Unit) {
                            onLoadMoreHome()
                        }
                    }
                    val title = when (index) {
                        0 -> "Рекомендации"
                        1 -> "Популярное"
                        2 -> "Новинки"
                        else -> "Ещё видео"
                    }
                    TvRow(
                        title = title,
                        videos = rowVideos,
                        onVideoClick = { onVideoSelected(it, homeVideos) }
                    )
                }
            }
        }

        // Ряд 4: Понравившиеся видео
        if (likedVideos.isNotEmpty()) {
            item {
                TvRow(
                    title = "Понравившиеся видео",
                    videos = likedVideos,
                    onVideoClick = { onVideoSelected(it, likedVideos) }
                )
            }
        }
    }
}
