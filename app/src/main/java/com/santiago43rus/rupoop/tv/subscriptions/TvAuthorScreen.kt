package com.santiago43rus.rupoop.tv.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.WatchHistoryItem
import com.santiago43rus.rupoop.tv.components.TvVideoCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAuthorScreen(
    author: Author,
    authorVideos: List<SearchResult>,
    isSubscribed: Boolean,
    watchHistory: List<WatchHistoryItem>? = null,
    onToggleSubscription: (Author) -> Unit,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onVideoMenuClick: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Шапка автора / канала
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
                    .background(colorScheme.border),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = author.name,
                    color = colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Видео автора: ${authorVideos.size}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = { onToggleSubscription(author) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSubscribed) colorScheme.surfaceVariant else colorScheme.primary,
                    contentColor = if (isSubscribed) colorScheme.onSurface else colorScheme.onPrimary,
                    focusedContainerColor = colorScheme.primary,
                    focusedContentColor = colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isSubscribed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                    contentDescription = "Подписка"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubscribed) "Вы подписаны" else "Подписаться",
                    color = if (isSubscribed) colorScheme.onSurface else colorScheme.onPrimary
                )
            }
        }

        // Сетка видео канала
        if (authorVideos.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                itemsIndexed(authorVideos, key = { _, v -> v.videoUrl }) { index, video ->
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoSelected(video, authorVideos) },
                        onMenuClick = { onVideoMenuClick(video) },
                        watchHistory = watchHistory
                    )
                    if (index == authorVideos.lastIndex) {
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Загрузка видео канала...", color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        }
    }
}
