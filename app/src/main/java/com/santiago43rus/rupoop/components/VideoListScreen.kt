package com.santiago43rus.rupoop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult

@Composable
fun VideoListScreen(
    videos: List<SearchResult>,
    onVideoClick: (SearchResult) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onShare: (SearchResult) -> Unit,
    onRemove: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyPlaceholderView(
            modifier = Modifier.fillMaxSize(),
            title = "Список пуст",
            message = "В этом разделе пока нет добавленных видео"
        )
    } else {
        val config = LocalConfiguration.current
        val columns = when {
            config.screenWidthDp >= 900 -> 3
            config.screenWidthDp >= 600 -> 2
            else -> 1
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = if (columns == 1) androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp) else androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = if (columns == 1) androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp) else androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalArrangement = if (columns == 1) androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp) else androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                VideoCardItem(
                    video = video, history = null,
                    onClick = { onVideoClick(video) },
                    onAuthorClick = onAuthorClick,
                    onMoreClick = { action ->
                        when (action) {
                            "remove" -> onRemove(video)
                        }
                    },
                    isEditMode = true,
                    isEditOnlyMode = true
                )
            }
        }
    }
}
