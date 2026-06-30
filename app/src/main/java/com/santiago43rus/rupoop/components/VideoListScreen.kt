package com.santiago43rus.rupoop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Список пуст") }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(videos) { video ->
                VideoCardItem(
                    video = video, history = null,
                    onClick = { onVideoClick(video) },
                    onAuthorClick = onAuthorClick,
                    onMoreClick = { action ->
                        when (action) {
                            "remove" -> onRemove(video)
                            "share" -> onShare(video)
                            "download" -> onDownload(video)
                        }
                    }, isEditMode = true)
            }
        }
    }
}
