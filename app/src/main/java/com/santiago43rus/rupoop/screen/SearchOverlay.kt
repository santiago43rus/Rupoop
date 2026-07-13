package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.VideoCardItem
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
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
