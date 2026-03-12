package com.santiago43rus.rupoop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.util.formatDuration

@Composable
fun LibraryScreen(
    registry: UserRegistry,
    onVideoClick: (WatchHistoryItem) -> Unit,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (WatchHistoryItem, String) -> Unit,
    onActionClick: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryHeader(Icons.Default.History, "История")
                Spacer(Modifier.weight(1f))
                Text("Все", color = Color.Red, modifier = Modifier.clickable { onActionClick("history") })
            }
            LazyRow(
                Modifier.fillMaxWidth().height(160.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                items(registry.watchHistory.take(10)) { item ->
                    var showMenu by remember { mutableStateOf(false) }
                    Column(Modifier.width(160.dp).padding(end = 8.dp)) {
                        Box(Modifier.fillMaxWidth().height(90.dp).clickable { onVideoClick(item) }) {
                            AsyncImage(
                                model = item.thumbnailUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (item.totalDuration > 0) {
                                LinearProgressIndicator(
                                    progress = { item.progress.toFloat() / item.totalDuration },
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).padding(horizontal = 4.dp),
                                    color = Color.Red, trackColor = Color.Gray.copy(alpha = 0.5f)
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp)
                                ) {
                                    Text(
                                        text = formatDuration(item.totalDuration / 1000),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = item.authorAvatarUrl ?: "https://rutube.ru/static/img/default-avatar.png",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray).clickable {
                                    onAuthorClick(Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))
                                },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f).clickable { onVideoClick(item) }) {
                                Text(
                                    item.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    item.authorName ?: "", maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Удалить из истории") }, onClick = { showMenu = false; onMoreClick(item, "remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { showMenu = false; onMoreClick(item, "later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                                    DropdownMenuItem(text = { Text("В плейлист") }, onClick = { showMenu = false; onMoreClick(item, "playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { showMenu = false; onMoreClick(item, "download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { showMenu = false; onMoreClick(item, "share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            LibraryRow(Icons.Default.ThumbUp, "Ваши лайки", registry.likedVideos.size.toString()) { onActionClick("liked") }
            LibraryRow(Icons.Default.Schedule, "Смотреть позже", registry.watchLater.size.toString()) { onActionClick("later") }
            LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Ваши плейлисты", registry.playlists.size.toString()) { onActionClick("playlists") }
        }
    }
}

@Composable
fun LibraryHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LibraryRow(icon: ImageVector, title: String, count: String, onAction: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f)); Text(count, color = Color.Gray)
        if (onAction != null) IconButton(onClick = onAction) { Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

