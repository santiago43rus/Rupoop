package com.santiago43rus.rupoop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.network.PlatformSearchEngineResolver
import com.santiago43rus.rupoop.parser.UniversalVideoParser
import com.santiago43rus.rupoop.util.formatViewCount
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.extractId

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
    isBackgroundEnabled: Boolean = false,
    episodes: List<com.santiago43rus.rupoop.parser.Episode> = emptyList(),
    selectedEpisodeIndex: Int = 0,
    onEpisodeClick: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Video Title (Like YouTube, clean bold title)
        Text(
            text = video?.title ?: "",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded }
        )
        
        val isLordfilm = video?.videoUrl?.lowercase()?.let { it.contains("lordfilm") || it.contains("lordserials") } == true

        // Video Metadata (Views and publish time)
        val viewsText = if (!isLordfilm) (video?.let { formatViewCount(it.hits) } ?: "") else ""
        val timeAgoText = video?.let { formatTimeAgo(it.publicationTs ?: it.createdTs) } ?: ""
        val metaText = buildString {
            if (viewsText.isNotEmpty()) append(viewsText)
            if (timeAgoText.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(timeAgoText)
            }
        }
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }

        val isLocalFile = video?.videoUrl?.startsWith("/") == true || video?.videoUrl?.startsWith("file://") == true || (video?.videoUrl != null && !video.videoUrl.startsWith("http"))
        if (!isLocalFile) {
            val isRutube = video?.videoUrl?.let { UniversalVideoParser.isRutubeUrl(it) } == true
            val platformName = video?.videoUrl?.let { PlatformSearchEngineResolver.getPlatformDisplayName(it) } ?: "Rutube"

            val isLordfilm = video?.videoUrl?.lowercase()?.let { it.contains("lordfilm") || it.contains("lordserials") } == true
            val authorToNavigate = if (isLordfilm) {
                Author(name = "Lordfilm", platform = "LORDFILM")
            } else {
                video?.author ?: Author(name = platformName, platform = platformName)
            }

            // Author / Channel and Subscribe Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (isLordfilm) {
                    LordfilmAvatarBadge(
                        size = 36.dp,
                        modifier = Modifier.clickable(enabled = false) { onAuthorClick(authorToNavigate) }
                    )
                } else {
                    val fallbackAvatar = if (platformName == "Одноклассники") "https://ok.ru/favicon.ico"
                        else "https://rutube.ru/static/img/default-avatar.png"
                    AsyncImage(
                        model = video?.author?.avatarUrl?.takeIf { it.isNotBlank() } ?: fallbackAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(0.3f))
                            .clickable(enabled = isRutube) { onAuthorClick(authorToNavigate) }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(enabled = isRutube) { onAuthorClick(authorToNavigate) }
                ) {
                    Text(
                        text = if (isLordfilm) "Lordfilm" else (video?.author?.name ?: "Автор"),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLordfilm) "Новинки кино и сериалов" else if (isRutube) "Rutube • 1.2 млн подписчиков" else platformName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val isSubbed = registry.subscriptions.any { it.name.equals(video?.author?.name, ignoreCase = true) }
                
                // Beautiful YouTube-style capsule Subscribe button
                Button(
                    onClick = { video?.author?.let { onToggleSub(it) } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubbed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground,
                        contentColor = if (isSubbed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background
                    ),
                    border = if (!isSubbed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isSubbed) "Вы подписаны" else "Подписаться",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))

        if (isLocalFile) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
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
            // Horizontal scroll of action capsules
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentId = video?.videoUrl?.let { extractId(it) }
                val isLiked = currentId != null && registry.likedVideos.any { extractId(it.videoUrl) == currentId }
                val isDisliked = currentId != null && registry.dislikedVideos.contains(currentId)

                // Like & Dislike combined capsule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { video?.let { onLike(it) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Лайк",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Dislike button
                    Box(
                        modifier = Modifier
                            .clickable { video?.let { onDislike(it) } }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Share capsule
                CapsuleAction(
                    icon = Icons.Default.Share,
                    label = "Поделиться",
                    onClick = { video?.let { onShare(it) } }
                )

                // Download capsule
                CapsuleAction(
                    icon = Icons.Outlined.Download,
                    label = "Скачать",
                    onClick = { video?.let { onDownload(it) } }
                )

                // Playlist capsule
                CapsuleAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "В плейлист",
                    onClick = { video?.let { onAddToPlaylist(it) } }
                )

                // Background playback capsule
                CapsuleAction(
                    icon = Icons.Default.Headphones,
                    label = "Фон",
                    iconColor = if (isBackgroundEnabled) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                    onClick = onBackgroundPlayToggle
                )
            }

            if (episodes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Серии (${episodes.size})",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    episodes.forEachIndexed { idx, ep ->
                        val isSelected = idx == selectedEpisodeIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onEpisodeClick(idx) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                                selectedLabelColor = MaterialTheme.colorScheme.background
                            ),
                            label = {
                                Text(
                                    ep.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CapsuleAction(
    icon: ImageVector,
    label: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
