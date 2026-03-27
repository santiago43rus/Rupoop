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
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.service.DownloadService
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val settingsManager = SettingsManager(context)
    val registryManager = UserRegistryManager(context)
    val recommendationEngine = RecommendationEngine(registryManager)
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
    var currentNav by mutableStateOf(NavItem.HOME)
    var currentLibSub by mutableStateOf(LibrarySubScreen.NONE)
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
    var selectedAuthor by mutableStateOf<Author?>(null)

    // ── Videos data ──
    var homeVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var subscriptionVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var authorVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var relatedVideos by mutableStateOf<List<SearchResult>>(emptyList())

    // ── Playback queue ──
    var currentVideoList by mutableStateOf<List<SearchResult>>(emptyList())
    var currentVideoIndex by mutableIntStateOf(-1)

    // ── Player state ──
    var playerState by mutableStateOf(PlayerState.CLOSED)
    var currentVideo by mutableStateOf<SearchResult?>(null)
    var isFullscreenVideo by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)

    // ── Search ──
    var searchQuery by mutableStateOf("")
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var isSearchExpanded by mutableStateOf(false)
    var searchSortOrder by mutableStateOf<String?>(null) // null = default, "-created_ts" = newest
    var authorSortOrder by mutableStateOf("-created_ts") // default newest

    // ── Overlays ──
    var isSearchVisible by mutableStateOf(false)
    var isAuthorVisible by mutableStateOf(false)
    var isSettingsVisible by mutableStateOf(false)
    var overlayOrder by mutableStateOf(listOf(OverlayState.SEARCH, OverlayState.AUTHOR))

    // ── Dialogs ──
    var showPlaylistDialog by mutableStateOf<SearchResult?>(null)
    var showOnboarding by mutableStateOf(settingsManager.isFirstLaunch)

    // ── Registry snapshot (observable) ──
    var userRegistry by mutableStateOf(registryManager.registry)

    // ── Pagination: Home ──
    var isHomeLoadingMore by mutableStateOf(false)
    var isRefreshingHome by mutableStateOf(false)

    // ── Pagination: Author ──
    var isAuthorLoadingMore by mutableStateOf(false)
    var isRefreshingAuthor by mutableStateOf(false)
    private var authorPage by mutableIntStateOf(1)
    var hasMoreAuthorVideos by mutableStateOf(true)

    // ── Pagination: Subscriptions ──
    private var subsPage by mutableIntStateOf(1)
    var hasMoreSubsVideos by mutableStateOf(true)
    var isSubsLoadingMore by mutableStateOf(false)
    var isRefreshingSubs by mutableStateOf(false)

    // ── ExoPlayer ──
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().also { player ->
        player.playWhenReady = true
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { this@AppViewModel.isPlaying = playing }
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
                theme = if (settingsManager.isDarkTheme) "dark" else "light",
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
    fun playVideo(video: SearchResult, list: List<SearchResult>?) {
        videoLoadingJob?.cancel()

        val isExplicitPlaylist = currentLibSub != LibrarySubScreen.NONE

        val existingIndex = currentVideoList.indexOfFirst { it.videoUrl == video.videoUrl }
        if (existingIndex != -1 && (list == relatedVideos || list == null)) {
            currentVideoIndex = existingIndex
        } else {
            val effectiveList = list ?: listOf(video)
            currentVideoList = effectiveList
            currentVideoIndex = effectiveList.indexOfFirst { it.videoUrl == video.videoUrl }
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
                    val optionsDeferred = async(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    val relatedDeferred = async(Dispatchers.IO) {
                        val queries = recommendationEngine.getSearchQueries(video)
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

                    val opt = optionsDeferred.await()
                    opt.videoBalancer?.m3u8?.let { url ->
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        exoPlayer.setMediaItem(MediaItem.fromUri(url))
                        exoPlayer.prepare()
                        if ((historyItem?.progress ?: 0) > 0) {
                            exoPlayer.seekTo(historyItem!!.progress)
                        }
                        exoPlayer.play()
                        playerState = PlayerState.FULL
                    }

                    val relatedResults = relatedDeferred.await()
                    val filteredRelated = recommendationEngine.recommendRelated(video, relatedResults)
                    relatedVideos = filteredRelated

                    if (!isExplicitPlaylist && (list == null || list == homeVideos || list == searchResults)) {
                        currentVideoList = listOf(video) + filteredRelated
                        currentVideoIndex = 0
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Play error", e)
                }
            }
        }
    }

    fun playNext() {
        if (currentVideoIndex < currentVideoList.size - 1) {
            playVideo(currentVideoList[currentVideoIndex + 1], currentVideoList)
        }
    }

    fun playPrevious() {
        if (currentVideoIndex > 0) {
            playVideo(currentVideoList[currentVideoIndex - 1], currentVideoList)
        }
    }

    // ── Play local file ──
    fun playLocalFile(filePath: String, title: String) {
        videoLoadingJob?.cancel()
        val file = java.io.File(filePath)
        if (!file.exists()) return
        currentVideo = SearchResult(videoUrl = filePath, title = title)
        currentVideoList = listOf(currentVideo!!)
        currentVideoIndex = 0
        relatedVideos = emptyList()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        exoPlayer.prepare()
        exoPlayer.play()
        playerState = PlayerState.FULL
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
                val queries = mutableListOf<String>()
                // Use enabled genres for home feed
                val genres = settingsManager.enabledGenres
                val filmGenres = listOf("боевик", "комедия", "драма", "ужасы", "фантастика", "триллер", "детектив", "мелодрама")

                genres.forEach { genre ->
                    if (filmGenres.any { it.equals(genre, ignoreCase = true) }) {
                        queries.add("$genre фильм")
                    } else {
                        queries.add(genre)
                    }
                }
                if (queries.isEmpty()) queries.add("популярное")

                val query = queries.random()
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query, page = 1) }
                val newVideos = recommendationEngine.recommend(resp.results)

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
                        subscriptionVideos = if (isLoadMore) {
                            (subscriptionVideos + sortedNewVideos).distinctBy { it.videoUrl }
                        } else {
                            sortedNewVideos.distinctBy { it.videoUrl }
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
                        withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, ordering = authorSortOrder, page = authorPage) }
                    }

                    if (resp.results.isEmpty()) {
                        hasMoreAuthorVideos = false
                    } else {
                        val newVideos = resp.results
                        authorVideos = if (isLoadMore) (authorVideos + newVideos).distinctBy { it.videoUrl } else newVideos
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
    fun startDownload(video: SearchResult) {
        viewModelScope.launch {
            if (!isNetworkAvailable(context)) {
                _snackbarMessage.emit("Нет подключения к интернету")
                return@launch
            }
            _snackbarMessage.emit("Скачивание начато: ${video.title}")
            extractId(video.videoUrl)?.let { id ->
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    opt.videoBalancer?.m3u8?.let { m3u8Url ->
                        val serviceIntent = Intent(context, DownloadService::class.java).apply {
                            putExtra("VIDEO_URL", m3u8Url)
                            putExtra("TITLE", video.title)
                            putExtra("VIDEO_ID", id)
                            putExtra("THUMBNAIL_URL", video.thumbnailUrl)
                            putExtra("QUALITY", settingsManager.downloadQuality)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                        else context.startService(serviceIntent)
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
                _snackbarMessage.emit("Синхронизация завершена")
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
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query, ordering = ordering) }
                searchResults = resp.results
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

    // ── Watch Later ──
    fun toggleWatchLater(video: SearchResult) {
        val added = registryManager.toggleWatchLater(video)
        userRegistry = registryManager.registry
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit(if (added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже")
        }
    }

    // ── Playlist ──
    fun addToPlaylist(name: String, video: SearchResult) {
        registryManager.addToPlaylist(name, video)
        userRegistry = registryManager.registry
        showPlaylistDialog = null
        pushToGitHub()
        viewModelScope.launch {
            _snackbarMessage.emit("Добавлено в $name")
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
        if (isSettingsVisible) { isSettingsVisible = false; return true }
        if (isSearchExpanded) { isSearchExpanded = false; return true }

        val topVisible = overlayOrder.lastOrNull {
            (it == OverlayState.SEARCH && isSearchVisible) ||
            (it == OverlayState.AUTHOR && isAuthorVisible)
        }

        if (topVisible == OverlayState.SEARCH) { isSearchVisible = false; searchQuery = ""; return true }
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
            "later" -> toggleWatchLater(video)
            "playlist" -> showPlaylistDialog = video
            "share" -> shareVideo(video)
            "download" -> startDownload(video)
        }
    }

    // ── Close player ──
    fun closePlayer() {
        playerState = PlayerState.CLOSED
        exoPlayer.stop()
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
        progressSavingJob?.cancel()
    }
}
