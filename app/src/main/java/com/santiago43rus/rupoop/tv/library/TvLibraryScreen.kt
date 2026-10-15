package com.santiago43rus.rupoop.tv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Playlist
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvRow
import com.santiago43rus.rupoop.tv.components.TvTabs

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLibraryScreen(
    historyVideos: List<SearchResult>,
    likedVideos: List<SearchResult>,
    watchLaterVideos: List<SearchResult>,
    playlists: List<Playlist>,
    hiddenVideos: List<SearchResult>,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onRemoveFromHistory: (SearchResult) -> Unit,
    onRemoveFromWatchLater: (SearchResult) -> Unit,
    onRestoreHiddenVideo: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("История", "Понравившиеся", "Смотреть позже", "Плейлисты", "Скрытые")
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvTabs(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            when (selectedTab) {
                0 -> { // История
                    if (historyVideos.isEmpty()) {
                        item { Text("История просмотров пуста", color = Color.Gray, fontSize = 16.sp) }
                    } else {
                        val chunks = historyVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Недавно просмотренные" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, historyVideos) }
                                )
                            }
                        }
                    }
                }
                1 -> { // Понравившиеся
                    if (likedVideos.isEmpty()) {
                        item { Text("Нет понравившихся видео", color = Color.Gray, fontSize = 16.sp) }
                    } else {
                        val chunks = likedVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Понравившиеся" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, likedVideos) }
                                )
                            }
                        }
                    }
                }
                2 -> { // Смотреть позже
                    if (watchLaterVideos.isEmpty()) {
                        item { Text("Список «Смотреть позже» пуст", color = Color.Gray, fontSize = 16.sp) }
                    } else {
                        val chunks = watchLaterVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Смотреть позже" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, watchLaterVideos) }
                                )
                            }
                        }
                    }
                }
                3 -> { // Плейлисты
                    if (playlists.isEmpty()) {
                        item { Text("Нет созданных плейлистов", color = Color.Gray, fontSize = 16.sp) }
                    } else {
                        playlists.forEach { playlist ->
                            item {
                                TvRow(
                                    title = "${playlist.name} (${playlist.videos.size})",
                                    videos = playlist.videos,
                                    onVideoClick = { onVideoSelected(it, playlist.videos) }
                                )
                            }
                        }
                    }
                }
                4 -> { // Скрытые
                    if (hiddenVideos.isEmpty()) {
                        item { Text("Список скрытых видео пуст", color = Color.Gray, fontSize = 16.sp) }
                    } else {
                        val chunks = hiddenVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Скрытые видео" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, hiddenVideos) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
