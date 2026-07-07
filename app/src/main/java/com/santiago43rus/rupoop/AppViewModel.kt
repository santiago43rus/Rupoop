package com.santiago43rus.rupoop

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.auth.AuthController
import com.santiago43rus.rupoop.auth.GistSyncManager
import com.santiago43rus.rupoop.auth.GitHubAuthManager
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.player.PlaybackController
import com.santiago43rus.rupoop.player.*
import com.santiago43rus.rupoop.service.DownloadService
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
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

    var userRegistry by mutableStateOf(registryManager.registry)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

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

    fun updateIsSearchVisible(vis: Boolean) {
        navigationController.isSearchVisible = vis
    }

    val navigationController: NavigationController = NavigationController(
        getPlayerState = { playbackController.playerState },
        setPlayerState = { playbackController.playerState = it },
        getIsFullscreenVideo = { playbackController.isFullscreenVideo },
        setIsFullscreenVideo = { playbackController.isFullscreenVideo = it },
        stopPlayer = { playbackController.exoPlayer.stop() },
        pushToGitHub = { pushToGitHub() },
        updateSearchStates = { q, res, o, exp, vis ->
            searchController.searchQuery = q
            searchResults = res
            searchController.searchSortOrder = o
            searchController.isSearchExpanded = exp
            updateIsSearchVisible(vis)
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
        updateSearchStates = { q, res, o, exp, vis ->
            navigationController.searchQuery = q
            searchResults = res
            navigationController.searchSortOrder = o
            navigationController.isSearchExpanded = exp
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
            contentFeedController.loadHome(false)
        },
        loadHome = { contentFeedController.loadHome(it) },
        getRegistry = { registryManager.registry }
    )

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

    var showPlaylistDialog by mutableStateOf<SearchResult?>(null)
    var showDownloadDialog by mutableStateOf<SearchResult?>(null)
    var showOnboarding by mutableStateOf(settingsManager.isFirstLaunch)

    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var authorSortOrder by mutableStateOf("-publication_ts")

    private var pushJob: Job? = null
    fun pushToGitHub() {
        val token = settingsManager.accessToken ?: return
        if (!isNetworkAvailable(context)) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(5000)
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

    fun handleDeepLink(url: String) {
        val video = SearchResult(videoUrl = url, title = "Загрузка...")
        playVideo(video, null)
    }

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    fun shareVideo(video: SearchResult) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${video.title}\n${video.videoUrl}")
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun toggleFullscreen(fill: Boolean) {
        isFullscreenVideo = fill
    }



    fun dismissOnboarding() {
        showOnboarding = false
        loadHome(false)
    }

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
