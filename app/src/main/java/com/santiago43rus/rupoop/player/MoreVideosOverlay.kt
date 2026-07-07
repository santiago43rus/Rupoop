package com.santiago43rus.rupoop.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreVideosOverlay(
    showMoreVideos: Boolean,
    onClose: () -> Unit,
    isFullscreen: Boolean,
    isLocalFile: Boolean,
    moreVideosDragOffset: Float,
    currentVideo: SearchResult?,
    relatedVideos: List<SearchResult>,
    onPlayRelated: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenHeightPixels = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()
    var panelDragY by remember { mutableStateOf(0f) }
    val isDraggingUp = moreVideosDragOffset < 0f

    val targetOffsetY = when {
        isDraggingUp -> (screenHeightPixels + moreVideosDragOffset).coerceAtLeast(0f)
        showMoreVideos -> panelDragY
        else -> screenHeightPixels
    }

    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = if (isDraggingUp || panelDragY > 0f) 0 else 250),
        label = "moreVideosY"
    )

    if ((showMoreVideos || animatedOffsetY < screenHeightPixels) && !isLocalFile) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .background(Color.Black.copy(0.9f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            if (panelDragY > 100f) {
                                onClose()
                            }
                            panelDragY = 0f
                        },
                        onDragCancel = { panelDragY = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        panelDragY = (panelDragY + dragAmount.y).coerceAtLeast(0f)
                    }
                }
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 20.dp else 4.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text("Ещё видео", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                
                Row(Modifier.padding(vertical = 8.dp).padding(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("Все видео") }, colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Color.White.copy(0.2f)))
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = {
                            Text(
                                text = "Автор: ${currentVideo?.author?.name ?: "Автор"}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)
                ) {
                    items(relatedVideos) { video ->
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .clickable { 
                                    onPlayRelated(video)
                                    onClose()
                                }
                        ) {
                            Box {
                                AsyncImage(
                                    model = video.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16 / 9f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                video.duration?.let { dur ->
                                    Surface(
                                        color = Color.Black.copy(0.8f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                    ) {
                                        Text(
                                            formatTime(dur.toLong() * 1000),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                AsyncImage(
                                    model = video.author?.avatarUrl ?: "",
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(video.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    val viewsText = formatViewCount(video.hits)
                                    val timeAgoText = formatTimeAgo(video.publicationTs ?: video.createdTs)
                                    val metaText = buildString {
                                        append(video.author?.name ?: "Автор")
                                        append(" • Rutube")
                                        if (viewsText.isNotEmpty()) append(" • $viewsText")
                                        if (timeAgoText.isNotEmpty()) append(" • $timeAgoText")
                                    }
                                    Text(metaText, color = Color.White.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
