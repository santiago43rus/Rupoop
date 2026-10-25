package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatDuration
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    video: SearchResult,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val videoUrlLower = video.videoUrl.lowercase()
    val isLordfilm = videoUrlLower.contains("lordfilm") || videoUrlLower.contains("lordserials")
    val platformName = when {
        videoUrlLower.contains("vk.com") || videoUrlLower.contains("vkvideo.ru") || videoUrlLower.contains("vk.ru") -> "VK Видео"
        videoUrlLower.contains("ok.ru") || videoUrlLower.contains("odnoklassniki.ru") -> "Одноклассники"
        isLordfilm -> "Lordfilm"
        else -> "Rutube"
    }

    val isLiveStream = video.duration == 0 || (video.duration == null && (
        video.tags?.any { it.contains("live", ignoreCase = true) || it.contains("эфир", ignoreCase = true) } == true ||
        video.title.contains("прямой эфир", ignoreCase = true) ||
        video.title.contains("live", ignoreCase = true)
    ))

    Card(
        onClick = onClick,
        onLongClick = onMenuClick,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 1f)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key.nativeKeyCode == 82 || event.key == Key.Menu)) {
                    if (onMenuClick != null) {
                        onMenuClick()
                        true
                    } else false
                } else false
            },
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, colorScheme.border.copy(alpha = 0.2f))),
            focusedBorder = Border(BorderStroke(2.dp, colorScheme.primary))
        ),
        colors = CardDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            focusedContainerColor = colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(colorScheme.background)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isLiveStream) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "В ЭФИРЕ",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    val duration = video.duration
                    if (duration != null && duration > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatDuration(duration.toLong()),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fallbackAvatar = if (platformName == "Одноклассники") "https://ok.ru/favicon.ico"
                    else "https://rutube.ru/static/img/default-avatar.png"
                AsyncImage(
                    model = video.author?.avatarUrl ?: fallbackAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorScheme.border),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val authorName = video.author?.name ?: "Автор"
                    val viewsText = if (video.hits != null && video.hits > 0) formatViewCount(video.hits) else ""
                    val timeAgoText = formatTimeAgo(video.publicationTs ?: video.createdTs)
                    val sep1 = if (viewsText.isNotEmpty()) " • " else ""
                    val sep2 = if (timeAgoText.isNotEmpty()) " • " else ""
                    Text(
                        text = if (isLordfilm) {
                            platformName
                        } else {
                            "$authorName • $platformName$sep1$viewsText$sep2$timeAgoText"
                        },
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onMenuClick != null) {
                    Text(
                        text = "⋮",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
