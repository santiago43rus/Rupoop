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
import com.santiago43rus.rupoop.data.*
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

    // ── Snackbar events ──
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // ── Auth state ──
    var isAuthenticating by mutableStateOf(false)
    var isAuthenticated by mutableStateOf(settingsManager.accessToken != null)
    var githubUser by mutableStateOf<GitHubUser?>(null)
    var isAccountMenuExpanded by mutableStateOf(false)

    // ── Navigation ──
    private val _currentNavState = mutableStateOf(NavItem.HOME)
    var currentNav: NavItem
        get() = _currentNavState.value
        set(value) {
            val oldNav = _currentNavState.value
            if (oldNav != value) {
                // Save current author state before switching
                authorStates[oldNav] = AuthorState(
                    isVisible = isAuthorVisible,
                    author = selectedAuthor,
                    videos = authorVideos,
                    page = authorPage,
                    hasMore = hasMoreAuthorVideos
                )

                _currentNavState.value = value
                restoreSearchStateForTab(value)

                // Restore author state for the new tab
                val authorState = authorStates[value] ?: AuthorState(false, null, emptyList(), 1, true)
                isAuthorVisible = authorState.isVisible
                selectedAuthor = authorState.author
                authorVideos = authorState.videos
                authorPage = authorState.page
                hasMoreAuthorVideos = authorState.hasMore
            }
        }
    var currentLibSub by mutableStateOf(LibrarySubScreen.NONE)
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
    var selectedAuthor by mutableStateOf<Author?>(null)

    // ── Author State Per Tab ──
    data class AuthorState(
        val isVisible: Boolean,
        val author: Author?,
        val videos: List<SearchResult>,
        val page: Int,
        val hasMore: Boolean
    )

    private val authorStates = mutableMapOf<NavItem, AuthorState>(
        NavItem.HOME to AuthorState(false, null, emptyList(), 1, true),
        NavItem.SUBSCRIPTIONS to AuthorState(false, null, emptyList(), 1, true),
        NavItem.LIBRARY to AuthorState(false, null, emptyList(), 1, true)
    )

    // ── Videos data ──
    var homeVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var subscriptionVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var authorVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var relatedVideos by mutableStateOf<List<SearchResult>>(emptyList())

    // ── Playback queue ──
    var currentVideoList by mutableStateOf<List<SearchResult>>(emptyList())
    var currentVideoIndex by mutableIntStateOf(-1)
    var isPlaylistMode by mutableStateOf(false)

    // ── Player state ──
    var playerState by mutableStateOf(PlayerState.CLOSED)
    var currentVideo by mutableStateOf<SearchResult?>(null)
    var isFullscreenVideo by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)

    // ── Search State Per Tab ──
    data class SearchState(
        val query: String,
        val results: List<SearchResult>,
        val ordering: String? = null
    )

    private val searchStacks = mutableMapOf<NavItem, MutableList<SearchState>>(
        NavItem.HOME to mutableListOf(),
        NavItem.SUBSCRIPTIONS to mutableListOf(),
        NavItem.LIBRARY to mutableListOf()
    )

    fun restoreSearchStateForTab(tab: NavItem) {
        val stack = searchStacks[tab] ?: mutableListOf()
        if (stack.isNotEmpty()) {
            val top = stack.last()
            searchQuery = top.query
            searchResults = top.results
            searchSortOrder = top.ordering
            isSearchVisible = true
            isSearchExpanded = false
            if (!overlayOrder.contains(OverlayState.SEARCH)) {
                overlayOrder = overlayOrder + OverlayState.SEARCH
            }
        } else {
            searchQuery = ""
            searchResults = emptyList()
            isSearchVisible = false
            isSearchExpanded = false
            overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
        }
    }

    fun clearCurrentSearchStack() {
        searchStacks[currentNav]?.clear()
        searchQuery = ""
        searchResults = emptyList()
        isSearchVisible = false
        isSearchExpanded = false
        overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
    }

    // ── Search ──
    var searchQuery by mutableStateOf("")
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var isSearchExpanded by mutableStateOf(false)
    var searchSortOrder by mutableStateOf<String?>(null) // null = default, "-created_ts" = newest
    var authorSortOrder by mutableStateOf("-publication_ts") // default newest

    // ── Overlays ──
    var isSearchVisible by mutableStateOf(false)
    var isAuthorVisible by mutableStateOf(false)
    var isSettingsVisible by mutableStateOf(false)
    var isHiddenVideosVisible by mutableStateOf(false)
    var isNotificationSettingsVisible by mutableStateOf(false)
    var overlayOrder by mutableStateOf(listOf(OverlayState.SEARCH, OverlayState.AUTHOR))
    
    var isBackgroundPlaybackEnabled by mutableStateOf(false)

    fun toggleBackgroundPlayback() {
        isBackgroundPlaybackEnabled = !isBackgroundPlaybackEnabled
        syncPlaybackService()
    }
    
    var showDownloadNotifications by mutableStateOf(settingsManager.showDownloadNotifications)
    var showBackgroundNotifications by mutableStateOf(settingsManager.showBackgroundNotifications)

    fun syncPlaybackService() {
        val shouldRun = showBackgroundNotifications && isBackgroundPlaybackEnabled && playerState != PlayerState.CLOSED
        if (shouldRun) {
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
        } else {
            try {
                val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("Rupoop", "Failed to stop PlaybackService", e)
            }
        }
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

    // ── Dialogs ──
    var showPlaylistDialog by mutableStateOf<SearchResult?>(null)
    var showDownloadDialog by mutableStateOf<SearchResult?>(null)
    var showOnboarding by mutableStateOf(settingsManager.isFirstLaunch)

    // ── Registry snapshot (observable) ──
    var userRegistry by mutableStateOf(registryManager.registry)

    // ── Pagination: Home ──
    var isHomeLoadingMore by mutableStateOf(false)
    var isRefreshingHome by mutableStateOf(false)

    // ── Pagination: Author ──
    var isAuthorLoadingMore by mutableStateOf(false)
    var isRefreshingAuthor by mutableStateOf(false)
    var authorPage by mutableIntStateOf(1)
    var hasMoreAuthorVideos by mutableStateOf(true)

    // ── Pagination: Subscriptions ──
    private var subsPage by mutableIntStateOf(1)
    var hasMoreSubsVideos by mutableStateOf(true)
    var isSubsLoadingMore by mutableStateOf(false)
    var isRefreshingSubs by mutableStateOf(false)

    // ── ExoPlayer ──
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().also { player ->
        player.playWhenReady = true
        sharedPlayer = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { this@AppViewModel.isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("Rupoop", "ExoPlayer error", error)
                viewModelScope.launch {
                    _snackbarMessage.emit("Ошибка воспроизведения: видео недоступно или отсутствует подключение")
                }
                playerState = PlayerState.CLOSED
            }
            override fun onPlaybackStateChanged(state: Int) {
                this@AppViewModel.isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && currentVideo != null) {
                    extractId(currentVideo!!.videoUrl)?.let { id ->
                        registryManager.updateWatchProgress(id, player.contentPosition, player.duration)
                        userRegistry = registryManager.registry
                    }
                }
                if (state == Player.STATE_ENDED && currentVideo != null) {
                    extractId(currentVideo!!.videoUrl)?.let { id ->
                        registryManager.updateWatchProgress(id, player.duration, player.duration)
                        userRegistry = registryManager.registry
                        pushToGitHub()
                    }
                }
            }
        })
    }

    private var videoLoadingJob: Job? = null
    private var progressSavingJob: Job? = null
    private var pushJob: Job? = null

    // ── Periodic progress saving ──
    fun startProgressSaving() {
        progressSavingJob?.cancel()
        progressSavingJob = viewModelScope.launch {
            while (true) {
                delay(15000)
                if (isPlaying && currentVideo != null) {
                    extractId(currentVideo!!.videoUrl)?.let { id ->
                        registryManager.updateWatchProgress(id, exoPlayer.currentPosition, exoPlayer.duration)
                        userRegistry = registryManager.registry
                        pushToGitHub()
                    }
                }
            }
        }
    }

    fun stopProgressSaving() {
        progressSavingJob?.cancel()
    }

    // ── Push to GitHub (debounced) ──
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

    // ── Play video ──
    fun playVideo(video: SearchResult, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
        val isLocal = video.videoUrl.isNotEmpty() && !video.videoUrl.startsWith("http")
        if (isLocal) {
            val file = java.io.File(video.videoUrl)
            if (!file.exists()) {
                viewModelScope.launch {
                    _snackbarMessage.emit("Видео не найдено")
                }
                playerState = PlayerState.CLOSED
                return
            }
            playLocalFile(video.videoUrl, video.title, list, isPlaylist)
            return
        }

        videoLoadingJob?.cancel()

        if (isPlaylist && list != null) {
            isPlaylistMode = true
            currentVideoList = list
            currentVideoIndex = list.indexOfFirst { it.videoUrl == video.videoUrl }.takeIf { it >= 0 } ?: 0
        } else {
            // Default mode (history navigation)
            if (!isPlaylistMode) {
                // We are already in default mode
                if (list == relatedVideos || list == currentVideoList) {
                    // Clicked a related video or a video in the current dynamic history
                    val existingIndex = currentVideoList.indexOfFirst { it.videoUrl == video.videoUrl }
                    if (existingIndex != -1 && list == currentVideoList) {
                        currentVideoIndex = existingIndex
                    } else {
                        // Truncate history after current index and add new video
                        val newHistory = if (currentVideoIndex >= 0) {
                            currentVideoList.take(currentVideoIndex + 1).toMutableList()
                        } else mutableListOf()
                        newHistory.add(video)
                        currentVideoList = newHistory
                        currentVideoIndex = newHistory.size - 1
                    }
                } else {
                    // Clicked a video from Home, Search, History, etc.
                    isPlaylistMode = false
                    currentVideoList = listOf(video)
                    currentVideoIndex = 0
                }
            } else {
                // Switching from playlist mode to default mode (e.g. clicked related video)
                isPlaylistMode = false
                currentVideoList = listOf(video)
                currentVideoIndex = 0
            }
        }

        currentVideo = video

        extractId(video.videoUrl)?.let { id ->
            val historyItem = userRegistry.watchHistory.find { it.videoId == id }

            registryManager.addWatchHistory(WatchHistoryItem(
                videoId = id,
                timestamp = System.currentTimeMillis(),
                progress = historyItem?.progress ?: 0,
                totalDuration = video.duration?.toLong()?.times(1000) ?: historyItem?.totalDuration ?: 0,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                authorName = video.author?.name,
                authorAvatarUrl = video.author?.avatarUrl,
                authorId = video.author?.id,
                videoUrl = video.videoUrl
            ))
            userRegistry = registryManager.registry
            pushToGitHub()

            videoLoadingJob = viewModelScope.launch {
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    val url = opt.videoBalancer?.m3u8
                    if (url != null) {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        
                        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(video.title)
                            .setArtist(video.author?.name)
                            .setArtworkUri(video.thumbnailUrl?.let { android.net.Uri.parse(it) })
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(id)
                            .setMediaMetadata(mediaMetadata)
                            .build()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        
                        syncPlaybackService()
                        
                        val historyProg = historyItem?.progress ?: 0
                        val historyTotal = historyItem?.totalDuration ?: 0
                        if (historyTotal > 0 && (historyProg.toFloat() / historyTotal) >= 0.95f) {
                            // If watched > 95%, start from beginning
                            exoPlayer.seekTo(0)
                        } else if (historyProg > 0) {
                            exoPlayer.seekTo(historyProg)
                        }
                        
                        exoPlayer.play()
                        playerState = PlayerState.FULL
                    } else {
                        _snackbarMessage.emit("Видео не найдено")
                        playerState = PlayerState.CLOSED
                    }

                    val relatedResults = withContext(Dispatchers.IO) {
                        val queries = relatedVideoRecommender.getSearchQueries(video)
                        val allResults = mutableListOf<SearchResult>()
                        for (q in queries) {
                            try {
                                val res = RetrofitClient.api.searchVideos(q).results
                                allResults.addAll(res)
                                if (allResults.size > 20) break
                            } catch (_: Exception) {}
                        }
                        allResults.distinctBy { it.videoUrl }
                    }
                    val filteredRelated = relatedVideoRecommender.recommendRelated(video, relatedResults)
                    val finalRelated = filterHiddenAndDisliked(filteredRelated)
                    relatedVideos = finalRelated

                    if (!isPlaylistMode) {
                        currentVideoList = currentVideoList.take(currentVideoIndex + 1) + finalRelated
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Play error", e)
                    _snackbarMessage.emit("Видео не найдено")
                    playerState = PlayerState.CLOSED
                }
            }
        }
    }

    fun playNext() {
        if (isPlaylistMode) {
            if (currentVideoIndex < currentVideoList.size - 1) {
                playVideo(currentVideoList[currentVideoIndex + 1], currentVideoList, true)
            }
        } else {
            if (currentVideoIndex < currentVideoList.size - 1) {
                playVideo(currentVideoList[currentVideoIndex + 1], currentVideoList, false)
            } else if (relatedVideos.isNotEmpty()) {
                playVideo(relatedVideos.first(), relatedVideos, false)
            }
        }
    }

    fun playPrevious() {
        if (currentVideoIndex > 0) {
            playVideo(currentVideoList[currentVideoIndex - 1], currentVideoList, isPlaylistMode)
        }
    }

    // ── Play local file ──
    fun playLocalFile(filePath: String, title: String, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
        videoLoadingJob?.cancel()
        val file = java.io.File(filePath)
        if (!file.exists()) return
        
        val video = SearchResult(videoUrl = filePath, title = title)
        currentVideo = video
        if (isPlaylist && list != null) {
            isPlaylistMode = true
            currentVideoList = list
            currentVideoIndex = list.indexOfFirst { it.videoUrl == filePath }.takeIf { it >= 0 } ?: 0
        } else {
            isPlaylistMode = false
            currentVideoList = listOf(video)
            currentVideoIndex = 0
        }
        relatedVideos = emptyList()

        val uniqueId = extractId(filePath) ?: filePath
        val historyItem = userRegistry.watchHistory.find { it.videoId == uniqueId }

        registryManager.addWatchHistory(WatchHistoryItem(
            videoId = uniqueId,
            timestamp = System.currentTimeMillis(),
            progress = historyItem?.progress ?: 0,
            totalDuration = historyItem?.totalDuration ?: 0,
            title = title,
            thumbnailUrl = null,
            authorName = "Локальный файл",
            authorAvatarUrl = null,
            authorId = null,
            videoUrl = filePath
        ))
        userRegistry = registryManager.registry
        pushToGitHub()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(file))
            .setMediaId(filePath)
            .setMediaMetadata(mediaMetadata)
            .apply {
                if (filePath.endsWith(".mp3") || filePath.endsWith(".m4a")) {
                    setMimeType("audio/mp4")
                }
            }
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val historyProg = historyItem?.progress ?: 0
        val historyTotal = historyItem?.totalDuration ?: 0
        if (historyTotal > 0 && (historyProg.toFloat() / historyTotal) >= 0.95f) {
            exoPlayer.seekTo(0)
        } else if (historyProg > 0) {
            exoPlayer.seekTo(historyProg)
        }

        exoPlayer.play()
        playerState = PlayerState.FULL

        syncPlaybackService()
    }

    // ── Deep link ──
    fun handleDeepLink(url: String) {
        val video = SearchResult(videoUrl = url, title = "Загрузка...")
        playVideo(video, null)
    }

    // ── Load home ──
    fun loadHome(isLoadMore: Boolean) {
        viewModelScope.launch {
            if (isLoadMore) isHomeLoadingMore = true
            else { isRefreshingHome = true; homeVideos = emptyList() }

            try {
                val enabledGenres = settingsManager.enabledGenres.toList()
                val selectedGenres = if (enabledGenres.size > 4) {
                    enabledGenres.shuffled().take(4)
                } else enabledGenres

                val filmGenres = listOf("боевик", "комедия", "драма", "ужасы", "фантастика", "триллер", "детектив", "мелодрама")
                val queries = selectedGenres.map { genre ->
                    if (filmGenres.any { it.equals(genre, ignoreCase = true) }) {
                        "$genre фильм"
                    } else {
                        genre
                    }
                }.toMutableList()

                if (queries.isEmpty()) queries.add("популярное")

                val deferreds = queries.map { query ->
                    viewModelScope.async(Dispatchers.IO) {
                        try {
                            val targetPage = if (isLoadMore) (2..4).random() else 1
                            RetrofitClient.api.searchVideos(query, page = targetPage).results
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Error fetching home genre query: $query", e)
                            emptyList<SearchResult>()
                        }
                    }
                }

                val allResults = deferreds.awaitAll().flatten().distinctBy { it.videoUrl }
                val newVideos = mainFeedRecommender.recommend(allResults)

                val updatedList = if (isLoadMore) (homeVideos + newVideos).distinctBy { it.videoUrl } else newVideos
                homeVideos = updatedList.take(200)
            } catch (e: Exception) {
                Log.e("Rupoop", "Error loading home", e)
            } finally {
                isRefreshingHome = false
                isHomeLoadingMore = false
            }
        }
    }

    // ── Load subscriptions ──
    fun loadSubscriptions(isLoadMore: Boolean) {
        viewModelScope.launch {
            if (isLoadMore) {
                isSubsLoadingMore = true
                subsPage++
            } else {
                isRefreshingSubs = true
                subsPage = 1
                hasMoreSubsVideos = true
                subscriptionVideos = emptyList()
            }

            if (userRegistry.subscriptions.isNotEmpty()) {
                try {
                    val allSubsVideos = mutableListOf<SearchResult>()
                    userRegistry.subscriptions.forEach { author ->
                        val resp = if (author.id != null) {
                            withContext(Dispatchers.IO) { RetrofitClient.api.getAuthorVideos(author.id.toString(), page = subsPage) }
                        } else {
                            withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, page = subsPage) }
                        }
                        allSubsVideos.addAll(resp.results.filter {
                            it.author?.name?.equals(author.name, ignoreCase = true) == true
                        })
                    }

                    if (allSubsVideos.isEmpty()) {
                        hasMoreSubsVideos = false
                    } else {
                        val sortedNewVideos = allSubsVideos.sortedByDescending { it.createdTs ?: "" }
                        val filteredNewVideos = filterHiddenAndDisliked(sortedNewVideos)
                        subscriptionVideos = if (isLoadMore) {
                            (subscriptionVideos + filteredNewVideos).distinctBy { it.videoUrl }
                        } else {
                            filteredNewVideos.distinctBy { it.videoUrl }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Subs load error", e)
                    if (isLoadMore) subsPage--
                }
            } else {
                subscriptionVideos = emptyList()
                hasMoreSubsVideos = false
            }
            isRefreshingSubs = false
            isSubsLoadingMore = false
        }
    }

    // ── Load author videos ──
    fun loadAuthorVideos(author: Author, isLoadMore: Boolean) {
        if (!isLoadMore || hasMoreAuthorVideos) {
            viewModelScope.launch {
                if (isLoadMore) {
                    isAuthorLoadingMore = true
                    authorPage++
                } else {
                    isRefreshingAuthor = true
                    authorPage = 1
                    hasMoreAuthorVideos = true
                    selectedAuthor = author
                    authorVideos = emptyList()

                    isAuthorVisible = true
                    overlayOrder = overlayOrder.filter { it != OverlayState.AUTHOR } + OverlayState.AUTHOR
                    if (playerState == PlayerState.FULL) playerState = PlayerState.MINI
                }
                try {
                    val resp = if (author.id != null) {
                        withContext(Dispatchers.IO) { RetrofitClient.api.getAuthorVideos(author.id.toString(), ordering = authorSortOrder, page = authorPage) }
                    } else {
                        withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, page = authorPage) }
                    }

                    if (resp.results.isEmpty()) {
                        hasMoreAuthorVideos = false
                    } else {
                        val filteredNewVideos = filterHiddenAndDisliked(resp.results)
                        val combined = if (isLoadMore) (authorVideos + filteredNewVideos) else filteredNewVideos
                        authorVideos = combined.sortedWith(
                            compareByDescending<SearchResult> { it.publicationTs ?: "" }
                                .thenByDescending { it.createdTs ?: "" }
                        ).distinctBy { it.videoUrl }
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Author videos error", e)
                    if (isLoadMore) authorPage--
                } finally {
                    isRefreshingAuthor = false
                    isAuthorLoadingMore = false
                }
            }
        }
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

    // ── Initial load / auth ──
    fun initializeApp() {
        val savedToken = settingsManager.accessToken
        if (savedToken != null) {
            viewModelScope.launch {
                isAuthenticating = true
                try {
                    val authHeader = "Bearer $savedToken"
                    githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }

                    val now = System.currentTimeMillis()
                    val lastSync = settingsManager.lastSyncTime
                    val freqMs = settingsManager.syncFrequencyHours * 3600000L
                    if (now - lastSync > freqMs || userRegistry.watchHistory.isEmpty()) {
                        userRegistry = withContext(Dispatchers.IO) { syncManager.sync(savedToken) }
                        settingsManager.lastSyncTime = now
                    } else {
                        userRegistry = registryManager.registry
                    }
                    isAuthenticated = true
                } catch (e: Exception) {
                    Log.e("RupoopAuth", "Auth error", e)
                } finally {
                    isAuthenticating = false
                }
            }
        }
    }

    // ── Auth result ──
    fun processAuthResponse(response: net.openid.appauth.AuthorizationResponse) {
        viewModelScope.launch {
            isAuthenticating = true
            try {
                val token = authManager.exchangeCodeForToken(response)
                settingsManager.accessToken = token
                val authHeader = "Bearer $token"
                githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }
                userRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                isAuthenticated = true
            } catch (e: Exception) {
                Log.e("RupoopAuth", "Auth processes error", e)
                _snackbarMessage.emit("Ошибка авторизации: ${e.localizedMessage}")
            } finally {
                isAuthenticating = false
                loadHome(false)
            }
        }
    }

    fun onAuthSuccess(token: String) {
        viewModelScope.launch {
            isAuthenticating = true
            try {
                settingsManager.accessToken = token
                val authHeader = "Bearer $token"
                githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }
                userRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                isAuthenticated = true
            } catch (e: Exception) {
                Log.e("RupoopAuth", "Sync error", e)
            } finally {
                isAuthenticating = false
                loadHome(false)
            }
        }
    }

    fun logout() {
        settingsManager.clearAuth()
        isAuthenticated = false
        githubUser = null
    }

    // ── Search ──
    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchSuggestions = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.suggestApi.getSuggestions(query) }
                val jsonString = response.body()?.string()
                if (jsonString != null) {
                    val jsonArray = RetrofitClient.json.parseToJsonElement(jsonString) as kotlinx.serialization.json.JsonArray
                    if (jsonArray.size > 1 && jsonArray[1] is kotlinx.serialization.json.JsonArray) {
                        val suggestionsArray = jsonArray[1] as kotlinx.serialization.json.JsonArray
                        searchSuggestions = suggestionsArray.map { it.toString().removeSurrounding("\"") }
                    }
                }
            } catch (e: Exception) {
                Log.e("Rupoop", "Search suggest auto-complete error", e)
            }
        }
    }

    fun performSearch(query: String, ordering: String? = searchSortOrder) {
        searchQuery = query
        isSearchExpanded = false

        isSearchVisible = true
        overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH } + OverlayState.SEARCH
        if (playerState == PlayerState.FULL) playerState = PlayerState.MINI

        registryManager.addSearchQuery(query)
        userRegistry = registryManager.registry
        pushToGitHub()
        val requestNav = currentNav
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query, ordering = ordering) }
                val filteredResults = filterHiddenAndDisliked(resp.results)
                val currentStack = searchStacks[requestNav] ?: mutableListOf()
                currentStack.add(SearchState(query, filteredResults, ordering))
                searchStacks[requestNav] = currentStack

                if (currentNav == requestNav) {
                    searchResults = filteredResults
                }
            } catch (e: Exception) {
                Log.e("Rupoop", "Search error", e)
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
        // Orientation is handled in UI layer based on this state
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

    // ── Back navigation ──
    fun handleBack(): Boolean {
        if (isFullscreenVideo) { toggleFullscreen(false); return true }
        if (playerState == PlayerState.FULL) { playerState = PlayerState.MINI; return true }
        if (isNotificationSettingsVisible) { isNotificationSettingsVisible = false; return true }
        if (isHiddenVideosVisible) { isHiddenVideosVisible = false; return true }
        if (isSettingsVisible) { isSettingsVisible = false; return true }
        if (isSearchExpanded) {
            val currentStack = searchStacks[currentNav] ?: mutableListOf()
            if (currentStack.isNotEmpty()) {
                isSearchExpanded = false
                return true
            }
        }

        val topVisible = overlayOrder.lastOrNull {
            (it == OverlayState.SEARCH && isSearchVisible) ||
            (it == OverlayState.AUTHOR && isAuthorVisible)
        }

        if (topVisible == OverlayState.SEARCH) {
            val currentStack = searchStacks[currentNav] ?: mutableListOf()
            if (currentStack.isNotEmpty()) {
                currentStack.removeAt(currentStack.size - 1)
            }
            if (currentStack.isNotEmpty()) {
                val previous = currentStack.last()
                searchQuery = previous.query
                searchResults = previous.results
                searchSortOrder = previous.ordering
                isSearchExpanded = false
                isSearchVisible = true
            } else {
                isSearchVisible = false
                searchQuery = ""
                searchResults = emptyList()
                overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
            }
            return true
        }
        
        if (isSearchExpanded) {
            isSearchExpanded = false
            return true
        }

        if (topVisible == OverlayState.AUTHOR) { isAuthorVisible = false; return true }
        if (currentLibSub != LibrarySubScreen.NONE) { currentLibSub = LibrarySubScreen.NONE; return true }
        if (currentNav != NavItem.HOME) { currentNav = NavItem.HOME; searchQuery = ""; return true }

        playerState = PlayerState.CLOSED
        exoPlayer.stop()
        pushToGitHub()
        return true
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
    // ── Close player ──
    fun closePlayer() {
        playerState = PlayerState.CLOSED
        exoPlayer.stop()
        syncPlaybackService()
        pushToGitHub()
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
        exoPlayer.release()
        sharedPlayer = null
        if (instance == this) {
            instance = null
        }
        progressSavingJob?.cancel()
    }

    companion object {
        var sharedPlayer: ExoPlayer? = null
        var instance: AppViewModel? = null
    }
}
