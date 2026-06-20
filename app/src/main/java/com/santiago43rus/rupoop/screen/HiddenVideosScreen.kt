package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager

@Composable
fun HiddenVideosScreen(
    registryManager: UserRegistryManager,
    onRegistryUpdate: (UserRegistry) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    var videos by remember { mutableStateOf(registryManager.getHiddenAndDislikedVideos()) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Скрытые и дизлайкнутые") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Список пуст", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(videos) { video ->
                    val videoId = com.santiago43rus.rupoop.util.extractId(video.videoUrl) ?: ""
                    VideoCardItem(
                        video = video,
                        history = null,
                        isEditMode = true,
                        onClick = {},
                        onAuthorClick = {},
                        onMoreClick = { action ->
                            if (action == "remove") {
                                registryManager.restoreVideo(videoId, video.title)
                                onRegistryUpdate(registryManager.registry)
                                videos = registryManager.getHiddenAndDislikedVideos()
                            }
                        }
                    )
                }
            }
        }
    }
}
