package com.santiago43rus.rupoop.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.util.formatDuration
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount

@Composable
fun VideoDetails(
    video: SearchResult?,
    registry: UserRegistry,
    onAuthorClick: (Author) -> Unit,
    onToggleSub: (Author) -> Unit,
    onLike: (SearchResult) -> Unit,
    onDislike: (SearchResult) -> Unit = {},
    onShare: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onBackgroundPlayToggle: () -> Unit = {},
    isBackgroundEnabled: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(12.dp)) {
        Text(
            text = video?.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded }
        )
        Spacer(Modifier.height(8.dp))
        val isLocalFile = video?.videoUrl?.startsWith("/") == true || video?.videoUrl?.startsWith("file://") == true || (video?.videoUrl != null && !video.videoUrl.startsWith("http"))
        if (!isLocalFile) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                AsyncImage(
                    model = video?.author?.avatarUrl ?: "", contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)
                        .clickable { video?.author?.let { onAuthorClick(it) } }
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { video?.author?.let { onAuthorClick(it) } }) {
                    Text(
                        video?.author?.name ?: "Автор",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
        }
        Spacer(Modifier.height(12.dp))
        if (isLocalFile) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onBackgroundPlayToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBackgroundEnabled) Color(0xFFE53935) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isBackgroundEnabled) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBackgroundEnabled) "Фоновое воспроизведение: ВКЛ" else "Воспроизвести в фоне",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val currentId = video?.videoUrl?.let { com.santiago43rus.rupoop.util.extractId(it) }
                val isLiked = currentId != null && registry.likedVideos.any { com.santiago43rus.rupoop.util.extractId(it.videoUrl) == currentId }
                val isDisliked = currentId != null && registry.dislikedVideos.contains(currentId)
                DetailAction(if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Лайк", color = if (isLiked) Color.Red else LocalContentColor.current, onClick = { video?.let { onLike(it) } })
                DetailAction(if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Дизлайк", color = if (isDisliked) Color.Red else LocalContentColor.current, onClick = { video?.let { onDislike(it) } })
                DetailAction(Icons.Outlined.Download, "Скачать", onClick = { video?.let { onDownload(it) } })
                DetailAction(Icons.Default.Share, "Поделиться", onClick = { video?.let { onShare(it) } })
                DetailAction(Icons.AutoMirrored.Filled.PlaylistAdd, "В плейлист", onClick = { video?.let { onAddToPlaylist(it) } })
                DetailAction(Icons.Default.Headphones, "Фон", color = if (isBackgroundEnabled) Color(0xFFE53935) else LocalContentColor.current, onClick = onBackgroundPlayToggle)
            }
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
