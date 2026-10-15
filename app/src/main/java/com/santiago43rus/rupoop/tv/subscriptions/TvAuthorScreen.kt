package com.santiago43rus.rupoop.tv.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAuthorScreen(
    author: Author,
    authorVideos: List<SearchResult>,
    isSubscribed: Boolean,
    onToggleSubscription: (Author) -> Unit,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Шапка автора / канала
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = author.avatarUrl,
                    contentDescription = author.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = author.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Видео автора: ${authorVideos.size}",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = { onToggleSubscription(author) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSubscribed) Color(0xFF21262D) else Color(0xFF238636),
                        focusedContainerColor = if (isSubscribed) Color(0xFF30363D) else Color(0xFF2EA043)
                    )
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                        contentDescription = "Подписка",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSubscribed) "Вы подписаны" else "Подписаться",
                        color = Color.White
                    )
                }
            }
        }

        // Видео автора
        if (authorVideos.isNotEmpty()) {
            val chunks = authorVideos.chunked(4)
            chunks.forEachIndexed { index, rowVideos ->
                item {
                    if (index == chunks.size - 1) {
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                    TvRow(
                        title = if (index == 0) "Видео канала" else "",
                        videos = rowVideos,
                        onVideoClick = { onVideoSelected(it, authorVideos) }
                    )
                }
            }
        } else {
            item {
                Text("Загрузка видео канала...", color = Color.Gray, fontSize = 15.sp)
            }
        }
    }
}
