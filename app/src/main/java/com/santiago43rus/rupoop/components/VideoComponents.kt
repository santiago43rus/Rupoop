package com.santiago43rus.rupoop.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.util.formatDuration

@Composable
fun VideoItem(
    video: SearchResult,
    history: WatchHistoryItem?,
    onClick: () -> Unit,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (String) -> Unit,
    isEditMode: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(0.38f)) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.DarkGray),
                    contentScale = ContentScale.Fit
                )
                if (history != null && history.totalDuration > 0) {
                    LinearProgressIndicator(
                        progress = { history.progress.toFloat() / history.totalDuration },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = Color.Red,
                        trackColor = Color.Gray.copy(alpha = 0.5f)
                    )
                }
                video.duration?.let { durSeconds ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
                    ) {
                        Text(
                            text = formatDuration(durSeconds.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            VideoMetaBlock(
                video = video,
                onAuthorClick = onAuthorClick,
                onMoreClick = onMoreClick,
                isEditMode = isEditMode,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                compact = true,
                modifier = Modifier.weight(0.62f)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(bottom = 12.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 10f).background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                if (history != null && history.totalDuration > 0) {
                    LinearProgressIndicator(
                        progress = { history.progress.toFloat() / history.totalDuration },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = Color.Red,
                        trackColor = Color.Gray.copy(alpha = 0.5f)
                    )
                }
                video.duration?.let { durSeconds ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
                    ) {
                        Text(
                            text = formatDuration(durSeconds.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            VideoMetaBlock(
                video = video,
                onAuthorClick = onAuthorClick,
                onMoreClick = onMoreClick,
                isEditMode = isEditMode,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it }
            )
        }
    }
}

@Composable
private fun VideoMetaBlock(
    video: SearchResult,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (String) -> Unit,
    isEditMode: Boolean,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (compact) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${video.author?.name ?: "Автор"} • Rutube",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { video.author?.let { onAuthorClick(it) } }
                )
            }
            Box {
                IconButton(onClick = { onShowMenuChange(true) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                    offset = DpOffset(x = (-12).dp, y = 10.dp)
                ) {
                    if (!isEditMode) {
                        DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { onShowMenuChange(false); onMoreClick("playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                        DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { onShowMenuChange(false); onMoreClick("later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                    } else {
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { onShowMenuChange(false); onMoreClick("remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { onShowMenuChange(false); onMoreClick("download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { onShowMenuChange(false); onMoreClick("share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                }
            }
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = video.author?.avatarUrl ?: "https://rutube.ru/static/img/default-avatar.png",
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)
                    .clickable { video.author?.let { onAuthorClick(it) } },
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${video.author?.name ?: "Автор"} • Rutube",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { video.author?.let { onAuthorClick(it) } }
                )
            }
            Box {
                IconButton(onClick = { onShowMenuChange(true) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onShowMenuChange(false) }) {
                    if (!isEditMode) {
                        DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { onShowMenuChange(false); onMoreClick("playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                        DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { onShowMenuChange(false); onMoreClick("later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                    } else {
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { onShowMenuChange(false); onMoreClick("remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { onShowMenuChange(false); onMoreClick("download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { onShowMenuChange(false); onMoreClick("share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                }
            }
        }
    }
}

@Composable
fun VideoDetails(
    video: SearchResult?,
    registry: UserRegistry,
    onAuthorClick: (Author) -> Unit,
    onToggleSub: (Author) -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Text(video?.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            AsyncImage(
                model = video?.author?.avatarUrl ?: "", contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)
                    .clickable { video?.author?.let { onAuthorClick(it) } }
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { video?.author?.let { onAuthorClick(it) } }) {
                Text(video?.author?.name ?: "Автор", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Rutube", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            val isSubbed = registry.subscriptions.any { it.name.equals(video?.author?.name, ignoreCase = true) }
            Button(
                onClick = { video?.author?.let { onToggleSub(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = if (isSubbed) Color.Gray else Color.Red),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(if (isSubbed) "Вы подписаны" else "Подписаться", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            val isLiked = registry.likedVideos.any { it.videoUrl == video?.videoUrl }
            DetailAction(if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Лайк", color = if (isLiked) Color.Red else LocalContentColor.current, onClick = onLike)
            DetailAction(Icons.Outlined.Download, "Скачать", onClick = onDownload)
            DetailAction(Icons.Default.Share, "Поделиться", onClick = onShare)
            DetailAction(Icons.AutoMirrored.Filled.PlaylistAdd, "В плейлист", onClick = onAddToPlaylist)
        }
    }
}

@Composable
fun DetailAction(icon: ImageVector, label: String, color: Color = LocalContentColor.current, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

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
                VideoItem(video, null, onClick = { onVideoClick(video) }, onAuthorClick = onAuthorClick, onMoreClick = { action ->
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

