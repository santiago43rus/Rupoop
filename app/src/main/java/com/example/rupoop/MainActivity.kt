package com.example.rupoop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerState { CLOSED, MINI, FULL }

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
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
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            settingsManager.isDarkTheme = isDarkTheme
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubeApp(isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var playerState by remember { mutableStateOf(PlayerState.CLOSED) }
    var currentVideo by remember { mutableStateOf<SearchResult?>(null) }
    var isFullscreenVideo by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })
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

    BackHandler(enabled = playerState != PlayerState.CLOSED) {
        if (isFullscreenVideo) toggleFullscreen(false)
        else if (playerState == PlayerState.FULL) playerState = PlayerState.MINI
        else { playerState = PlayerState.CLOSED; exoPlayer.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Верхняя панель скрывается при полном плеере
            if (playerState != PlayerState.FULL) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        if (!isSearchExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rupoop", fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                            }
                        } else {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Поиск") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    scope.launch {
                                        try {
                                            val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(searchQuery) }
                                            searchResults = resp.results
                                        } catch (e: Exception) {}
                                    }
                                    isSearchExpanded = false
                                })
                            )
                        }
                    },
                    actions = {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = onThemeToggle) { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                            IconButton(onClick = {}) { Icon(Icons.Default.AccountCircle, null) }
                        } else {
                            IconButton(onClick = { isSearchExpanded = false; searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { video ->
                    VideoItem(video) {
                        focusManager.clearFocus()
                        currentVideo = video
                        extractRutubeId(video.videoUrl)?.let { id ->
                            scope.launch {
                                try {
                                    val opt = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                                    opt.videoBalancer?.m3u8?.let { url ->
                                        exoPlayer.setMediaItem(MediaItem.fromUri(url))
                                        exoPlayer.prepare()
                                        playerState = PlayerState.FULL
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        if (playerState != PlayerState.CLOSED) {
            // Строгое соответствие высоте экрана (без +100dp)
            val playerHeight by animateDpAsState(
                targetValue = if (playerState == PlayerState.FULL) config.screenHeightDp.dp else 64.dp,
                label = "playerHeight"
            )

            Box(modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(playerHeight)
                .background(MaterialTheme.colorScheme.background)
            ) {
                if (playerState == PlayerState.FULL) {
                    CustomVideoPlayer(
                        exoPlayer = exoPlayer,
                        isPlaying = isPlaying,
                        isFullscreen = isFullscreenVideo,
                        currentVideo = currentVideo,
                        onMinimize = { playerState = PlayerState.MINI },
                        onToggleFullscreen = { toggleFullscreen(!isFullscreenVideo) }
                    )
                } else {
                    MiniPlayer(currentVideo, isPlaying, exoPlayer,
                        onClose = { playerState = PlayerState.CLOSED; exoPlayer.stop() },
                        onClick = { playerState = PlayerState.FULL }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoItem(video: SearchResult, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(bottom = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            video.duration?.let { durSeconds ->
                if (durSeconds > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Text(
                            text = formatDuration(durSeconds.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = video.author?.avatarUrl ?: "https://rutube.ru/static/img/default-avatar.png",
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                Text("${video.author?.name ?: "Автор"} • Rutube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
fun setScreenOrientation(context: Context, orientation: Int) {
    val activity = context.findActivity() ?: return
    activity.requestedOrientation = orientation
}
fun hideSystemBars(activity: Activity) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
fun showSystemBars(activity: Activity) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}
fun extractRutubeId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }
