package com.santiago43rus.rupoop.tv

import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.*
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.tv.components.TvDownloadDialog
import com.santiago43rus.rupoop.tv.components.TvVideoMenuDialog
import com.santiago43rus.rupoop.tv.home.TvHomeScreen
import com.santiago43rus.rupoop.tv.library.TvDownloadsScreen
import com.santiago43rus.rupoop.tv.library.TvLibraryScreen
import com.santiago43rus.rupoop.tv.player.TvPlayerScreen
import com.santiago43rus.rupoop.tv.search.TvSearchScreen
import com.santiago43rus.rupoop.tv.settings.TvLoginScreen
import com.santiago43rus.rupoop.tv.settings.TvSettingsScreen
import com.santiago43rus.rupoop.tv.subscriptions.TvAuthorScreen
import com.santiago43rus.rupoop.tv.subscriptions.TvSubscriptionsScreen
import com.santiago43rus.rupoop.tv.theme.RupoopTvTheme
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.launch

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
    onThemeToggle: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(TvNavSection.HOME) }
    var viewingAuthor by remember { mutableStateOf<Author?>(null) }
    var isLoginScreenOpen by remember { mutableStateOf(false) }
    var activeMenuVideo by remember { mutableStateOf<SearchResult?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Сбор системных сообщений snackbarMessage на ТВ
    LaunchedEffect(Unit) {
        vm.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    RupoopTvTheme(themeMode = vm.settingsManager.themeMode) {
        val colorScheme = MaterialTheme.colorScheme

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
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
                return@RupoopTvTheme
            }

            // Экран авторизации GitHub Device Flow
            if (isLoginScreenOpen) {
                TvLoginScreen(
                    onAuthSuccess = { token ->
                        vm.onAuthSuccess(token)
                        isLoginScreenOpen = false
                    },
                    onBack = { isLoginScreenOpen = false }
                )
                return@RupoopTvTheme
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
                return@RupoopTvTheme
            }

            // Первоначальная загрузка
            LaunchedEffect(Unit) {
                if (vm.homeVideos.isEmpty()) vm.loadHome(false)
                if (vm.liveVideos.isEmpty()) vm.loadLiveStreams(false)
                if (vm.subscriptionVideos.isEmpty() && vm.userRegistry.subscriptions.isNotEmpty()) {
                    vm.loadSubscriptions(false)
                }
            }

            // Фиксированный Row: левый рейл (~88dp) + контент без перекрытия
            Row(modifier = Modifier.fillMaxSize()) {
                // Левый навигационный рейл
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(colorScheme.surface)
                        .padding(vertical = 24.dp, horizontal = 8.dp)
                        .focusRestorer(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvNavSection.entries.forEach { section ->
                        val isSelected = selectedSection == section
                        Surface(
                            onClick = { selectedSection = section },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = section.title,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Область контента (занимает всё оставшееся пространство с weight 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
                                    onVideoMenuClick = { video ->
                                        activeMenuVideo = video
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
                                    settingsManager = vm.settingsManager,
                                    registry = vm.userRegistry,
                                    currentThemeMode = vm.settingsManager.themeMode,
                                    onThemeToggle = onThemeToggle,
                                    onOpenHiddenVideos = {
                                        selectedSection = TvNavSection.LIBRARY
                                    },
                                    onPullFromGist = { vm.pullFromGist() },
                                    onPushToGist = { vm.pushToGist() },
                                    onResetSync = {
                                        vm.settingsManager.cachedGistId = null
                                        vm.showSnackbar("ID Gist сброшен")
                                    },
                                    onClearHistory = { vm.clearWatchHistory() },
                                    onOpenLogin = { isLoginScreenOpen = true },
                                    onLogout = { vm.logout() }
                                )
                            }
                        }
                    }
                }
            }

            // Контекстное меню ⋮ на карточке видео
            activeMenuVideo?.let { video ->
                TvVideoMenuDialog(
                    video = video,
                    onAction = { action ->
                        vm.handleVideoMoreAction(video, action)
                    },
                    onDismiss = { activeMenuVideo = null }
                )
            }

            // Диалог выбора загрузки видео на ТВ
            vm.showDownloadDialog?.let { video ->
                TvDownloadDialog(
                    video = video,
                    onDownloadVideo = {
                        vm.startDownload(video, isAudio = false)
                        vm.showDownloadDialog = null
                    },
                    onDownloadAudio = {
                        vm.startDownload(video, isAudio = true)
                        vm.showDownloadDialog = null
                    },
                    onDismiss = { vm.showDownloadDialog = null }
                )
            }

            // Хост уведомлений
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
            )
        }
    }
}
