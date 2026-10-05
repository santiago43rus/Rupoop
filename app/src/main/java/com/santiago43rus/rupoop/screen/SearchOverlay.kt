package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.data.SearchSource
import com.santiago43rus.rupoop.util.extractId

@Composable
fun SearchOverlay(vm: AppViewModel) {
    val listState = rememberLazyGridState()

    LaunchedEffect(vm.searchResults) {
        if (vm.searchResults.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val config = LocalConfiguration.current
    val columns = when {
        config.screenWidthDp >= 900 -> 3
        config.screenWidthDp >= 600 -> 2
        else -> 1
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Source selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchSource.entries.forEach { source ->
                    val isSelected = vm.selectedSearchSource == source
                    FilterChip(
                        selected = isSelected,
                        onClick = { vm.selectSearchSource(source) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.ui.graphics.Color.White,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.Black
                        ),
                        label = {
                            Text(
                                text = source.displayName,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            if (vm.isSearching) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    val shimmerCount = if (columns > 1) columns * 4 else 6
                    items(shimmerCount) {
                        com.santiago43rus.rupoop.components.VideoCardShimmer()
                    }
                }
            } else if (vm.searchError) {
                com.santiago43rus.rupoop.components.ErrorPlaceholderView(
                    modifier = Modifier.fillMaxSize(),
                    title = "Не удалось загрузить",
                    message = "Произошла ошибка при поиске. Проверьте соединение и попробуйте снова.",
                    onRetry = { vm.performSearch(vm.searchQuery) }
                )
            } else if (vm.searchResults.isEmpty() && vm.searchQuery.isNotBlank()) {
                val subMsg = if (vm.selectedSearchSource != SearchSource.ALL) {
                    "По запросу «${vm.searchQuery}» ничего не найдено на платформе ${vm.selectedSearchSource.displayName}"
                } else {
                    "По запросу «${vm.searchQuery}» ничего не найдено. Попробуйте изменить запрос."
                }
                com.santiago43rus.rupoop.components.EmptyPlaceholderView(
                    modifier = Modifier.fillMaxSize(),
                    title = "Ничего не найдено",
                    message = subMsg
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = if (columns == 1) PaddingValues(bottom = 16.dp) else PaddingValues(12.dp),
                    horizontalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(16.dp),
                    verticalArrangement = if (columns == 1) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(12.dp)
                ) {
                    items(vm.searchResults) { video ->
                        val history =
                            vm.userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                        VideoCardItem(
                            video = video, history = history,
                            onClick = { vm.playVideo(video, vm.searchResults) },
                            onAuthorClick = { vm.loadAuthorVideos(it, false) },
                            onMoreClick = { action -> vm.handleVideoMoreAction(video, action) }
                        )
                    }
                }
            }
        }
    }
}
