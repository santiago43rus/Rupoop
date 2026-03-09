package com.example.rupoop

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import net.openid.appauth.AuthorizationResponse
import java.util.Locale

enum class PlayerState { CLOSED, MINI, FULL }
enum class NavItem { HOME, SUBSCRIPTIONS, LIBRARY, SETTINGS }
enum class LibrarySubScreen { NONE, LIKED, WATCH_LATER, PLAYLISTS, PLAYLIST_DETAIL, HISTORY, AUTHOR }

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var registryManager: UserRegistryManager
    private lateinit var recommendationEngine: RecommendationEngine
    private var deepLinkVideoUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        registryManager = UserRegistryManager(this)
        recommendationEngine = RecommendationEngine(registryManager)
        
        handleIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(settingsManager.isDarkTheme) }

            SideEffect {
                val window = (this as Activity).window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }

            val colorScheme = if (isDarkTheme) darkColorScheme(
                background = Color(0xFF0F0F0F),
                surface = Color(0xFF212121),
                onBackground = Color.White,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF272727),
                onSurfaceVariant = Color.White
            ) else lightColorScheme(
                background = Color.White,
                surface = Color(0xFFF2F2F2),
                onBackground = Color.Black,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF2F2F2),
                onSurfaceVariant = Color.Black
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RutubeApp(
                        settingsManager = settingsManager,
                        registryManager = registryManager,
                        recommendationEngine = recommendationEngine,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            settingsManager.isDarkTheme = isDarkTheme
                        },
                        deepLinkVideoUrl = deepLinkVideoUrl,
                        onDeepLinkConsumed = { deepLinkVideoUrl = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data != null && data.host == "rutube.ru" && data.path?.startsWith("/video/") == true) {
                deepLinkVideoUrl = data.toString()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubeApp(
    settingsManager: SettingsManager,
    registryManager: UserRegistryManager,
    recommendationEngine: RecommendationEngine,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    deepLinkVideoUrl: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val authManager = remember { GitHubAuthManager(context) }
    val syncManager = remember { GistSyncManager(RetrofitClient.gistApi, registryManager, settingsManager) }
    
    var isAuthenticating by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(settingsManager.accessToken != null) }
    var githubUser by remember { mutableStateOf<GitHubUser?>(null) }
    var userRegistry by remember { mutableStateOf(registryManager.registry) }
    var isAccountMenuExpanded by remember { mutableStateOf(false) }

    var currentNav by remember { mutableStateOf(NavItem.HOME) }
    var previousNav by remember { mutableStateOf(NavItem.HOME) }
    var currentLibSub by remember { mutableStateOf(LibrarySubScreen.NONE) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedAuthor by remember { mutableStateOf<Author?>(null) }
    var authorVideos by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    
    var searchQuery by remember { mutableStateOf("") }
    var homeVideos by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var subscriptionVideos by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var relatedVideos by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var playerState by remember { mutableStateOf(PlayerState.CLOSED) }
    var currentVideo by remember { mutableStateOf<SearchResult?>(null) }
    var isFullscreenVideo by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isInSearchMode by remember { mutableStateOf(false) }
    
    var showPlaylistDialog by remember { mutableStateOf<SearchResult?>(null) }

    val pushToGitHub = {
        val token = settingsManager.accessToken
        if (token != null) {
            scope.launch {
                syncManager.push(token, registryManager.registry)
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && currentVideo != null) {
                        registryManager.updateWatchProgress(extractId(currentVideo!!.videoUrl), contentPosition, duration)
                        userRegistry = registryManager.registry
                    }
                    if (state == Player.STATE_ENDED && currentVideo != null) {
                        registryManager.updateWatchProgress(extractId(currentVideo!!.videoUrl), duration, duration)
                        userRegistry = registryManager.registry
                        pushToGitHub()
                    }
                }
            })
        }
    }

    // Periodic progress saving & syncing
    LaunchedEffect(isPlaying, currentVideo) {
        if (isPlaying && currentVideo != null) {
            while (true) {
                delay(15000)
                extractId(currentVideo!!.videoUrl).let { id ->
                    registryManager.updateWatchProgress(id, exoPlayer.currentPosition, exoPlayer.duration)
                    userRegistry = registryManager.registry
                    pushToGitHub()
                }
            }
        }
    }

    var videoLoadingJob by remember { mutableStateOf<Job?>(null) }

    val playVideo: (SearchResult) -> Unit = { video ->
        videoLoadingJob?.cancel()
        focusManager.clearFocus()
        currentVideo = video
        extractRutubeId(video.videoUrl)?.let { id ->
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

            videoLoadingJob = scope.launch {
                try {
                    // Start fetching options and related videos in parallel
                    val optionsDeferred = async(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    val relatedDeferred = async(Dispatchers.IO) { 
                        val queries = recommendationEngine.getSearchQueries(video)
                        val allResults = mutableListOf<SearchResult>()
                        // Проходимся по сгенерированным запросам (След. серия -> След. сезон -> База)
                        for (q in queries) {
                            try {
                                val res = RetrofitClient.api.searchVideos(q).results
                                allResults.addAll(res)
                                if (allResults.size > 20) break // Чтобы не грузить сеть
                            } catch (e: Exception) {}
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
                    relatedVideos = recommendationEngine.recommendRelated(video, relatedResults)
                } catch (e: Exception) {
                    Log.e("Rupoop", "Play error", e)
                }
            }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val response = AuthorizationResponse.fromIntent(data)
                if (response != null) {
                    scope.launch {
                        isAuthenticating = true
                        try {
                            val token = authManager.exchangeCodeForToken(response)
                            settingsManager.accessToken = token
                            val authHeader = "Bearer $token"
                            githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }
                            userRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                            isAuthenticated = true
                        } catch (e: Exception) {
                            Log.e("RupoopAuth", "Sync error", e)
                        } finally {
                            isAuthenticating = false
                        }
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    var isRefreshingHome by remember { mutableStateOf(false) }
    var isRefreshingSubs by remember { mutableStateOf(false) }
    var isRefreshingAuthor by remember { mutableStateOf(false) }

    val loadHome = {
        scope.launch {
            isRefreshingHome = true
            try {
                val queries = mutableListOf<String>()
                if (settingsManager.adultContentEnabled) {
                    queries.addAll(listOf("фильмы новинки", "сериалы зарубежные", "боевики", "комедии", "фантастика фильм"))
                }
                if (settingsManager.kidsContentEnabled) {
                    queries.addAll(listOf("полнометражные мультфильмы", "мультсериалы", "disney мультик", "пиксар"))
                }
                if (queries.isEmpty()) queries.add("популярное")

                val query = queries.random()
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query) }
                // Пропускаем через движок рекомендаций для лучшей сортировки
                homeVideos = recommendationEngine.recommend(resp.results)
            } catch (e: Exception) {
                Log.e("Rupoop", "Error loading home", e)
            } finally {
                isRefreshingHome = false
            }
        }
    }

    val loadSubscriptions = {
        scope.launch {
            isRefreshingSubs = true
            if (userRegistry.subscriptions.isNotEmpty()) {
                try {
                    val allSubsVideos = mutableListOf<SearchResult>()
                    userRegistry.subscriptions.forEach { author ->
                        val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, ordering = "-created_ts") }
                        allSubsVideos.addAll(resp.results.filter { it.author?.name == author.name })
                    }
                    subscriptionVideos = allSubsVideos.distinctBy { it.videoUrl }.sortedByDescending { it.createdTs }
                } catch (e: Exception) {}
            } else {
                subscriptionVideos = emptyList()
            }
            isRefreshingSubs = false
        }
    }

    val loadAuthorVideos = { author: Author ->
        scope.launch {
            isRefreshingAuthor = true
            selectedAuthor = author
            if (currentNav != NavItem.LIBRARY) previousNav = currentNav
            currentNav = NavItem.LIBRARY
            currentLibSub = LibrarySubScreen.AUTHOR
            try {
                val resp = if (author.id != null) {
                    withContext(Dispatchers.IO) { RetrofitClient.api.getAuthorVideos(author.id.toString()) }
                } else {
                    withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, ordering = "-created_ts") }
                }
                authorVideos = resp.results.filter { it.author?.name == author.name }.sortedByDescending { it.createdTs }
            } catch (e: Exception) {}
            isRefreshingAuthor = false
        }
    }

    val startDownload = { video: SearchResult ->
        scope.launch {
            snackbarHostState.showSnackbar("Скачивание начато: ${video.title}")
            extractRutubeId(video.videoUrl)?.let { id ->
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    opt.videoBalancer?.m3u8?.let { m3u8Url ->
                        val serviceIntent = Intent(context, DownloadService::class.java).apply {
                            putExtra("VIDEO_URL", m3u8Url)
                            putExtra("TITLE", video.title)
                            putExtra("QUALITY", settingsManager.downloadQuality)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                        else context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("RupoopDownload", "Error starting download", e)
                }
            }
        }
    }

    val syncWithGitHub = {
        val token = settingsManager.accessToken
        if (token != null) {
            scope.launch {
                try {
                    userRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                    settingsManager.lastSyncTime = System.currentTimeMillis()
                    snackbarHostState.showSnackbar("Синхронизация завершена")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Ошибка синхронизации")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedToken = settingsManager.accessToken
        if (savedToken != null) {
            scope.launch {
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
                    loadHome()
                }
            }
        } else {
            loadHome()
        }
    }
    
    LaunchedEffect(currentNav) {
        if (currentNav == NavItem.SUBSCRIPTIONS) loadSubscriptions()
        if (currentNav == NavItem.HOME && homeVideos.isEmpty()) loadHome()
    }

    val shareVideo = { video: SearchResult ->
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${video.title}\n${video.videoUrl}")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    val performSearch = { query: String ->
        searchQuery = query
        isSearchExpanded = false
        isInSearchMode = true
        focusManager.clearFocus()
        registryManager.addSearchQuery(query)
        userRegistry = registryManager.registry
        pushToGitHub()
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query) }
                searchResults = resp.results
            } catch (e: Exception) {}
        }
    }

    val toggleFullscreen = { fill: Boolean ->
        isFullscreenVideo = fill
        val activity = context.findActivity()
        if (fill) {
            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            activity?.let { hideSystemBars(it) }
        } else {
            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            activity?.let { showSystemBars(it) }
        }
    }

    BackHandler(enabled = playerState != PlayerState.CLOSED || isInSearchMode || isSearchExpanded || currentLibSub != LibrarySubScreen.NONE || currentNav != NavItem.HOME) {
        if (isFullscreenVideo) toggleFullscreen(false)
        else if (playerState == PlayerState.FULL) playerState = PlayerState.MINI
        else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        }
        else if (currentLibSub != LibrarySubScreen.NONE) {
            if (currentLibSub == LibrarySubScreen.AUTHOR && currentNav == NavItem.LIBRARY && previousNav != NavItem.LIBRARY) {
                currentNav = previousNav
            }
            currentLibSub = LibrarySubScreen.NONE
        }
        else if (isInSearchMode) { 
            isInSearchMode = false
            searchQuery = ""
        }
        else if (currentNav != NavItem.HOME) { 
            currentNav = NavItem.HOME
        }
        else { 
            playerState = PlayerState.CLOSED
            exoPlayer.stop() 
            pushToGitHub()
        }
    }

    var showOnboarding by remember { mutableStateOf(settingsManager.isFirstLaunch) }
    if (showOnboarding) {
        ContentSelectionDialog(
            settingsManager = settingsManager,
            onDismiss = { 
                showOnboarding = false
                loadHome() // Обновляем ленту с новыми фильтрами
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (playerState != PlayerState.FULL && !isFullscreenVideo) {
                TopAppBar(
                    title = {
                        if (!isSearchExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isInSearchMode || currentLibSub != LibrarySubScreen.NONE || currentNav != NavItem.HOME) {
                                    IconButton(onClick = { 
                                        if (isInSearchMode) { isInSearchMode = false; searchQuery = "" }
                                        else if (currentLibSub != LibrarySubScreen.NONE) {
                                            if (currentLibSub == LibrarySubScreen.AUTHOR && currentNav == NavItem.LIBRARY && previousNav != NavItem.LIBRARY) {
                                                currentNav = previousNav
                                            }
                                            currentLibSub = LibrarySubScreen.NONE
                                        }
                                        else currentNav = NavItem.HOME
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                    }
                                }
                                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(when(currentLibSub) {
                                    LibrarySubScreen.LIKED -> "Понравившиеся"
                                    LibrarySubScreen.WATCH_LATER -> "Смотреть позже"
                                    LibrarySubScreen.PLAYLISTS -> "Плейлисты"
                                    LibrarySubScreen.PLAYLIST_DETAIL -> selectedPlaylist?.name ?: "Плейлист"
                                    LibrarySubScreen.HISTORY -> "История просмотра"
                                    LibrarySubScreen.AUTHOR -> selectedAuthor?.name ?: "Канал"
                                    else -> when(currentNav) {
                                        NavItem.SUBSCRIPTIONS -> "Подписки"
                                        NavItem.LIBRARY -> "Библиотека"
                                        NavItem.SETTINGS -> "Настройки"
                                        else -> "Rupoop"
                                    }
                                }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Поиск") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                            )
                        }
                    },
                    actions = {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = { currentNav = NavItem.SETTINGS }) { Icon(Icons.Default.Settings, null) }
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(onClick = { 
                                    if (!isAuthenticated && !isAuthenticating) {
                                        authLauncher.launch(authManager.createAuthIntent())
                                    }
                                    else if (isAuthenticated) isAccountMenuExpanded = true
                                }) {
                                    if (isAuthenticating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    else Icon(if (isAuthenticated) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle, null, 
                                        tint = if (isAuthenticated) Color(0xFF4CAF50) else LocalContentColor.current)
                                }
                                if (isAuthenticated) {
                                    DropdownMenu(expanded = isAccountMenuExpanded, onDismissRequest = { isAccountMenuExpanded = false }) {
                                        githubUser?.let { user ->
                                            DropdownMenuItem(text = { Text(user.login, fontWeight = FontWeight.Bold) }, onClick = {}, enabled = false)
                                            HorizontalDivider()
                                        }
                                        DropdownMenuItem(text = { Text("Синхронизировать") }, onClick = { isAccountMenuExpanded = false; syncWithGitHub() })
                                        DropdownMenuItem(text = { Text("Выйти") }, onClick = { isAccountMenuExpanded = false; settingsManager.clearAuth(); isAuthenticated = false; githubUser = null })
                                    }
                                }
                            }
                        } else {
                            IconButton(onClick = { isSearchExpanded = false; searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (playerState != PlayerState.FULL && !isFullscreenVideo) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(selected = currentNav == NavItem.HOME, onClick = { currentNav = NavItem.HOME; currentLibSub = LibrarySubScreen.NONE; isInSearchMode = false; searchQuery = "" }, icon = { Icon(if(currentNav == NavItem.HOME) Icons.Filled.Home else Icons.Outlined.Home, "Home") }, label = { Text("Главная") })
                    NavigationBarItem(selected = currentNav == NavItem.SUBSCRIPTIONS, onClick = { currentNav = NavItem.SUBSCRIPTIONS; currentLibSub = LibrarySubScreen.NONE }, icon = { Icon(if(currentNav == NavItem.SUBSCRIPTIONS) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Subs") }, label = { Text("Подписки") })
                    NavigationBarItem(selected = currentNav == NavItem.LIBRARY, onClick = { currentNav = NavItem.LIBRARY; currentLibSub = LibrarySubScreen.NONE }, icon = { Icon(if(currentNav == NavItem.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, "Lib") }, label = { Text("Библиотека") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentNav) {
                NavItem.HOME -> {
                    PullToRefreshBox(isRefreshing = isRefreshingHome, onRefresh = { loadHome() }, state = rememberPullToRefreshState()) {
                        val videos = if (isInSearchMode) searchResults else homeVideos
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(videos) { video ->
                                val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                VideoItem(video, history, 
                                    onClick = { playVideo(video) }, 
                                    onAuthorClick = { loadAuthorVideos(it) },
                                    onMoreClick = { action ->
                                        when(action) {
                                            "later" -> { val added = registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub(); scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже") } }
                                            "playlist" -> { showPlaylistDialog = video }
                                            "share" -> shareVideo(video)
                                            "download" -> startDownload(video)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                NavItem.SUBSCRIPTIONS -> {
                    PullToRefreshBox(isRefreshing = isRefreshingSubs, onRefresh = { loadSubscriptions() }, state = rememberPullToRefreshState()) {
                        if (userRegistry.subscriptions.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("У вас пока нет подписок") }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                        items(userRegistry.subscriptions) { author ->
                                            Column(Modifier.padding(end = 16.dp).width(70.dp).clickable { loadAuthorVideos(author) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                                AsyncImage(model = author.avatarUrl ?: "", contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray))
                                                Text(author.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                                items(subscriptionVideos) { video ->
                                    val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                    VideoItem(video, history, 
                                        onClick = { playVideo(video) }, 
                                        onAuthorClick = { loadAuthorVideos(it) },
                                        onMoreClick = { action ->
                                            when(action) {
                                                "later" -> { val added = registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub(); scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже") } }
                                                "playlist" -> { showPlaylistDialog = video }
                                                "share" -> shareVideo(video)
                                                "download" -> startDownload(video)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                NavItem.LIBRARY -> {
                    when (currentLibSub) {
                        LibrarySubScreen.NONE -> {
                            LibraryScreen(userRegistry, 
                                onVideoClick = { item -> playVideo(SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))) },
                                onAuthorClick = { loadAuthorVideos(it) },
                                onMoreClick = { item, action ->
                                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))
                                    when(action) {
                                        "remove" -> { registryManager.removeFromHistory(item.videoId); userRegistry = registryManager.registry; pushToGitHub() }
                                        "share" -> shareVideo(video)
                                        "download" -> startDownload(video)
                                        "later" -> { registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub() }
                                        "playlist" -> showPlaylistDialog = video
                                    }
                                },
                                onActionClick = { action ->
                                    when(action) {
                                        "liked" -> currentLibSub = LibrarySubScreen.LIKED
                                        "later" -> currentLibSub = LibrarySubScreen.WATCH_LATER
                                        "playlists" -> currentLibSub = LibrarySubScreen.PLAYLISTS
                                        "history" -> currentLibSub = LibrarySubScreen.HISTORY
                                    }
                                }
                            )
                        }
                        LibrarySubScreen.HISTORY -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(userRegistry.watchHistory) { item ->
                                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl), duration = (item.totalDuration / 1000).toInt())
                                    VideoItem(video, item, 
                                        onClick = { playVideo(video) }, 
                                        onAuthorClick = { loadAuthorVideos(it) },
                                        onMoreClick = { action ->
                                            if (action == "remove") { registryManager.removeFromHistory(item.videoId); userRegistry = registryManager.registry; pushToGitHub() }
                                            else if (action == "share") shareVideo(video)
                                            else if (action == "download") startDownload(video)
                                        }, 
                                        isEditMode = true
                                    )
                                }
                            }
                        }
                        LibrarySubScreen.LIKED -> { VideoListScreen(userRegistry.likedVideos, userRegistry, playVideo, { loadAuthorVideos(it) }, { shareVideo(it) }, { video -> registryManager.toggleLike(video); userRegistry = registryManager.registry; pushToGitHub() }, { startDownload(it) }) }
                        LibrarySubScreen.WATCH_LATER -> { VideoListScreen(userRegistry.watchLater, userRegistry, playVideo, { loadAuthorVideos(it) }, { shareVideo(it) }, { video -> registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub() }, { startDownload(it) }) }
                        LibrarySubScreen.PLAYLISTS -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(userRegistry.playlists) { playlist ->
                                    LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, playlist.name, playlist.videos.size.toString(), onAction = { registryManager.deletePlaylist(playlist.id); userRegistry = registryManager.registry; pushToGitHub() }) {
                                        selectedPlaylist = playlist
                                        currentLibSub = LibrarySubScreen.PLAYLIST_DETAIL
                                    }
                                }
                            }
                        }
                        LibrarySubScreen.PLAYLIST_DETAIL -> { VideoListScreen(selectedPlaylist?.videos ?: emptyList(), userRegistry, playVideo, { loadAuthorVideos(it) }, { shareVideo(it) }, { video -> selectedPlaylist?.let { registryManager.removeFromPlaylist(it.id, video.videoUrl); userRegistry = registryManager.registry; selectedPlaylist = userRegistry.playlists.find { p -> p.id == it.id }; pushToGitHub() } }, { startDownload(it) }) }
                        LibrarySubScreen.AUTHOR -> {
                            PullToRefreshBox(isRefreshing = isRefreshingAuthor, onRefresh = { selectedAuthor?.let { loadAuthorVideos(it) } }, state = rememberPullToRefreshState()) {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(authorVideos) { video ->
                                        val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                        VideoItem(video, history, 
                                            onClick = { playVideo(video) }, 
                                            onAuthorClick = { loadAuthorVideos(it) },
                                            onMoreClick = { action ->
                                                when(action) {
                                                    "later" -> { registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub() }
                                                    "playlist" -> { showPlaylistDialog = video }
                                                    "share" -> shareVideo(video)
                                                    "download" -> startDownload(video)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                NavItem.SETTINGS -> {
                    SettingsScreen(settingsManager, onThemeToggle, registryManager, onRegistryUpdate = { userRegistry = it; pushToGitHub() })
                }
            }

            if (isSearchExpanded && userRegistry.searchHistory.isNotEmpty()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn {
                        items(userRegistry.searchHistory) { query ->
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = Color.Gray)
                                Spacer(Modifier.width(16.dp))
                                Text(query, Modifier.weight(1f).clickable { performSearch(query) })
                                IconButton(onClick = { registryManager.removeSearchQuery(query); userRegistry = registryManager.registry; pushToGitHub() }) {
                                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (playerState != PlayerState.CLOSED) {
                val playerHeight by animateDpAsState(targetValue = if (playerState == PlayerState.FULL) config.screenHeightDp.dp else 64.dp, label = "")
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(playerHeight).background(MaterialTheme.colorScheme.background)) {
                    if (playerState == PlayerState.FULL) {
                        Column {
                            CustomVideoPlayer(exoPlayer, isPlaying, isFullscreenVideo, currentVideo, onMinimize = { playerState = PlayerState.MINI }, onToggleFullscreen = { toggleFullscreen(!isFullscreenVideo) })
                            if (!isFullscreenVideo) {
                                LazyColumn(Modifier.weight(1f)) {
                                    item {
                                        VideoDetails(currentVideo, userRegistry, 
                                            onAuthorClick = { loadAuthorVideos(it) },
                                            onToggleSub = { author -> 
                                                val subs = userRegistry.subscriptions.toMutableList()
                                                if (subs.any { it.name == author.name }) subs.removeAll { it.name == author.name }
                                                else subs.add(author)
                                                registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
                                                userRegistry = registryManager.registry
                                                pushToGitHub()
                                            },
                                            onLike = { currentVideo?.let { val added = registryManager.toggleLike(it); userRegistry = registryManager.registry; pushToGitHub(); scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Понравившиеся" else "Удалено из Понравившихся") } } },
                                            onShare = { currentVideo?.let { shareVideo(it) } },
                                            onAddToPlaylist = { showPlaylistDialog = currentVideo },
                                            onDownload = { currentVideo?.let { startDownload(it) } }
                                        )
                                        HorizontalDivider(); Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                                    }
                                    items(relatedVideos) { video ->
                                        val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                        VideoItem(video, history, 
                                            onClick = { playVideo(video) }, 
                                            onAuthorClick = { loadAuthorVideos(it) },
                                            onMoreClick = { action ->
                                                when(action) {
                                                    "later" -> { registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; pushToGitHub() }
                                                    "playlist" -> { showPlaylistDialog = video }
                                                    "share" -> shareVideo(video)
                                                    "download" -> startDownload(video)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        MiniPlayer(currentVideo, isPlaying, exoPlayer, onClose = { playerState = PlayerState.CLOSED; exoPlayer.stop(); pushToGitHub() }, onClick = { playerState = PlayerState.FULL })
                    }
                }
            }
            
            showPlaylistDialog?.let { video ->
                PlaylistSelectionDialog(
                    playlists = userRegistry.playlists,
                    onDismiss = { showPlaylistDialog = null },
                    onPlaylistSelected = { name -> 
                        registryManager.addToPlaylist(name, video)
                        userRegistry = registryManager.registry
                        showPlaylistDialog = null
                        pushToGitHub()
                        scope.launch { snackbarHostState.showSnackbar("Добавлено в $name") }
                    },
                    onCreateNew = { name ->
                        registryManager.addToPlaylist(name, video)
                        userRegistry = registryManager.registry
                        showPlaylistDialog = null
                        pushToGitHub()
                        scope.launch { snackbarHostState.showSnackbar("Плейлист $name создан") }
                    }
                )
            }
        }
    }
}

@Composable
fun ContentSelectionDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    var adultEnabled by remember { mutableStateOf(settingsManager.adultContentEnabled) }
    var kidsEnabled by remember { mutableStateOf(settingsManager.kidsContentEnabled) }

    AlertDialog(
        onDismissRequest = { /* Нельзя закрыть мимо кнопок */ },
        title = { Text("Что вам интересно?") },
        text = {
            Column {
                Text("Выберите, что вы планируете смотреть. Это поможет нам настроить рекомендации.", modifier = Modifier.padding(bottom = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { adultEnabled = !adultEnabled }) {
                    Checkbox(checked = adultEnabled, onCheckedChange = { adultEnabled = it })
                    Text("Фильмы и Сериалы")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { kidsEnabled = !kidsEnabled }) {
                    Checkbox(checked = kidsEnabled, onCheckedChange = { kidsEnabled = it })
                    Text("Мультфильмы и Детское")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Если сняли обе галочки, принудительно оставляем фильмы
                    if (!adultEnabled && !kidsEnabled) adultEnabled = true 
                    settingsManager.adultContentEnabled = adultEnabled
                    settingsManager.kidsContentEnabled = kidsEnabled
                    settingsManager.isFirstLaunch = false
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        }
    )
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, onThemeToggle: () -> Unit, registryManager: UserRegistryManager, onRegistryUpdate: (UserRegistry) -> Unit) {
    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var syncFreq by remember { mutableStateOf(settingsManager.syncFrequencyHours.toString()) }
    var adultEnabled by remember { mutableStateOf(settingsManager.adultContentEnabled) }
    var kidsEnabled by remember { mutableStateOf(settingsManager.kidsContentEnabled) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Внешний вид", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Темная тема") }, trailingContent = { Switch(checked = settingsManager.isDarkTheme, onCheckedChange = { onThemeToggle() }) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            
            Text("Рекомендации контента", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Фильмы и Сериалы") }, trailingContent = { Switch(checked = adultEnabled, onCheckedChange = { adultEnabled = it; settingsManager.adultContentEnabled = it }) })
            ListItem(headlineContent = { Text("Мультфильмы и Детское") }, trailingContent = { Switch(checked = kidsEnabled, onCheckedChange = { kidsEnabled = it; settingsManager.kidsContentEnabled = it }) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Загрузка", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            Text("Качество видео для скачивания", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                listOf("360", "480", "720", "1080").forEach { q ->
                    FilterChip(selected = downloadQuality == q, onClick = { downloadQuality = q; settingsManager.downloadQuality = q }, label = { Text(q + "p") })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("История и конфиденциальность", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            Button(onClick = { registryManager.clearWatchHistory(); onRegistryUpdate(registryManager.registry) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text("Очистить историю просмотра")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { registryManager.clearSearchHistory(); onRegistryUpdate(registryManager.registry) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text("Очистить историю поиска")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("GitHub Синхронизация", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Периодичность (в часах)") }, trailingContent = {
                TextField(value = syncFreq, onValueChange = { syncFreq = it; it.toIntOrNull()?.let { settingsManager.syncFrequencyHours = it } }, modifier = Modifier.width(60.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            })
        }
    }
}

@Composable
fun PlaylistSelectionDialog(playlists: List<Playlist>, onDismiss: () -> Unit, onPlaylistSelected: (String) -> Unit, onCreateNew: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if(isCreating) "Новый плейлист" else "Добавить в плейлист") },
        text = {
            if (isCreating) TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Название") })
            else {
                LazyColumn {
                    items(playlists) { p -> Row(Modifier.fillMaxWidth().clickable { onPlaylistSelected(p.name) }.padding(12.dp)) { Text(p.name) } }
                    item { TextButton(onClick = { isCreating = true }) { Text("+ Создать новый") } }
                }
            }
        },
        confirmButton = { if (isCreating) Button(onClick = { if(newName.isNotBlank()) onCreateNew(newName) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun VideoListScreen(videos: List<SearchResult>, registry: UserRegistry, onVideoClick: (SearchResult) -> Unit, onAuthorClick: (Author) -> Unit, onShare: (SearchResult) -> Unit, onRemove: (SearchResult) -> Unit, onDownload: (SearchResult) -> Unit) {
    if (videos.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Список пуст") }
    else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(videos) { video ->
                VideoItem(video, null, onClick = { onVideoClick(video) }, onAuthorClick = onAuthorClick, onMoreClick = { action ->
                    if (action == "remove") onRemove(video)
                    else if (action == "share") onShare(video)
                    else if (action == "download") onDownload(video)
                }, isEditMode = true)
            }
        }
    }
}

@Composable
fun VideoDetails(video: SearchResult?, registry: UserRegistry, onAuthorClick: (Author) -> Unit, onToggleSub: (Author) -> Unit, onLike: () -> Unit, onShare: () -> Unit, onAddToPlaylist: () -> Unit, onDownload: () -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text(video?.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = video?.author?.avatarUrl ?: "", contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray).clickable { video?.author?.let { onAuthorClick(it) } })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable { video?.author?.let { onAuthorClick(it) } }) {
                Text(video?.author?.name ?: "Автор", fontWeight = FontWeight.Bold)
                Text("Rutube", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            val isSubbed = registry.subscriptions.any { it.name == video?.author?.name }
            Button(onClick = { video?.author?.let { onToggleSub(it) } }, colors = ButtonDefaults.buttonColors(containerColor = if(isSubbed) Color.Gray else Color.Red)) {
                Text(if(isSubbed) "Вы подписаны" else "Подписаться")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            val isLiked = registry.likedVideos.any { it.videoUrl == video?.videoUrl }
            DetailAction(if(isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Лайк", color = if(isLiked) Color.Red else LocalContentColor.current, onClick = onLike)
            DetailAction(Icons.Outlined.Download, "Скачать", onClick = onDownload)
            DetailAction(Icons.Default.Share, "Поделиться", onClick = onShare)
            DetailAction(Icons.AutoMirrored.Filled.PlaylistAdd, "В плейлист", onClick = onAddToPlaylist)
        }
    }
}

@Composable
fun DetailAction(icon: ImageVector, label: String, color: Color = LocalContentColor.current, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = color); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LibraryScreen(registry: UserRegistry, onVideoClick: (WatchHistoryItem) -> Unit, onAuthorClick: (Author) -> Unit, onMoreClick: (WatchHistoryItem, String) -> Unit, onActionClick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LibraryHeader(Icons.Default.History, "История")
                Spacer(Modifier.weight(1f)); Text("Все", color = Color.Red, modifier = Modifier.clickable { onActionClick("history") })
            }
            LazyRow(Modifier.fillMaxWidth().height(160.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(registry.watchHistory.take(10)) { item ->
                    var showMenu by remember { mutableStateOf(false) }
                    Column(Modifier.width(160.dp).padding(end = 8.dp)) {
                        Box(Modifier.fillMaxWidth().height(90.dp).clickable { onVideoClick(item) }) {
                            AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            if (item.totalDuration > 0) {
                                LinearProgressIndicator(progress = { item.progress.toFloat() / item.totalDuration }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).padding(horizontal = 4.dp), color = Color.Red, trackColor = Color.Gray.copy(alpha = 0.5f))
                                Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp)) {
                                    Text(text = formatDuration(item.totalDuration / 1000), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = item.authorAvatarUrl ?: "https://rutube.ru/static/img/default-avatar.png", 
                                contentDescription = null, 
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray).clickable { 
                                    onAuthorClick(Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))
                                },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f).clickable { onVideoClick(item) }) {
                                Text(item.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(item.authorName ?: "", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color.Gray, overflow = TextOverflow.Ellipsis)
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Удалить из истории") }, onClick = { showMenu = false; onMoreClick(item, "remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { showMenu = false; onMoreClick(item, "later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                                    DropdownMenuItem(text = { Text("В плейлист") }, onClick = { showMenu = false; onMoreClick(item, "playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { showMenu = false; onMoreClick(item, "download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { showMenu = false; onMoreClick(item, "share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            LibraryRow(Icons.Default.ThumbUp, "Ваши лайки", registry.likedVideos.size.toString()) { onActionClick("liked") }
            LibraryRow(Icons.Default.Schedule, "Смотреть позже", registry.watchLater.size.toString()) { onActionClick("later") }
            LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Ваши плейлисты", registry.playlists.size.toString()) { onActionClick("playlists") }
        }
    }
}

@Composable
fun LibraryHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

@Composable
fun LibraryRow(icon: ImageVector, title: String, count: String, onAction: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f)); Text(count, color = Color.Gray)
        if (onAction != null) IconButton(onClick = onAction) { Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

@Composable
fun VideoItem(video: SearchResult, history: WatchHistoryItem?, onClick: () -> Unit, onAuthorClick: (Author) -> Unit, onMoreClick: (String) -> Unit, isEditMode: Boolean = false) {
    var showMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(bottom = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.DarkGray), contentScale = ContentScale.Crop)
            if (history != null && history.totalDuration > 0) LinearProgressIndicator(progress = { history.progress.toFloat() / history.totalDuration }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Color.Red, trackColor = Color.Gray.copy(alpha = 0.5f))
            video.duration?.let { durSeconds -> Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)) { Text(text = formatDuration(durSeconds.toLong()), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) } }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AsyncImage(model = video.author?.avatarUrl ?: "https://rutube.ru/static/img/default-avatar.png", contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray).clickable { video.author?.let { onAuthorClick(it) } }, contentScale = ContentScale.Crop)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
                Text("${video.author?.name ?: "Автор"} • Rutube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.clickable { video.author?.let { onAuthorClick(it) } })
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!isEditMode) {
                        DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { showMenu = false; onMoreClick("playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                        DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { showMenu = false; onMoreClick("later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                    } else DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onMoreClick("remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { showMenu = false; onMoreClick("download") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { showMenu = false; onMoreClick("share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String { val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60; return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s) }
fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }
fun setScreenOrientation(context: Context, orientation: Int) { context.findActivity()?.requestedOrientation = orientation }
fun hideSystemBars(activity: Activity) { WindowCompat.setDecorFitsSystemWindows(activity.window, false); WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
fun showSystemBars(activity: Activity) { WindowCompat.setDecorFitsSystemWindows(activity.window, true); WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
fun extractId(url: String): String = url.split("/").lastOrNull { it.isNotEmpty() } ?: ""
fun extractRutubeId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }
