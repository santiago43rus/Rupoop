package com.santiago43rus.rupoop.tv.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.tv.components.TvRow
import com.santiago43rus.rupoop.tv.components.TvSectionTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubscriptionsScreen(
    userRegistry: UserRegistry,
    subscriptionVideos: List<SearchResult>,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onAuthorSelected: (Author) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (userRegistry.subscriptions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Нет подписок", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Подписывайтесь на каналы под плеером, чтобы видеть их новые видео здесь", color = Color.Gray, fontSize = 15.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
    ) {
        // Ряд 1: Список каналов-подписок с круглыми аватарами
        item {
            TvSectionTitle(title = "Каналы")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                items(userRegistry.subscriptions, key = { it.id ?: it.name.hashCode().toLong() }) { author ->
                    Surface(
                        onClick = { onAuthorSelected(author) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF1E2228),
                            focusedContainerColor = Color(0xFF282E38)
                        ),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = author.avatarUrl,
                                contentDescription = author.name,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = author.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Ряд 2: Видео из подписок
        if (subscriptionVideos.isNotEmpty()) {
            val chunks = subscriptionVideos.chunked(4)
            chunks.forEachIndexed { index, rowVideos ->
                item {
                    // Подгрузка при достижении конца
                    if (index == chunks.size - 1) {
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                    TvRow(
                        title = if (index == 0) "Новые видео подписок" else "",
                        videos = rowVideos,
                        onVideoClick = { onVideoSelected(it, subscriptionVideos) }
                    )
                }
            }
        } else {
            item {
                Text("Загрузка видео подписок...", color = Color.Gray, fontSize = 15.sp)
            }
        }
    }
}
