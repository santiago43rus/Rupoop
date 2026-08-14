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
import com.santiago43rus.rupoop.components.DeleteConfirmationDialog
import com.santiago43rus.rupoop.components.VideoCardItem
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.util.extractId

@Composable
fun HiddenVideosScreen(
    registryManager: UserRegistryManager,
    onRegistryUpdate: (UserRegistry, Boolean) -> Unit,
    isGitHubAuthenticated: Boolean = false,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    var videos by remember { mutableStateOf(registryManager.getHiddenAndDislikedVideos()) }
    var videoToDelete by remember { mutableStateOf<SearchResult?>(null) }

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
                    VideoCardItem(
                        video = video,
                        history = null,
                        isEditMode = true,
                        onClick = {},
                        onAuthorClick = {},
                        onMoreClick = { action ->
                            if (action == "remove") {
                                videoToDelete = video
                            }
                        }
                    )
                }
            }
        }
    }

    videoToDelete?.let { video ->
        val videoId = extractId(video.videoUrl) ?: ""
        DeleteConfirmationDialog(
            title = "Удалить из скрытых",
            message = "Вы уверены, что хотите удалить «${video.title}» из списка скрытых и вернуть в рекомендации?",
            showGistCheckbox = isGitHubAuthenticated,
            onConfirm = { deleteFromGist ->
                registryManager.restoreVideo(videoId, video.title)
                onRegistryUpdate(registryManager.registry, deleteFromGist)
                videos = registryManager.getHiddenAndDislikedVideos()
                videoToDelete = null
            },
            onDismiss = { videoToDelete = null }
        )
    }
}
