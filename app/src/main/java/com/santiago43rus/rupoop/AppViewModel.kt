package com.santiago43rus.rupoop

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.auth.GistSyncManager
import com.santiago43rus.rupoop.auth.GitHubAuthManager
import com.santiago43rus.rupoop.auth.AuthController
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.player.PlaybackController
import android.annotation.SuppressLint
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.service.DownloadService
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    val context = application.applicationContext

    init {
        instance = this
    }

    val settingsManager = SettingsManager(context)
    val registryManager = UserRegistryManager(context)
    val mainFeedRecommender = MainFeedRecommendationStrategy(registryManager)
    val relatedVideoRecommender = RelatedVideoRecommendationStrategy(registryManager)
    val authManager = GitHubAuthManager(context)
    val syncManager = GistSyncManager(RetrofitClient.gistApi, registryManager, settingsManager)
    val downloadTracker = DownloadTracker(context)

    // ── Registry snapshot (observable) ──
    var userRegistry by mutableStateOf(registryManager.registry)

    // ── Snackbar events ──
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // ── Delegates / Controllers ──
    
    val playbackController: PlaybackController = PlaybackController(
        context = context,
        scope = viewModelScope,
        registryManager = registryManager,
        settingsManager = settingsManager,
        relatedVideoRecommender = relatedVideoRecommender,
        pushToGitHub = { pushToGitHub() },
        filterHiddenAndDisliked = { filterHiddenAndDisliked(it) },
        removeVideoFromUiLists = { removeVideoFromUiLists(it) },
        snackbarMessage = _snackbarMessage,
        onRegistryUpdate = { userRegistry = it }
    )

    val navigationController: NavigationController = NavigationController(
        getPlayerState = { playbackController.playerState },
        setPlayerState = { playbackController.playerState = it },
        getIsFullscreenVideo = { playbackController.isFullscreenVideo },
        setIsFullscreenVideo = { playbackController.isFullscreenVideo = it },
        stopPlayer = { playbackController.exoPlayer.stop() },
        pushToGitHub = { pushToGitHub() },
        updateSearchStates = { q, res, ord, exp, vis ->
            searchController.searchQuery = q
            searchResults = res
            searchController.searchSortOrder = ord
            searchController.isSearchExpanded = exp
            navigationController.isSearchVisible = vis
        }
    )

    val contentFeedController: ContentFeedController = ContentFeedController(
        scope = viewModelScope,
        settingsManager = settingsManager,
        registryManager = registryManager,
        mainFeedRecommender = mainFeedRecommender,
        filterHiddenAndDisliked = { filterHiddenAndDisliked(it) },
        onAuthorVisibleChanged = { navigationController.isAuthorVisible = it },
        getPlayerState = { playbackController.playerState },
        setPlayerState = { playbackController.playerState = it },
        getAuthorSortOrder = { authorSortOrder }
    )

    val searchController: SearchController = SearchController(
        scope = viewModelScope,
        registryManager = registryManager,
        pushToGitHub = { pushToGitHub() },
        filterHiddenAndDisliked = { filterHiddenAndDisliked(it) },
        getPlayerState = { playbackController.playerState },
        setPlayerState = { playbackController.playerState = it },
        getCurrentNav = { navigationController.currentNav },
        getSearchStacks = { navigationController.searchStacks },
        updateSearchStates = { q, res, ord, exp, vis ->
            searchController.searchQuery = q
            searchResults = res
            searchController.searchSortOrder = ord
            searchController.isSearchExpanded = exp
            navigationController.isSearchVisible = vis
        },
        getOverlayOrder = { navigationController.overlayOrder },
        setOverlayOrder = { navigationController.overlayOrder = it },
        getIsSearchVisible = { navigationController.isSearchVisible },
        setIsSearchVisible = { navigationController.isSearchVisible = it }
    )

    val authController: AuthController = AuthController(
        scope = viewModelScope,
        settingsManager = settingsManager,
        authManager = authManager,
        syncManager = syncManager,
        snackbarMessage = _snackbarMessage,
        onRegistryUpdate = { 
            userRegistry = it 
            registryManager.updateRegistry(it)
        },
        loadHome = { loadHome(it) },
        getRegistry = { registryManager.registry }
    )

    // ── Navigation Property Delegation ──
    var currentNav: NavItem
        get() = navigationController.currentNav
        set(value) { navigationController.currentNav = value }

    var currentLibSub: LibrarySubScreen
        get() = navigationController.currentLibSub
        set(value) { navigationController.currentLibSub = value }

    var selectedPlaylist: Playlist?
        get() = navigationController.selectedPlaylist
        set(value) { navigationController.selectedPlaylist = value }

    var selectedAuthor: Author?
        get() = navigationController.selectedAuthor
        set(value) { navigationController.selectedAuthor = value }

    var isAuthorVisible: Boolean
        get() = navigationController.isAuthorVisible
        set(value) { navigationController.isAuthorVisible = value }

    var isSettingsVisible: Boolean
        get() = navigationController.isSettingsVisible
        set(value) { navigationController.isSettingsVisible = value }

    var isHiddenVideosVisible: Boolean
        get() = navigationController.isHiddenVideosVisible
        set(value) { navigationController.isHiddenVideosVisible = value }

    var isNotificationSettingsVisible: Boolean
        get() = navigationController.isNotificationSettingsVisible
        set(value) { navigationController.isNotificationSettingsVisible = value }

    var isSearchVisible: Boolean
        get() = navigationController.isSearchVisible
        set(value) { navigationController.isSearchVisible = value }

    var overlayOrder: List<OverlayState>
        get() = navigationController.overlayOrder
        set(value) { navigationController.overlayOrder = value }

    fun restoreSearchStateForTab(tab: NavItem) = navigationController.restoreSearchStateForTab(tab)
    fun clearCurrentSearchStack() = navigationController.clearCurrentSearchStack()
    fun handleBack(): Boolean = navigationController.handleBack()

    // ── Playback Property Delegation ──
    val exoPlayer: ExoPlayer
        get() = playbackController.exoPlayer

    var currentVideoList: List<SearchResult>
        get() = playbackController.currentVideoList
        set(value) { playbackController.currentVideoList = value }

    var currentVideoIndex: Int
        get() = playbackController.currentVideoIndex
        set(value) { playbackController.currentVideoIndex = value }

    var isPlaylistMode: Boolean
        get() = playbackController.isPlaylistMode
        set(value) { playbackController.isPlaylistMode = value }

    var playerState: PlayerState
        get() = playbackController.playerState
        set(value) { playbackController.playerState = value }

    var currentVideo: SearchResult?
        get() = playbackController.currentVideo
        set(value) { playbackController.currentVideo = value }

    var isFullscreenVideo: Boolean
        get() = playbackController.isFullscreenVideo
        set(value) { playbackController.isFullscreenVideo = value }

    var isPlaying: Boolean
        get() = playbackController.isPlaying
        set(value) { playbackController.isPlaying = value }

    var isBuffering: Boolean
        get() = playbackController.isBuffering
        set(value) { playbackController.isBuffering = value }

    var isBackgroundPlaybackEnabled: Boolean
        get() = playbackController.isBackgroundPlaybackEnabled
        set(value) { playbackController.isBackgroundPlaybackEnabled = value }

    var relatedVideos: List<SearchResult>
        get() = playbackController.relatedVideos
        set(value) { playbackController.relatedVideos = value }

    fun playVideo(video: SearchResult, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
        playbackController.playVideo(video, list, isPlaylist)
    }
    fun playNext() = playbackController.playNext()
    fun playPrevious() = playbackController.playPrevious()
    fun playLocalFile(filePath: String, title: String, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
        playbackController.playLocalFile(filePath, title, list, isPlaylist)
    }
    fun closePlayer() = playbackController.closePlayer()
    fun startProgressSaving() = playbackController.startProgressSaving()
    fun stopProgressSaving() = playbackController.stopProgressSaving()
    fun toggleBackgroundPlayback() = playbackController.toggleBackgroundPlayback()

    // ── Auth Property Delegation ──
    var isAuthenticating: Boolean
        get() = authController.isAuthenticating
        set(value) { authController.isAuthenticating = value }

    var isAuthenticated: Boolean
        get() = authController.isAuthenticated
        set(value) { authController.isAuthenticated = value }

    var githubUser: GitHubUser?
        get() = authController.githubUser
        set(value) { authController.githubUser = value }

    var isAccountMenuExpanded: Boolean
        get() = authController.isAccountMenuExpanded
        set(value) { authController.isAccountMenuExpanded = value }

    fun initializeApp() = authController.initializeApp()
    fun processAuthResponse(response: net.openid.appauth.AuthorizationResponse) = authController.processAuthResponse(response)
    fun onAuthSuccess(token: String) = authController.onAuthSuccess(token)
    fun logout() = authController.logout()

    // ── Search Property Delegation ──
    var searchQuery: String
        get() = searchController.searchQuery
        set(value) { searchController.searchQuery = value }

    var searchSuggestions: List<String>
        get() = searchController.searchSuggestions
        set(value) { searchController.searchSuggestions = value }

    var isSearchExpanded: Boolean
        get() = searchController.isSearchExpanded
        set(value) { searchController.isSearchExpanded = value }

    var searchSortOrder: String?
        get() = searchController.searchSortOrder
        set(value) { searchController.searchSortOrder = value }

    fun updateSearchQuery(query: String) = searchController.updateSearchQuery(query)
    fun performSearch(query: String, ordering: String? = searchSortOrder) = searchController.performSearch(query, ordering)

    // ── Feed Property Delegation ──
    var homeVideos: List<SearchResult>
        get() = contentFeedController.homeVideos
        set(value) { contentFeedController.homeVideos = value }

    var subscriptionVideos: List<SearchResult>
        get() = contentFeedController.subscriptionVideos
        set(value) { contentFeedController.subscriptionVideos = value }

    var authorVideos: List<SearchResult>
        get() = contentFeedController.authorVideos
        set(value) { contentFeedController.authorVideos = value }

    var isHomeLoadingMore: Boolean
        get() = contentFeedController.isHomeLoadingMore
        set(value) { contentFeedController.isHomeLoadingMore = value }

    var isRefreshingHome: Boolean
        get() = contentFeedController.isRefreshingHome
        set(value) { contentFeedController.isRefreshingHome = value }

    var isAuthorLoadingMore: Boolean
        get() = contentFeedController.isAuthorLoadingMore
        set(value) { contentFeedController.isAuthorLoadingMore = value }

    var isRefreshingAuthor: Boolean
        get() = contentFeedController.isRefreshingAuthor
        set(value) { contentFeedController.isRefreshingAuthor = value }

    var authorPage: Int
        get() = contentFeedController.authorPage
        set(value) { contentFeedController.authorPage = value }

    var hasMoreAuthorVideos: Boolean
        get() = contentFeedController.hasMoreAuthorVideos
        set(value) { contentFeedController.hasMoreAuthorVideos = value }

    var isSubsLoadingMore: Boolean
        get() = contentFeedController.isSubsLoadingMore
        set(value) { contentFeedController.isSubsLoadingMore = value }

    var isRefreshingSubs: Boolean
        get() = contentFeedController.isRefreshingSubs
        set(value) { contentFeedController.isRefreshingSubs = value }

    var hasMoreSubsVideos: Boolean
        get() = contentFeedController.hasMoreSubsVideos
        set(value) { contentFeedController.hasMoreSubsVideos = value }

    fun loadHome(isLoadMore: Boolean) = contentFeedController.loadHome(isLoadMore)
    fun loadSubscriptions(isLoadMore: Boolean) = contentFeedController.loadSubscriptions(isLoadMore)
    fun loadAuthorVideos(author: Author, isLoadMore: Boolean) {
        contentFeedController.loadAuthorVideos(author, isLoadMore) { selected ->
            selectedAuthor = selected
        }
    }

    // ── Settings & Notification Notifications ──
    var showDownloadNotifications by mutableStateOf(settingsManager.showDownloadNotifications)
    var showBackgroundNotifications by mutableStateOf(settingsManager.showBackgroundNotifications)

    fun syncPlaybackService() {
        playbackController.syncPlaybackService()
    }

    fun updateDownloadNotifications(enabled: Boolean) {
        showDownloadNotifications = enabled
        settingsManager.showDownloadNotifications = enabled
    }

    fun updateBackgroundNotifications(enabled: Boolean) {
        showBackgroundNotifications = enabled
        settingsManager.showBackgroundNotifications = enabled
        syncPlaybackService()
    }

    // ── Dialog states ──
    var showPlaylistDialog by mutableStateOf<SearchResult?>(null)
    var showDownloadDialog by mutableStateOf<SearchResult?>(null)
    var showOnboarding by mutableStateOf(settingsManager.isFirstLaunch)

    // ── Static/Extra Search States referenced by Nav/Search stacks ──
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var authorSortOrder by mutableStateOf("-publication_ts") // default newest

    // ── Push to GitHub (debounced) ──
    private var pushJob: Job? = null
    fun pushToGitHub() {
        val token = settingsManager.accessToken ?: return
        if (!isNetworkAvailable(context)) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(5000) // debounce 5 seconds
            
            val appSettings = AppSettings(
                theme = settingsManager.themeMode,
                downloadQuality = settingsManager.downloadQuality,
                syncFrequencyHours = settingsManager.syncFrequencyHours,
                adultContentEnabled = settingsManager.adultContentEnabled,
                kidsContentEnabled = settingsManager.kidsContentEnabled,
                enabledGenres = settingsManager.enabledGenres.toList()
            )
            val updatedRegistry = registryManager.registry.copy(appSettings = appSettings)

            syncManager.push(token, updatedRegistry)
        }
    }

    // ── Deep link ──
    fun handleDeepLink(url: String) {
        val video = SearchResult(videoUrl = url, title = "Загрузка...")
        playVideo(video, null)
    }

    // ── Download ──
    fun startDownload(video: SearchResult, isAudio: Boolean = false) {
        viewModelScope.launch {
            if (!isNetworkAvailable(context)) {
                _snackbarMessage.emit("Нет подключения к интернету")
                return@launch
            }
            _snackbarMessage.emit("Скачивание начато: ${video.title}")
            extractId(video.videoUrl)?.let { id ->
                val uniqueId = "${id}___${System.currentTimeMillis()}"
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    opt.videoBalancer?.m3u8?.let { m3u8Url ->
                        val serviceIntent = Intent(context, DownloadService::class.java).apply {
                            putExtra("VIDEO_URL", m3u8Url)
                            putExtra("TITLE", video.title)
                            putExtra("VIDEO_ID", uniqueId)
                            putExtra("THUMBNAIL_URL", video.thumbnailUrl)
                            putExtra("QUALITY", settingsManager.downloadQuality)
                            putExtra("IS_AUDIO", isAudio)
                        }
                        val showNotif = settingsManager.showDownloadNotifications
                        if (showNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RupoopDownload", "Error starting download", e)
                    _snackbarMessage.emit("Ошибка начала загрузки")
                }
            }
        }
    }

    // ── Sync ──
    fun syncWithGitHub() {
        val token = settingsManager.accessToken ?: return
        viewModelScope.launch {
            try {
                userRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                settingsManager.lastSyncTime = System.currentTimeMillis()
                pushJob = null

                try {
                    val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e("Rupoop", "Failed to start PlaybackService", e)
                }
            } catch (_: Exception) {
                _snackbarMessage.emit("Ошибка синхронизации")
            }
        }
    }

    // ── Share ──
    fun shareVideo(video: SearchResult) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${video.title}\n${video.videoUrl}")
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // ── Fullscreen ──
    fun toggleFullscreen(fill: Boolean) {
        isFullscreenVideo = fill
    }

    // ── Subscription toggle ──
    fun toggleSubscription(author: Author) {
        val subs = userRegistry.subscriptions.toMutableList()
        if (subs.any { it.name.equals(author.name, ignoreCase = true) }) {
            subs.removeAll { it.name.equals(author.name, ignoreCase = true) }
        } else {
            subs.add(author)
        }
        registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
        userRegistry = registryManager.registry
        pushToGitHub()
    }

    // ── Like ──
    fun toggleLike(video: SearchResult) {
        val added = registryManager.toggleLike(video)
        userRegistry = registryManager.registry
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit(if (added) "Добавлено в Понравившиеся" else "Удалено из Понравившихся")
        }
    }

    // ── Dislike ──
    fun toggleDislike(video: SearchResult) {
        val added = registryManager.toggleDislike(video)
        userRegistry = registryManager.registry
        if (added) {
            removeVideoFromUiLists(video)
        }
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit(if (added) "Добавлено в Не нравится" else "Удалено из Не нравится")
        }
    }

    // ── Watch Later ──
    fun toggleWatchLater(video: SearchResult) {
        val added = registryManager.toggleWatchLater(video)
        userRegistry = registryManager.registry
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit(if (added) "Добавлено in Смотреть позже" else "Удалено из Смотреть позже")
        }
    }

    fun addToWatchLaterViaMenu(video: SearchResult) {
        val exists = userRegistry.watchLater.any { extractId(it.videoUrl) == extractId(video.videoUrl) }
        if (exists) {
            viewModelScope.launch {
                _snackbarMessage.emit("Видео уже находится в разделе \"Смотреть позже\"")
            }
        } else {
            registryManager.toggleWatchLater(video)
            userRegistry = registryManager.registry
            pushToGitHub()
            viewModelScope.launch {
                _snackbarMessage.emit("Добавлено in Смотреть позже")
            }
        }
    }

    // ── Playlist ──
    fun addToPlaylist(name: String, video: SearchResult) {
        val added = registryManager.addToPlaylist(name, video)
        userRegistry = registryManager.registry
        showPlaylistDialog = null
        pushToGitHub()
        viewModelScope.launch {
            if (added) {
                _snackbarMessage.emit("Добавлено в $name")
            } else {
                _snackbarMessage.emit("Видео уже добавлено в плейлист \"$name\"")
            }
        }
    }

    fun createPlaylistAndAdd(name: String, video: SearchResult) {
        registryManager.addToPlaylist(name, video)
        userRegistry = registryManager.registry
        showPlaylistDialog = null
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit("Плейлист $name создан")
        }
    }

    // ── History ──
    fun removeFromHistory(videoId: String) {
        registryManager.removeFromHistory(videoId)
        userRegistry = registryManager.registry
        pushToGitHub()
    }

    fun removeSearchQuery(query: String) {
        registryManager.removeSearchQuery(query)
        userRegistry = registryManager.registry
        pushToGitHub()
    }

    fun deletePlaylist(id: String) {
        registryManager.deletePlaylist(id)
        userRegistry = registryManager.registry
        pushToGitHub()
    }

    fun removeFromPlaylist(playlistId: String, videoUrl: String) {
        registryManager.removeFromPlaylist(playlistId, videoUrl)
        userRegistry = registryManager.registry
        selectedPlaylist = userRegistry.playlists.find { it.id == playlistId }
        pushToGitHub()
    }

    // ── Video action handler (used across screens) ──
    fun handleVideoMoreAction(video: SearchResult, action: String) {
        when (action) {
            "later" -> addToWatchLaterViaMenu(video)
            "playlist" -> showPlaylistDialog = video
            "share" -> shareVideo(video)
            "download" -> showDownloadDialog = video
            "dislike" -> {
                registryManager.toggleDislike(video)
                userRegistry = registryManager.registry
                removeVideoFromUiLists(video)
                pushToGitHub()
                _snackbarMessage.tryEmit("Видео отмечено как \"Не нравится\"")
            }
            "not_interested" -> {
                registryManager.hideVideo(video)
                registryManager.hideTitle(video.title)
                val videoId = extractId(video.videoUrl)
                if (videoId != null && !userRegistry.dislikedVideos.contains(videoId)) {
                    registryManager.toggleDislike(video)
                }
                userRegistry = registryManager.registry
                removeVideoFromUiLists(video)
                pushToGitHub()
                _snackbarMessage.tryEmit("Видео и его аналоги скрыты из ленты")
            }
        }
    }

    fun removeVideoFromUiLists(video: SearchResult) {
        val videoId = extractId(video.videoUrl)
        val title = video.title
        val filterPredicate: (SearchResult) -> Boolean = { item ->
            val itemId = extractId(item.videoUrl)
            itemId != videoId && !item.title.contains(title, ignoreCase = true)
        }
        homeVideos = homeVideos.filter(filterPredicate)
        searchResults = searchResults.filter(filterPredicate)
        subscriptionVideos = subscriptionVideos.filter(filterPredicate)
        authorVideos = authorVideos.filter(filterPredicate)
        relatedVideos = relatedVideos.filter(filterPredicate)
    }

    fun filterHiddenAndDisliked(videos: List<SearchResult>): List<SearchResult> {
        val hiddenIds = userRegistry.hiddenVideos.toSet()
        val dislikedIds = userRegistry.dislikedVideos.toSet()
        val hiddenTitles = userRegistry.hiddenTitles.toSet()
        return videos.filter { video ->
            val id = extractId(video.videoUrl)
            if (id in hiddenIds || id in dislikedIds) false
            else if (hiddenTitles.any { video.title.contains(it, ignoreCase = true) }) false
            else true
        }
    }

    fun isPreviousVideoDislikedOrHidden(): Boolean {
        if (currentVideoIndex <= 0 || currentVideoIndex >= currentVideoList.size) return false
        val prevVideo = currentVideoList[currentVideoIndex - 1]
        val prevId = extractId(prevVideo.videoUrl)
        val hiddenIds = userRegistry.hiddenVideos.toSet()
        val dislikedIds = userRegistry.dislikedVideos.toSet()
        val hiddenTitles = userRegistry.hiddenTitles.toSet()
        
        return prevId in hiddenIds || prevId in dislikedIds || hiddenTitles.any { prevVideo.title.contains(it, ignoreCase = true) }
    }

    // ── Onboarding ──
    fun dismissOnboarding() {
        showOnboarding = false
        loadHome(false)
    }

    // ── Registry update (from settings) ──
    fun onRegistryUpdate(registry: UserRegistry) {
        userRegistry = registry
        pushToGitHub()
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
        sharedPlayer = null
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        var sharedPlayer: ExoPlayer?
            get() = instance?.playbackController?.exoPlayer
            set(value) {}
        var instance: AppViewModel? = null
    }
}
