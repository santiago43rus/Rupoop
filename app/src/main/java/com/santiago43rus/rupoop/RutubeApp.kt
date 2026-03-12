package com.santiago43rus.rupoop

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.components.*
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.player.CustomVideoPlayer
import com.santiago43rus.rupoop.player.MiniPlayer
import com.santiago43rus.rupoop.screen.*
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubeApp(
    vm: AppViewModel,
    onThemeToggle: () -> Unit,
    deepLinkVideoUrl: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val config = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect snackbar events from ViewModel
    LaunchedEffect(Unit) {
        vm.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Auth launcher
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val response = AuthorizationResponse.fromIntent(result.data!!)
            if (response != null) {
                scope.launch {
                    try {
                        val token = vm.authManager.exchangeCodeForToken(response)
                        vm.onAuthSuccess(token)
                    } catch (e: Exception) {
                        Log.e("RupoopAuth", "Auth exchange error", e)
                    }
                }
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    // Deep link
    LaunchedEffect(deepLinkVideoUrl) {
        if (deepLinkVideoUrl != null) {
            vm.handleDeepLink(deepLinkVideoUrl)
            onDeepLinkConsumed()
        }
    }

    // Initialize app & periodic saving
    LaunchedEffect(Unit) { vm.initializeApp() }
    LaunchedEffect(vm.isPlaying, vm.currentVideo) {
        if (vm.isPlaying && vm.currentVideo != null) vm.startProgressSaving()
        else vm.stopProgressSaving()
    }
    LaunchedEffect(vm.currentNav) {
        if (vm.currentNav == NavItem.SUBSCRIPTIONS) vm.loadSubscriptions(false)
        if (vm.currentNav == NavItem.HOME && vm.homeVideos.isEmpty()) vm.loadHome(false)
    }

    // Back handler
    BackHandler(
        enabled = vm.playerState != PlayerState.CLOSED || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible || vm.currentLibSub != LibrarySubScreen.NONE || vm.currentNav != NavItem.HOME
    ) {
        focusManager.clearFocus()
        vm.handleBack()
    }

    // Onboarding
    if (vm.showOnboarding) {
        ContentSelectionDialog(settingsManager = vm.settingsManager, onDismiss = { vm.dismissOnboarding() })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (vm.playerState != PlayerState.FULL && !vm.isFullscreenVideo) {
                AppTopBar(vm = vm, focusManager = focusManager, authLauncher = authLauncher)
            }
        },
        bottomBar = {
            if (vm.playerState != PlayerState.FULL && !vm.isFullscreenVideo) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(
                        selected = vm.currentNav == NavItem.HOME,
                        onClick = { vm.currentNav = NavItem.HOME; vm.currentLibSub = LibrarySubScreen.NONE; vm.isSearchVisible = false; vm.isAuthorVisible = false; vm.searchQuery = "" },
                        icon = { Icon(if (vm.currentNav == NavItem.HOME) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
                        label = { Text("Главная") }
                    )
                    NavigationBarItem(
                        selected = vm.currentNav == NavItem.SUBSCRIPTIONS,
                        onClick = { vm.currentNav = NavItem.SUBSCRIPTIONS; vm.currentLibSub = LibrarySubScreen.NONE; vm.isSearchVisible = false; vm.isAuthorVisible = false },
                        icon = { Icon(if (vm.currentNav == NavItem.SUBSCRIPTIONS) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Subs") },
                        label = { Text("Подписки") }
                    )
                    NavigationBarItem(
                        selected = vm.currentNav == NavItem.LIBRARY,
                        onClick = { vm.currentNav = NavItem.LIBRARY; vm.currentLibSub = LibrarySubScreen.NONE; vm.isSearchVisible = false; vm.isAuthorVisible = false },
                        icon = { Icon(if (vm.currentNav == NavItem.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, "Lib") },
                        label = { Text("Библиотека") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Main content by nav
            when (vm.currentNav) {
                NavItem.HOME -> {
                    HomeScreen(
                        homeVideos = vm.homeVideos, userRegistry = vm.userRegistry,
                        isRefreshing = vm.isRefreshingHome, isLoadingMore = vm.isHomeLoadingMore,
                        onRefresh = { vm.loadHome(false) }, onLoadMore = { vm.loadHome(true) },
                        onVideoClick = { video, list -> vm.playVideo(video, list) },
                        onAuthorClick = { vm.loadAuthorVideos(it, false) },
                        onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) }
                    )
                }
                NavItem.SUBSCRIPTIONS -> {
                    SubscriptionsScreen(
                        userRegistry = vm.userRegistry, subscriptionVideos = vm.subscriptionVideos,
                        isRefreshing = vm.isRefreshingSubs, isLoadingMore = vm.isSubsLoadingMore, hasMoreVideos = vm.hasMoreSubsVideos,
                        onRefresh = { vm.loadSubscriptions(false) }, onLoadMore = { vm.loadSubscriptions(true) },
                        onVideoClick = { video, list -> vm.playVideo(video, list) },
                        onAuthorClick = { vm.loadAuthorVideos(it, false) },
                        onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) }
                    )
                }
                NavItem.LIBRARY -> LibraryContent(vm = vm)
                NavItem.SETTINGS -> SettingsScreen(vm.settingsManager, onThemeToggle, vm.registryManager, onRegistryUpdate = { vm.onRegistryUpdate(it) })
            }

            // Overlays
            vm.overlayOrder.forEach { overlay ->
                if (overlay == OverlayState.SEARCH && vm.isSearchVisible) {
                    SearchOverlay(vm = vm)
                }
                if (overlay == OverlayState.AUTHOR && vm.isAuthorVisible) {
                    AuthorScreen(
                        author = vm.selectedAuthor, authorVideos = vm.authorVideos, userRegistry = vm.userRegistry,
                        isRefreshing = vm.isRefreshingAuthor, isLoadingMore = vm.isAuthorLoadingMore, hasMoreVideos = vm.hasMoreAuthorVideos,
                        onRefresh = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, false) } },
                        onLoadMore = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, true) } },
                        onVideoClick = { video, list -> vm.playVideo(video, list) },
                        onAuthorClick = { vm.loadAuthorVideos(it, false) },
                        onToggleSubscription = { vm.toggleSubscription(it) },
                        onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) }
                    )
                }
            }

            // Search history overlay
            if (vm.isSearchExpanded && vm.userRegistry.searchHistory.isNotEmpty()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn {
                        items(vm.userRegistry.searchHistory) { query ->
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = Color.Gray)
                                Spacer(Modifier.width(16.dp))
                                Text(query, Modifier.weight(1f).clickable { focusManager.clearFocus(); vm.performSearch(query) })
                                IconButton(onClick = { vm.removeSearchQuery(query) }) {
                                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Player
            if (vm.playerState != PlayerState.CLOSED) {
                val playerHeight by animateDpAsState(
                    targetValue = if (vm.playerState == PlayerState.FULL) config.screenHeightDp.dp else 64.dp, label = ""
                )
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(playerHeight).background(MaterialTheme.colorScheme.background)) {
                    if (vm.playerState == PlayerState.FULL) {
                        Column {
                            CustomVideoPlayer(
                                exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isFullscreen = vm.isFullscreenVideo,
                                currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                                onMinimize = { vm.playerState = PlayerState.MINI },
                                onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo) },
                                onNext = { vm.playNext() }, onPrevious = { vm.playPrevious() },
                                isFirstVideo = vm.currentVideoIndex <= 0,
                                isLastVideo = vm.currentVideoIndex >= vm.currentVideoList.size - 1,
                                onPlayRelated = { vm.playVideo(it, vm.relatedVideos) }
                            )
                            if (!vm.isFullscreenVideo) {
                                LazyColumn(Modifier.weight(1f)) {
                                    item {
                                        VideoDetails(
                                            vm.currentVideo, vm.userRegistry,
                                            onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                            onToggleSub = { vm.toggleSubscription(it) },
                                            onLike = { vm.currentVideo?.let { vm.toggleLike(it) } },
                                            onShare = { vm.currentVideo?.let { vm.shareVideo(it) } },
                                            onAddToPlaylist = { vm.showPlaylistDialog = vm.currentVideo },
                                            onDownload = { vm.currentVideo?.let { vm.startDownload(it) } }
                                        )
                                        HorizontalDivider()
                                        Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                                    }
                                    items(vm.relatedVideos) { video ->
                                        val history = vm.userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                        VideoItem(video, history,
                                            onClick = { vm.playVideo(video, vm.relatedVideos) },
                                            onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                            onMoreClick = { action -> vm.handleVideoMoreAction(video, action) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        MiniPlayer(vm.currentVideo, vm.isPlaying, vm.exoPlayer, onClose = { vm.closePlayer() }, onClick = { vm.playerState = PlayerState.FULL })
                    }
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
        }
    }
}

// Top App Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    vm: AppViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager,
    authLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    TopAppBar(
        title = {
            if (!vm.isSearchExpanded) {
                val topVisible = vm.overlayOrder.lastOrNull {
                    (it == OverlayState.SEARCH && vm.isSearchVisible) ||
                    (it == OverlayState.AUTHOR && vm.isAuthorVisible)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topVisible != null || vm.currentLibSub != LibrarySubScreen.NONE || vm.currentNav != NavItem.HOME) {
                        IconButton(onClick = {
                            if (topVisible == OverlayState.SEARCH) vm.isSearchVisible = false
                            else if (topVisible == OverlayState.AUTHOR) vm.isAuthorVisible = false
                            else if (vm.currentLibSub != LibrarySubScreen.NONE) vm.currentLibSub = LibrarySubScreen.NONE
                            else vm.currentNav = NavItem.HOME
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    }
                    Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            topVisible == OverlayState.AUTHOR -> vm.selectedAuthor?.name ?: "Канал"
                            topVisible == OverlayState.SEARCH -> "Результаты поиска"
                            vm.currentLibSub == LibrarySubScreen.LIKED -> "Понравившиеся"
                            vm.currentLibSub == LibrarySubScreen.WATCH_LATER -> "Смотреть позже"
                            vm.currentLibSub == LibrarySubScreen.PLAYLISTS -> "Плейлисты"
                            vm.currentLibSub == LibrarySubScreen.PLAYLIST_DETAIL -> vm.selectedPlaylist?.name ?: "Плейлист"
                            vm.currentLibSub == LibrarySubScreen.HISTORY -> "История просмотра"
                            else -> when (vm.currentNav) {
                                NavItem.SUBSCRIPTIONS -> "Подписки"
                                NavItem.LIBRARY -> "Библиотека"
                                NavItem.SETTINGS -> "Настройки"
                                else -> "Rupoop"
                            }
                        },
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                TextField(
                    value = vm.searchQuery, onValueChange = { vm.searchQuery = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Поиск") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.performSearch(vm.searchQuery) }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
            }
        },
        actions = {
            if (!vm.isSearchExpanded) {
                IconButton(onClick = { vm.isSearchExpanded = true }) { Icon(Icons.Default.Search, null) }
                IconButton(onClick = { vm.currentNav = NavItem.SETTINGS }) { Icon(Icons.Default.Settings, null) }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = {
                        if (!vm.isAuthenticated && !vm.isAuthenticating) authLauncher.launch(vm.authManager.createAuthIntent())
                        else if (vm.isAuthenticated) vm.isAccountMenuExpanded = true
                    }) {
                        if (vm.isAuthenticating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(
                            if (vm.isAuthenticated) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle, null,
                            tint = if (vm.isAuthenticated) Color(0xFF4CAF50) else LocalContentColor.current
                        )
                    }
                    if (vm.isAuthenticated) {
                        DropdownMenu(expanded = vm.isAccountMenuExpanded, onDismissRequest = { vm.isAccountMenuExpanded = false }) {
                            vm.githubUser?.let { user ->
                                DropdownMenuItem(text = { Text(user.login, fontWeight = FontWeight.Bold) }, onClick = {}, enabled = false)
                                HorizontalDivider()
                            }
                            DropdownMenuItem(text = { Text("Синхронизировать") }, onClick = { vm.isAccountMenuExpanded = false; vm.syncWithGitHub() })
                            DropdownMenuItem(text = { Text("Выйти") }, onClick = { vm.isAccountMenuExpanded = false; vm.logout() })
                        }
                    }
                }
            } else {
                IconButton(onClick = { vm.isSearchExpanded = false; vm.searchQuery = "" }) { Icon(Icons.Default.Close, null) }
            }
        }
    )
}

// Search overlay
@Composable
private fun SearchOverlay(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.searchResults) { video ->
                val history = vm.userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                VideoItem(video, history,
                    onClick = { vm.playVideo(video, vm.searchResults) },
                    onAuthorClick = { vm.loadAuthorVideos(it, false) },
                    onMoreClick = { action -> vm.handleVideoMoreAction(video, action) }
                )
            }
        }
    }
}

// Library content (with sub-screens)
@Composable
private fun LibraryContent(vm: AppViewModel) {
    when (vm.currentLibSub) {
        LibrarySubScreen.NONE -> {
            LibraryScreen(
                vm.userRegistry,
                onVideoClick = { item ->
                    vm.playVideo(
                        SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl)),
                        vm.userRegistry.watchHistory.map { SearchResult(videoUrl = it.videoUrl, title = it.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl)) }
                    )
                },
                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                onMoreClick = { item, action ->
                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))
                    when (action) {
                        "remove" -> vm.removeFromHistory(item.videoId)
                        "share" -> vm.shareVideo(video)
                        "download" -> vm.startDownload(video)
                        "later" -> vm.toggleWatchLater(video)
                        "playlist" -> vm.showPlaylistDialog = video
                    }
                },
                onActionClick = { action ->
                    when (action) {
                        "liked" -> vm.currentLibSub = LibrarySubScreen.LIKED
                        "later" -> vm.currentLibSub = LibrarySubScreen.WATCH_LATER
                        "playlists" -> vm.currentLibSub = LibrarySubScreen.PLAYLISTS
                        "history" -> vm.currentLibSub = LibrarySubScreen.HISTORY
                    }
                }
            )
        }
        LibrarySubScreen.HISTORY -> {
            LazyColumn(Modifier.fillMaxSize()) {
                val historyVideos = vm.userRegistry.watchHistory.map { item ->
                    SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl), duration = (item.totalDuration / 1000).toInt())
                }
                items(vm.userRegistry.watchHistory) { item ->
                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl), duration = (item.totalDuration / 1000).toInt())
                    VideoItem(video, item,
                        onClick = { vm.playVideo(video, historyVideos) },
                        onAuthorClick = { vm.loadAuthorVideos(it, false) },
                        onMoreClick = { action ->
                            when (action) {
                                "remove" -> vm.removeFromHistory(item.videoId)
                                "share" -> vm.shareVideo(video)
                                "download" -> vm.startDownload(video)
                            }
                        },
                        isEditMode = true
                    )
                }
            }
        }
        LibrarySubScreen.LIKED -> VideoListScreen(vm.userRegistry.likedVideos, { v -> vm.playVideo(v, vm.userRegistry.likedVideos) }, { vm.loadAuthorVideos(it, false) }, { vm.shareVideo(it) }, { video -> vm.toggleLike(video) }, { vm.startDownload(it) })
        LibrarySubScreen.WATCH_LATER -> VideoListScreen(vm.userRegistry.watchLater, { v -> vm.playVideo(v, vm.userRegistry.watchLater) }, { vm.loadAuthorVideos(it, false) }, { vm.shareVideo(it) }, { video -> vm.toggleWatchLater(video) }, { vm.startDownload(it) })
        LibrarySubScreen.PLAYLISTS -> {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.userRegistry.playlists) { playlist ->
                    LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, playlist.name, playlist.videos.size.toString(), onAction = { vm.deletePlaylist(playlist.id) }) {
                        vm.selectedPlaylist = playlist
                        vm.currentLibSub = LibrarySubScreen.PLAYLIST_DETAIL
                    }
                }
            }
        }
        LibrarySubScreen.PLAYLIST_DETAIL -> {
            VideoListScreen(
                vm.selectedPlaylist?.videos ?: emptyList(),
                { v -> vm.playVideo(v, vm.selectedPlaylist?.videos) },
                { vm.loadAuthorVideos(it, false) },
                { vm.shareVideo(it) },
                { video -> vm.selectedPlaylist?.let { vm.removeFromPlaylist(it.id, video.videoUrl) } },
                { vm.startDownload(it) }
            )
        }
    }
}
