package com.santiago43rus.rupoop.tv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Playlist
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvDeleteConfirmationDialog
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
    initialTab: Int = 0,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onVideoMenuClick: (SearchResult) -> Unit,
    onRemoveFromHistory: (SearchResult) -> Unit,
    onRemoveFromWatchLater: (SearchResult) -> Unit,
    onRestoreHiddenVideo: (SearchResult) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("История", "Понравившиеся", "Смотреть позже", "Плейлисты", "Скрытые")
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val colorScheme = MaterialTheme.colorScheme

    var pendingDeleteItem by remember { mutableStateOf<Any?>(null) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogMessage by remember { mutableStateOf("") }

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
                        item { Text("История просмотров пуста", color = colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                    } else {
                        val chunks = historyVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Недавно просмотренные" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, historyVideos) },
                                    onVideoMenuClick = onVideoMenuClick
                                )
                            }
                        }
                    }
                }
                1 -> { // Понравившиеся
                    if (likedVideos.isEmpty()) {
                        item { Text("Нет понравившихся видео", color = colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                    } else {
                        val chunks = likedVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Понравившиеся" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, likedVideos) },
                                    onVideoMenuClick = onVideoMenuClick
                                )
                            }
                        }
                    }
                }
                2 -> { // Смотреть позже
                    if (watchLaterVideos.isEmpty()) {
                        item { Text("Список «Смотреть позже» пуст", color = colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                    } else {
                        val chunks = watchLaterVideos.chunked(4)
                        chunks.forEachIndexed { idx, rowVideos ->
                            item {
                                TvRow(
                                    title = if (idx == 0) "Смотреть позже" else "",
                                    videos = rowVideos,
                                    onVideoClick = { onVideoSelected(it, watchLaterVideos) },
                                    onVideoMenuClick = onVideoMenuClick
                                )
                            }
                        }
                    }
                }
                3 -> { // Плейлисты
                    if (playlists.isEmpty()) {
                        item { Text("Нет созданных плейлистов", color = colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                    } else {
                        playlists.forEach { playlist ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${playlist.name} (${playlist.videos.size})",
                                        color = colorScheme.onBackground,
                                        fontSize = 20.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            deleteDialogTitle = "Удалить плейлист"
                                            deleteDialogMessage = "Удалить плейлист «${playlist.name}»?"
                                            pendingDeleteItem = playlist
                                        },
                                        colors = IconButtonDefaults.colors(
                                            containerColor = colorScheme.surfaceVariant,
                                            contentColor = colorScheme.onSurface,
                                            focusedContainerColor = colorScheme.primary,
                                            focusedContentColor = colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить плейлист")
                                    }
                                }
                                TvRow(
                                    title = "",
                                    videos = playlist.videos,
                                    onVideoClick = { onVideoSelected(it, playlist.videos) },
                                    onVideoMenuClick = onVideoMenuClick
                                )
                            }
                        }
                    }
                }
                4 -> { // Скрытые
                    if (hiddenVideos.isEmpty()) {
                        item { Text("Список скрытых видео пуст", color = colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                    } else {
                        items(hiddenVideos, key = { it.videoUrl }) { video ->
                            Surface(
                                onClick = { onVideoSelected(video, hiddenVideos) },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.title, color = colorScheme.onSurface, fontSize = 16.sp, maxLines = 1)
                                        Text(video.author?.name ?: "", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = { onRestoreHiddenVideo(video) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                            focusedContainerColor = colorScheme.primary,
                                            focusedContentColor = colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Вернуть в ленту")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val currentItem = pendingDeleteItem
    if (currentItem != null) {
        TvDeleteConfirmationDialog(
            title = deleteDialogTitle,
            message = deleteDialogMessage,
            onConfirm = {
                if (currentItem is Playlist) {
                    onDeletePlaylist(currentItem)
                }
                pendingDeleteItem = null
            },
            onDismiss = { pendingDeleteItem = null }
        )
    }
}
