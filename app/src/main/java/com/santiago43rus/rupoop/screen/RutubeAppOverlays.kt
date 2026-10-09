package com.santiago43rus.rupoop.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.PlaylistSelectionDialog
import com.santiago43rus.rupoop.util.OverlayState
import com.santiago43rus.rupoop.util.PlayerState

@androidx.media3.common.util.UnstableApi
@Composable
fun RutubeAppOverlays(
    vm: AppViewModel,
    onThemeToggle: (String) -> Unit
) {
    // Overlays with animation
    for (overlay in vm.overlayOrder) {
        AnimatedVisibility(
            visible = overlay == OverlayState.SEARCH && vm.isSearchVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
        ) {
            SearchOverlay(vm = vm)
        }
        AnimatedVisibility(
            visible = overlay == OverlayState.AUTHOR && vm.isAuthorVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
        ) {
            AuthorScreen(
                author = vm.selectedAuthor, authorVideos = vm.authorVideos, userRegistry = vm.userRegistry,
                isRefreshing = vm.isRefreshingAuthor, isLoadingMore = vm.isAuthorLoadingMore, hasMoreVideos = vm.hasMoreAuthorVideos,
                onRefresh = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, false) } },
                onLoadMore = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, true) } },
                onVideoClick = { video, list -> vm.playVideo(video, list) },
                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                onToggleSubscription = { author, fromGist ->
                    val isSubbed = vm.userRegistry.subscriptions.any { it.name.equals(author.name, ignoreCase = true) }
                    if (isSubbed) {
                        vm.unsubscribeAuthor(author, fromGist)
                    } else {
                        vm.toggleSubscription(author)
                    }
                },
                onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) },
                isGitHubAuthenticated = vm.isAuthenticated,
                currentSort = vm.authorSortOrder,
                onSortChange = { newSort ->
                    vm.authorSortOrder = newSort
                    vm.selectedAuthor?.let { vm.loadAuthorVideos(it, false) }
                }
            )
        }
    }

    // Settings overlay
    AnimatedVisibility(
        visible = vm.isSettingsVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                vm = vm,
                onThemeToggle = onThemeToggle,
                onShowHiddenVideos = { vm.isHiddenVideosVisible = true }
            )
        }
    }

    // Hidden and Disliked overlay
    AnimatedVisibility(
        visible = vm.isHiddenVideosVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HiddenVideosScreen(
                registryManager = vm.registryManager,
                onRegistryUpdate = { registry, fromGist -> vm.onRegistryUpdate(registry, fromGist) },
                isGitHubAuthenticated = vm.isAuthenticated,
                onDismiss = { vm.isHiddenVideosVisible = false }
            )
        }
    }

    // Playlist dialog
    vm.showPlaylistDialog?.let { video ->
        PlaylistSelectionDialog(
            playlists = vm.userRegistry.playlists,
            onDismiss = { vm.showPlaylistDialog = null },
            onPlaylistSelected = { name -> vm.addToPlaylist(name, video) },
            onCreateNew = { name -> vm.createPlaylistAndAdd(name, video) }
        )
    }

    // Download option dialog
    vm.showDownloadDialog?.let { video ->
        AlertDialog(
            onDismissRequest = { vm.showDownloadDialog = null },
            title = { Text("Выберите формат загрузки") },
            text = { Text("Вы хотите скачать video целиком или только звуковую дорожку?") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.startDownload(video, isAudio = false)
                        vm.showDownloadDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text("Видео (MP4)", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        vm.startDownload(video, isAudio = true)
                        vm.showDownloadDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text("Аудио (M4A)", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        )
    }
}
