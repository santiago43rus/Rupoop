package com.santiago43rus.rupoop.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.components.PlaylistSelectionDialog
import com.santiago43rus.rupoop.util.OverlayState
import com.santiago43rus.rupoop.util.PlayerState

@androidx.media3.common.util.UnstableApi
@Composable
fun RutubeAppOverlays(
    vm: AppViewModel,
    onThemeToggle: (String) -> Unit,
    context: Context
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
                onToggleSubscription = { vm.toggleSubscription(it) },
                onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) },
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
                onShowHiddenVideos = { vm.isHiddenVideosVisible = true },
                onOpenNotificationSettings = { vm.isNotificationSettingsVisible = true },
                onNotificationsChanged = {
                    val showBg = vm.showBackgroundNotifications
                    if (!showBg) {
                        try {
                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                            context.stopService(intent)
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Failed to stop PlaybackService", e)
                        }
                    } else if (vm.playerState != PlayerState.CLOSED) {
                        try {
                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Failed to start PlaybackService", e)
                        }
                    }
                }
            )
        }
    }

    // Notification Settings overlay
    AnimatedVisibility(
        visible = vm.isNotificationSettingsVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NotificationSettingsScreen(
                vm = vm,
                onDismiss = { vm.isNotificationSettingsVisible = false },
                onSettingsChanged = {
                    val showBg = vm.showBackgroundNotifications
                    if (!showBg) {
                        try {
                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                            context.stopService(intent)
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Failed to stop PlaybackService", e)
                        }
                    } else if (vm.playerState != PlayerState.CLOSED) {
                        try {
                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Failed to start PlaybackService", e)
                        }
                    }
                }
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
                onRegistryUpdate = { vm.onRegistryUpdate(it) },
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Видео (MP4)")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        vm.startDownload(video, isAudio = true)
                        vm.showDownloadDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Аудио (M4A)")
                }
            }
        )
    }
}
