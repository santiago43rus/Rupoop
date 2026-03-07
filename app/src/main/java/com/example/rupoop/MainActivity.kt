package com.example.rupoop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationResponse
import java.util.Locale

enum class PlayerState { CLOSED, MINI, FULL }
enum class NavItem { HOME, SUBSCRIPTIONS, LIBRARY }
enum class LibrarySubScreen { NONE, LIKED, WATCH_LATER, PLAYLISTS, PLAYLIST_DETAIL, HISTORY }

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
    val syncManager = remember { GistSyncManager(RetrofitClient.gistApi, registryManager) }
    
    var isAuthenticating by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(settingsManager.accessToken != null) }
    var githubUser by remember { mutableStateOf<GitHubUser?>(null) }
    var userRegistry by remember { mutableStateOf(registryManager.registry) }
    var isAccountMenuExpanded by remember { mutableStateOf(false) }

    var currentNav by remember { mutableStateOf(NavItem.HOME) }
    var currentLibSub by remember { mutableStateOf(LibrarySubScreen.NONE) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    val loadHome = {
        scope.launch {
            isRefreshing = true
            try {
                val movieQueries = listOf("фильмы 2024", "боевики", "комедии", "ужасы", "фантастика", "мультфильмы")
                val query = movieQueries.random()
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query) }
                searchResults = resp.results.shuffled()
            } catch (e: Exception) {
                Log.e("Rupoop", "Error loading home", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    val loadSubscriptions = {
        scope.launch {
            if (userRegistry.subscriptions.isNotEmpty()) {
                try {
                    val allSubsVideos = mutableListOf<SearchResult>()
                    userRegistry.subscriptions.forEach { author ->
                        val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name) }
                        // Filter strictly by author name
                        allSubsVideos.addAll(resp.results.filter { it.author?.name == author.name })
                    }
                    // Sort by "new" is hard with Rutube API as it doesn't provide dates in search reliably, 
                    // but we take all and unique them.
                    subscriptionVideos = allSubsVideos.distinctBy { it.videoUrl }
                } catch (e: Exception) {}
            } else {
                subscriptionVideos = emptyList()
            }
        }
    }

    val playVideo: (SearchResult) -> Unit = { video ->
        focusManager.clearFocus()
        currentVideo = video
        extractRutubeId(video.videoUrl)?.let { id ->
            val historyItem = userRegistry.watchHistory.find { it.videoId == id }
            
            registryManager.addWatchHistory(WatchHistoryItem(
                videoId = id,
                timestamp = System.currentTimeMillis(),
                progress = historyItem?.progress ?: 0,
                totalDuration = historyItem?.totalDuration ?: 0,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                authorName = video.author?.name,
                videoUrl = video.videoUrl
            ))
            userRegistry = registryManager.registry

            scope.launch {
                try {
                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                    opt.videoBalancer?.m3u8?.let { url ->
                        exoPlayer.setMediaItem(MediaItem.fromUri(url))
                        exoPlayer.prepare()
                        if ((historyItem?.progress ?: 0) > 0) {
                            exoPlayer.seekTo(historyItem!!.progress)
                        }
                        exoPlayer.play()
                        playerState = PlayerState.FULL
                        
                        val relatedResp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(video.title.take(10)) }
                        relatedVideos = relatedResp.results.filter { it.videoUrl != video.videoUrl }.shuffled()
                    }
                } catch (e: Exception) {}
            }
        }
    }

    LaunchedEffect(deepLinkVideoUrl) {
        deepLinkVideoUrl?.let { url ->
            playVideo(SearchResult(videoUrl = url, title = "Загрузка..."))
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(isPlaying, currentVideo) {
        if (isPlaying && currentVideo != null) {
            val videoId = extractRutubeId(currentVideo!!.videoUrl)
            if (videoId != null) {
                while (isPlaying) {
                    delay(5000)
                    registryManager.updateWatchProgress(videoId, exoPlayer.currentPosition, exoPlayer.duration)
                    userRegistry = registryManager.registry
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
                    userRegistry = withContext(Dispatchers.IO) { syncManager.sync(savedToken) }
                    isAuthenticated = true
                } catch (e: Exception) {
                    Log.e("RupoopAuth", "Auto-sync error", e)
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
        if (currentNav == NavItem.HOME && searchResults.isEmpty()) loadHome()
    }

    val shareVideo = { video: SearchResult ->
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${video.title}\n${video.videoUrl}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    val performSearch = { query: String ->
        searchQuery = query
        isSearchExpanded = false
        isInSearchMode = true
        focusManager.clearFocus()
        registryManager.addSearchQuery(query)
        userRegistry = registryManager.registry
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query) }
                searchResults = resp.results
            } catch (e: Exception) {}
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
        else if (isSearchExpanded) isSearchExpanded = false
        else if (currentLibSub != LibrarySubScreen.NONE) {
            currentLibSub = LibrarySubScreen.NONE
        }
        else if (isInSearchMode) { 
            isInSearchMode = false
            loadHome() 
        }
        else if (currentNav != NavItem.HOME) { 
            currentNav = NavItem.HOME
            currentLibSub = LibrarySubScreen.NONE 
        }
        else { 
            playerState = PlayerState.CLOSED
            exoPlayer.stop() 
        }
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
                                        if (isInSearchMode) { isInSearchMode = false; loadHome() }
                                        else if (currentLibSub != LibrarySubScreen.NONE) currentLibSub = LibrarySubScreen.NONE
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
                                    else -> if(currentNav == NavItem.SUBSCRIPTIONS) "Подписки" else if(currentNav == NavItem.LIBRARY) "Библиотека" else "Rupoop"
                                }, fontWeight = FontWeight.Bold)
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
                            IconButton(onClick = onThemeToggle) { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                            
                            // Account Icon Restored
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(onClick = { 
                                    if (!isAuthenticated && !isAuthenticating) authLauncher.launch(authManager.createAuthIntent())
                                    else if (isAuthenticated) isAccountMenuExpanded = true
                                }) {
                                    if (isAuthenticating) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(if (isAuthenticated) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle, null, 
                                            tint = if (isAuthenticated) Color(0xFF4CAF50) else LocalContentColor.current)
                                    }
                                }
                                if (isAuthenticated) {
                                    DropdownMenu(expanded = isAccountMenuExpanded, onDismissRequest = { isAccountMenuExpanded = false }) {
                                        githubUser?.let { user ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(user.login, fontWeight = FontWeight.Bold)
                                                        Text("GitHub Account", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                },
                                                onClick = {},
                                                enabled = false
                                            )
                                            HorizontalDivider()
                                        }
                                        DropdownMenuItem(text = { Text("Выйти") }, onClick = {
                                            isAccountMenuExpanded = false; settingsManager.clearAuth(); isAuthenticated = false; githubUser = null
                                        })
                                    }
                                }
                            }
                        } else {
                            IconButton(onClick = { isSearchExpanded = false }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (playerState != PlayerState.FULL && !isFullscreenVideo) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(
                        selected = currentNav == NavItem.HOME,
                        onClick = { currentNav = NavItem.HOME; currentLibSub = LibrarySubScreen.NONE },
                        icon = { Icon(if(currentNav == NavItem.HOME) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
                        label = { Text("Главная") }
                    )
                    NavigationBarItem(
                        selected = currentNav == NavItem.SUBSCRIPTIONS,
                        onClick = { currentNav = NavItem.SUBSCRIPTIONS; currentLibSub = LibrarySubScreen.NONE },
                        icon = { Icon(if(currentNav == NavItem.SUBSCRIPTIONS) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Subs") },
                        label = { Text("Подписки") }
                    )
                    NavigationBarItem(
                        selected = currentNav == NavItem.LIBRARY,
                        onClick = { currentNav = NavItem.LIBRARY; currentLibSub = LibrarySubScreen.NONE },
                        icon = { Icon(if(currentNav == NavItem.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, "Lib") },
                        label = { Text("Библиотека") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentNav) {
                NavItem.HOME -> {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { loadHome() }, state = rememberPullToRefreshState()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(searchResults) { video ->
                                val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                VideoItem(video, history, onClick = { playVideo(video) }, onMoreClick = { action ->
                                    when(action) {
                                        "later" -> { val added = registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже") } }
                                        "playlist" -> { showPlaylistDialog = video }
                                        "share" -> shareVideo(video)
                                    }
                                })
                            }
                        }
                    }
                }
                NavItem.SUBSCRIPTIONS -> {
                    if (userRegistry.subscriptions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("У вас пока нет подписок") }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    items(userRegistry.subscriptions) { author ->
                                        Column(Modifier.padding(end = 16.dp).width(70.dp).clickable { performSearch(author.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                            AsyncImage(model = author.avatarUrl ?: "", contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray))
                                            Text(author.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                            items(subscriptionVideos) { video ->
                                val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                VideoItem(video, history, onClick = { playVideo(video) }, onMoreClick = { action ->
                                    when(action) {
                                        "later" -> { val added = registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже") } }
                                        "playlist" -> { showPlaylistDialog = video }
                                        "share" -> shareVideo(video)
                                    }
                                })
                            }
                        }
                    }
                }
                NavItem.LIBRARY -> {
                    when (currentLibSub) {
                        LibrarySubScreen.NONE -> {
                            LibraryScreen(userRegistry, 
                                onVideoClick = { item -> playVideo(SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(name = item.authorName ?: ""))) },
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
                                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(name = item.authorName ?: ""))
                                    VideoItem(video, item, onClick = { playVideo(video) }, onMoreClick = { action ->
                                        if (action == "remove") { registryManager.removeFromHistory(item.videoId); userRegistry = registryManager.registry }
                                        else if (action == "share") shareVideo(video)
                                    }, isEditMode = true)
                                }
                            }
                        }
                        LibrarySubScreen.LIKED -> {
                            VideoListScreen(userRegistry.likedVideos, userRegistry, playVideo, { shareVideo(it) }, { video -> registryManager.toggleLike(video); userRegistry = registryManager.registry }, "liked")
                        }
                        LibrarySubScreen.WATCH_LATER -> {
                            VideoListScreen(userRegistry.watchLater, userRegistry, playVideo, { shareVideo(it) }, { video -> registryManager.toggleWatchLater(video); userRegistry = registryManager.registry }, "later")
                        }
                        LibrarySubScreen.PLAYLISTS -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(userRegistry.playlists) { playlist ->
                                    LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, playlist.name, playlist.videos.size.toString()) {
                                        selectedPlaylist = playlist
                                        currentLibSub = LibrarySubScreen.PLAYLIST_DETAIL
                                    }
                                }
                            }
                        }
                        LibrarySubScreen.PLAYLIST_DETAIL -> {
                            VideoListScreen(selectedPlaylist?.videos ?: emptyList(), userRegistry, playVideo, { shareVideo(it) }, { video -> selectedPlaylist?.let { registryManager.removeFromPlaylist(it.id, video.videoUrl); userRegistry = registryManager.registry; selectedPlaylist = userRegistry.playlists.find { p -> p.id == it.id } } }, "playlist")
                        }
                    }
                }
            }

            if (isSearchExpanded && userRegistry.searchHistory.isNotEmpty()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn {
                        items(userRegistry.searchHistory) { query ->
                            Row(Modifier.fillMaxWidth().clickable { performSearch(query) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = Color.Gray); Spacer(Modifier.width(16.dp)); Text(query)
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
                                            onToggleSub = { author -> 
                                                val subs = userRegistry.subscriptions.toMutableList()
                                                if (subs.any { it.name == author.name }) subs.removeAll { it.name == author.name }
                                                else subs.add(author)
                                                registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
                                                userRegistry = registryManager.registry
                                            },
                                            onLike = { currentVideo?.let { val added = registryManager.toggleLike(it); userRegistry = registryManager.registry; scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Понравившиеся" else "Удалено из Понравившихся") } } },
                                            onShare = { currentVideo?.let { shareVideo(it) } },
                                            onAddToPlaylist = { showPlaylistDialog = currentVideo }
                                        )
                                        HorizontalDivider(); Text("Рекомендации", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                                    }
                                    items(relatedVideos) { video ->
                                        val history = userRegistry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
                                        VideoItem(video, history, onClick = { playVideo(video) }, onMoreClick = { action ->
                                            when(action) {
                                                "later" -> { val added = registryManager.toggleWatchLater(video); userRegistry = registryManager.registry; scope.launch { snackbarHostState.showSnackbar(if(added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже") } }
                                                "playlist" -> { showPlaylistDialog = video }
                                                "share" -> shareVideo(video)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    } else {
                        MiniPlayer(currentVideo, isPlaying, exoPlayer, onClose = { playerState = PlayerState.CLOSED; exoPlayer.stop() }, onClick = { playerState = PlayerState.FULL })
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
                        scope.launch { snackbarHostState.showSnackbar("Добавлено в $name") }
                    },
                    onCreateNew = { name ->
                        registryManager.addToPlaylist(name, video)
                        userRegistry = registryManager.registry
                        showPlaylistDialog = null
                        scope.launch { snackbarHostState.showSnackbar("Плейлист $name создан") }
                    }
                )
            }
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
            if (isCreating) {
                TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Название") })
            } else {
                LazyColumn {
                    items(playlists) { p ->
                        Row(Modifier.fillMaxWidth().clickable { onPlaylistSelected(p.name) }.padding(12.dp)) { Text(p.name) }
                    }
                    item {
                        TextButton(onClick = { isCreating = true }) { Text("+ Создать новый") }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreating) {
                Button(onClick = { if(newName.isNotBlank()) onCreateNew(newName) }) { Text("Создать") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun VideoListScreen(videos: List<SearchResult>, registry: UserRegistry, onVideoClick: (SearchResult) -> Unit, onShare: (SearchResult) -> Unit, onRemove: (SearchResult) -> Unit, type: String) {
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Список пуст") }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(videos) { video ->
                VideoItem(video, null, onClick = { onVideoClick(video) }, onMoreClick = { action ->
                    if (action == "remove") onRemove(video)
                    else if (action == "share") onShare(video)
                }, isEditMode = true)
            }
        }
    }
}

@Composable
fun VideoDetails(video: SearchResult?, registry: UserRegistry, onToggleSub: (Author) -> Unit, onLike: () -> Unit, onShare: () -> Unit, onAddToPlaylist: () -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text(video?.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = video?.author?.avatarUrl ?: "", contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
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
            DetailAction(Icons.Outlined.ThumbDown, "Дизлайк")
            DetailAction(Icons.Default.Share, "Поделиться", onClick = onShare)
            DetailAction(Icons.AutoMirrored.Filled.PlaylistAdd, "В плейлист", onClick = onAddToPlaylist)
        }
    }
}

@Composable
fun DetailAction(icon: ImageVector, label: String, color: Color = LocalContentColor.current, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LibraryScreen(registry: UserRegistry, onVideoClick: (WatchHistoryItem) -> Unit, onActionClick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LibraryHeader(Icons.Default.History, "История")
                Spacer(Modifier.weight(1f))
                Text("Все", color = Color.Red, modifier = Modifier.clickable { onActionClick("history") })
            }
            LazyRow(Modifier.fillMaxWidth().height(140.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(registry.watchHistory.take(10)) { item ->
                    Column(Modifier.width(160.dp).padding(end = 8.dp).clickable { onVideoClick(item) }) {
                        Box(Modifier.fillMaxWidth().height(90.dp)) {
                            AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            if (item.totalDuration > 0) { LinearProgressIndicator(progress = { item.progress.toFloat() / item.totalDuration }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).padding(horizontal = 4.dp), color = Color.Red, trackColor = Color.Gray.copy(alpha = 0.5f)) }
                        }
                        Text(item.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LibraryRow(icon: ImageVector, title: String, count: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f)); Text(count, color = Color.Gray); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

@Composable
fun VideoItem(video: SearchResult, history: WatchHistoryItem?, onClick: () -> Unit, onMoreClick: (String) -> Unit, isEditMode: Boolean = false) {
    var showMenu by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(bottom = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.DarkGray), contentScale = ContentScale.Crop)
            if (history != null && history.totalDuration > 0) { LinearProgressIndicator(progress = { history.progress.toFloat() / history.totalDuration }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Color.Red, trackColor = Color.Gray.copy(alpha = 0.5f)) }
            video.duration?.let { durSeconds -> Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)) { Text(text = formatDuration(durSeconds.toLong()), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) } }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AsyncImage(model = video.author?.avatarUrl ?: "https://rutube.ru/static/img/default-avatar.png", contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
                Text("${video.author?.name ?: "Автор"} • Rutube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!isEditMode) {
                        DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { showMenu = false; onMoreClick("playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                        DropdownMenuItem(text = { Text("Смотреть позже") }, onClick = { showMenu = false; onMoreClick("later") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                    } else {
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onMoreClick("remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                    DropdownMenuItem(text = { Text("Поделиться") }, onClick = { showMenu = false; onMoreClick("share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
fun setScreenOrientation(context: Context, orientation: Int) { context.findActivity()?.requestedOrientation = orientation }
fun hideSystemBars(activity: Activity) { WindowCompat.setDecorFitsSystemWindows(activity.window, false); WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
fun showSystemBars(activity: Activity) { WindowCompat.setDecorFitsSystemWindows(activity.window, true); WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
fun extractRutubeId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }
fun extractId(url: String): String = url.split("/").lastOrNull { it.isNotEmpty() } ?: ""
