package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.util.extractId

@Composable
fun SearchOverlay(vm: AppViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(vm.searchResults) {
        if (vm.searchResults.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
