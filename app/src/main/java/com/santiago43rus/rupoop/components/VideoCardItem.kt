package com.santiago43rus.rupoop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun VideoCardItem(
    video: SearchResult,
    modifier: Modifier = Modifier,
    history: WatchHistoryItem? = null,
    onClick: () -> Unit,
    onAuthorClick: (Author) -> Unit,
    onMoreClick: (String) -> Unit,
    isEditMode: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    val localThumbnailBitmap = remember(video.videoUrl) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(video.videoUrl) {
        val isLocal = video.videoUrl.isNotEmpty() && !video.videoUrl.startsWith("http")
        if (isLocal && video.thumbnailUrl == null && video.videoUrl.endsWith(".mp4")) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(video.videoUrl)
                    val bmp = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    localThumbnailBitmap.value = bmp
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 12.dp)
    ) {
        // Preview (Thumbnail)
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = video.thumbnailUrl ?: localThumbnailBitmap.value ?: video.videoUrl, contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            // Duration
            video.duration?.let { durSeconds ->
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp, end = 8.dp)
                ) {
                    Text(
                        text = formatDuration(durSeconds.toLong()),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Progress Bar (if watched)
            if (history != null && history.totalDuration > 0) {
                 LinearProgressIndicator(
                    progress = { history.progress.toFloat() / history.totalDuration },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
        }

        // Header: Author & Title
        Row(
            modifier = Modifier
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = video.author?.avatarUrl ?: "https://rutube.ru/static/img/default-avatar.png",
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable { video.author?.let { onAuthorClick(it) } },
                contentScale = ContentScale.Crop,
                fallback = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_info_details),
                error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_info_details)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val viewsText = formatViewCount(video.hits)
                val timeAgoText = formatTimeAgo(video.publicationTs ?: video.createdTs)
                val sep1 = if (viewsText.isNotEmpty()) " • " else ""
                val sep2 = if (timeAgoText.isNotEmpty()) " • " else ""
                Text(
                    "${video.author?.name ?: "Автор"} • Rutube$sep1$viewsText$sep2$timeAgoText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { video.author?.let { onAuthorClick(it) } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!isEditMode) {
                        DropdownMenuItem(text = { Text("Не интересно") }, onClick = { showMenu = false; onMoreClick("not_interested") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) })
                    } else {
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onMoreClick("remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                    DropdownMenuItem(text = { Text("В плейлист") }, onClick = { showMenu = false; onMoreClick("playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                    DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { showMenu = false; onMoreClick("later") }, leadingIcon = { Icon(Icons.Default.WatchLater, null) })
                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { showMenu = false; onMoreClick("share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { showMenu = false; onMoreClick("download") }, leadingIcon = { Icon(Icons.Outlined.Download, null) })
                }
            }
        }
    }
}
