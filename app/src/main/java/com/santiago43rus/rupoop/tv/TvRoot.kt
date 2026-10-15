package com.santiago43rus.rupoop.tv

import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.*
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.home.TvHomeScreen
import com.santiago43rus.rupoop.tv.library.TvDownloadsScreen
import com.santiago43rus.rupoop.tv.library.TvLibraryScreen
import com.santiago43rus.rupoop.tv.player.TvPlayerScreen
import com.santiago43rus.rupoop.tv.search.TvSearchScreen
import com.santiago43rus.rupoop.tv.settings.TvSettingsScreen
import com.santiago43rus.rupoop.tv.subscriptions.TvAuthorScreen
import com.santiago43rus.rupoop.tv.subscriptions.TvSubscriptionsScreen
import com.santiago43rus.rupoop.util.PlayerState

enum class TvNavSection(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Default.Home),
    SUBSCRIPTIONS("Подписки", Icons.Default.Subscriptions),
    SEARCH("Поиск", Icons.Default.Search),
    LIBRARY("Библиотека", Icons.AutoMirrored.Filled.LibraryBooks),
    DOWNLOADS("Загрузки", Icons.Default.Download),
    SETTINGS("Настройки", Icons.Default.Settings)
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRoot(
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(TvNavSection.HOME) }
    var viewingAuthor by remember { mutableStateOf<Author?>(null) }
    var isLoginScreenOpen by remember { mutableStateOf(false) }

    // Полноэкранный плеер
    if (vm.playerState == PlayerState.FULL) {
        TvPlayerScreen(
            currentVideo = vm.currentVideo,
            exoPlayer = vm.exoPlayer,
            isPlaying = vm.isPlaying,
            registry = vm.userRegistry,
            relatedVideos = vm.relatedVideos,
            episodes = vm.currentEpisodes,
            currentEpisodeIndex = vm.currentEpisodeIndex,
            audioTracks = vm.availableAudioTracks,
            selectedAudioTrackId = vm.selectedAudioTrackId,
            onPlayPause = {
                if (vm.isPlaying) {
                    vm.exoPlayer.pause()
                } else {
                    vm.exoPlayer.play()
                }
            },
            onSeekRelative = { offsetMs ->
                val duration = vm.exoPlayer.duration.coerceAtLeast(0L)
                val targetPos = (vm.exoPlayer.currentPosition + offsetMs).coerceIn(0L, duration)
                vm.exoPlayer.seekTo(targetPos)
            },
            onClosePlayer = {
                vm.closePlayer()
            },
            onPlayNext = {
                vm.playNext()
            },
            onPlayPrevious = {
                vm.playPrevious()
            },
            onEpisodeSelect = { index ->
                vm.playEpisode(index)
            },
            onAudioTrackSelect = { track ->
                vm.selectAudioTrack(track)
            },
            onLike = { video ->
                vm.toggleLike(video)
            },
            onDislike = { video ->
                vm.toggleDislike(video)
            },
            onWatchLater = { video ->
                vm.toggleWatchLater(video)
            },
            onAddToPlaylist = { video ->
                vm.showPlaylistDialog = video
            },
            onShare = { video ->
                val clipboard = vm.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Video URL", video.videoUrl)
                clipboard?.setPrimaryClip(clip)
                vm.showSnackbar("Ссылка скопирована в буфер обмена")
            },
            onAuthorClick = { author ->
                viewingAuthor = author
                vm.loadAuthorVideos(author, false)
            },
            onRelatedVideoClick = { video ->
                vm.playVideo(video, vm.relatedVideos)
            }
        )
        return
    }

    // Экран авторизации GitHub Device Flow (если открыт)
    if (isLoginScreenOpen) {
        com.santiago43rus.rupoop.tv.settings.TvLoginScreen(
            onAuthSuccess = { token ->
                vm.onAuthSuccess(token)
                isLoginScreenOpen = false
            },
            onBack = { isLoginScreenOpen = false }
        )
        return
    }

    // Экран канала автора (если открыт)
    viewingAuthor?.let { author ->
        val isSubscribed = vm.userRegistry.subscriptions.any { it.name == author.name }
        TvAuthorScreen(
            author = author,
            authorVideos = vm.authorVideos,
            isSubscribed = isSubscribed,
            onToggleSubscription = { vm.toggleSubscription(it) },
            onVideoSelected = { video, list ->
                vm.playVideo(video, list)
                vm.playerState = PlayerState.FULL
            },
            onLoadMore = { vm.loadAuthorVideos(author, true) },
            onBack = { viewingAuthor = null }
        )
        return
    }

    // Первоначальная загрузка ленты рекомендаций и подписок
    LaunchedEffect(Unit) {
        if (vm.homeVideos.isEmpty()) {
            vm.loadHome(false)
        }
        if (vm.liveVideos.isEmpty()) {
            vm.loadLiveStreams(false)
        }
        if (vm.subscriptionVideos.isEmpty() && vm.userRegistry.subscriptions.isNotEmpty()) {
            vm.loadSubscriptions(false)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        // Левый Navigation Drawer / Rail для ТВ
        NavigationDrawer(
            drawerContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 24.dp, horizontal = 12.dp)
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvNavSection.entries.forEach { section ->
                        NavigationDrawerItem(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            leadingContent = {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    tint = if (selectedSection == section) Color(0xFF58A6FF) else Color(0xFF8B949E)
                                )
                            }
                        ) {
                            Text(
                                text = section.title,
                                color = if (selectedSection == section) Color.White else Color(0xFF8B949E),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                Crossfade(targetState = selectedSection, label = "TvNavTransition") { section ->
                    when (section) {
                        TvNavSection.HOME -> {
                            val historyVideos = remember(vm.userRegistry.watchHistory) {
                                vm.userRegistry.watchHistory.map { item ->
                                    SearchResult(
                                        videoUrl = item.videoUrl,
                                        title = item.title ?: "",
                                        thumbnailUrl = item.thumbnailUrl,
                                        author = Author(
                                            id = item.authorId,
                                            name = item.authorName ?: "",
                                            avatarUrl = item.authorAvatarUrl
                                        ),
                                        duration = (item.totalDuration / 1000).toInt()
                                    )
                                }
                            }
                            TvHomeScreen(
                                homeVideos = vm.homeVideos,
                                historyVideos = historyVideos,
                                likedVideos = vm.userRegistry.likedVideos,
                                liveVideos = vm.liveVideos,
                                onVideoSelected = { video, list ->
                                    vm.playVideo(video, list)
                                    vm.playerState = PlayerState.FULL
                                },
                                onLoadMoreHome = { vm.loadHome(true) }
                            )
                        }
                        TvNavSection.SUBSCRIPTIONS -> {
                            TvSubscriptionsScreen(
                                userRegistry = vm.userRegistry,
                                subscriptionVideos = vm.subscriptionVideos,
                                onVideoSelected = { video, list ->
                                    vm.playVideo(video, list)
                                    vm.playerState = PlayerState.FULL
                                },
                                onAuthorSelected = { author ->
                                    viewingAuthor = author
                                    vm.loadAuthorVideos(author, false)
                                },
                                onLoadMore = { vm.loadSubscriptions(true) }
                            )
                        }
                        TvNavSection.SEARCH -> {
                            TvSearchScreen(
                                searchQuery = vm.searchQuery,
                                selectedSearchSource = vm.selectedSearchSource,
                                searchResults = vm.searchResults,
                                searchSuggestions = vm.searchSuggestions,
                                searchHistory = vm.userRegistry.searchHistory,
                                isSearching = vm.isSearching,
                                onSearchQueryChange = { vm.updateSearchQuery(it) },
                                onSearchSourceSelect = { vm.selectSearchSource(it) },
                                onPerformSearch = { vm.performSearch(it) },
                                onRemoveSearchQuery = { vm.removeSearchQuery(it) },
                                onClearSearchHistory = { vm.clearSearchHistory() },
                                onVideoSelected = { video, list ->
                                    vm.playVideo(video, list)
                                    vm.playerState = PlayerState.FULL
                                }
                            )
                        }
                        TvNavSection.LIBRARY -> {
                            val historyVideos = remember(vm.userRegistry.watchHistory) {
                                vm.userRegistry.watchHistory.map { item ->
                                    SearchResult(
                                        videoUrl = item.videoUrl,
                                        title = item.title ?: "",
                                        thumbnailUrl = item.thumbnailUrl,
                                        author = Author(
                                            id = item.authorId,
                                            name = item.authorName ?: "",
                                            avatarUrl = item.authorAvatarUrl
                                        ),
                                        duration = (item.totalDuration / 1000).toInt()
                                    )
                                }
                            }
                            TvLibraryScreen(
                                historyVideos = historyVideos,
                                likedVideos = vm.userRegistry.likedVideos,
                                watchLaterVideos = vm.userRegistry.watchLater,
                                playlists = vm.userRegistry.playlists,
                                hiddenVideos = vm.userRegistry.hiddenVideosList,
                                onVideoSelected = { video, list ->
                                    vm.playVideo(video, list)
                                    vm.playerState = PlayerState.FULL
                                },
                                onRemoveFromHistory = { vm.removeFromHistory(it.videoUrl) },
                                onRemoveFromWatchLater = { vm.toggleWatchLater(it) },
                                onRestoreHiddenVideo = { vm.restoreHiddenVideo(it, "") },
                                onDeletePlaylist = { vm.deletePlaylist(it.id) }
                            )
                        }
                        TvNavSection.DOWNLOADS -> {
                            val downloadsList by vm.downloadTracker.downloads.collectAsState()
                            TvDownloadsScreen(
                                downloads = downloadsList,
                                onPlay = { download ->
                                    download.filePath?.let { path ->
                                        vm.playLocalFile(path, download.title)
                                        vm.playerState = PlayerState.FULL
                                    }
                                },
                                onDelete = { videoId ->
                                    vm.downloadTracker.removeDownload(videoId)
                                }
                            )
                        }
                        TvNavSection.SETTINGS -> {
                            TvSettingsScreen(
                                autoPlayNext = vm.settingsManager.autoPlayNext,
                                onAutoPlayNextChange = { vm.settingsManager.autoPlayNext = it },
                                adultContentEnabled = vm.settingsManager.adultContentEnabled,
                                onAdultContentChange = { vm.settingsManager.adultContentEnabled = it },
                                isAuthenticated = vm.isAuthenticated,
                                githubUserName = vm.githubUser?.login,
                                onOpenLogin = { isLoginScreenOpen = true },
                                onLogout = { vm.logout() }
                            )
                        }
                    }
                }
            }
        }
    }
}
