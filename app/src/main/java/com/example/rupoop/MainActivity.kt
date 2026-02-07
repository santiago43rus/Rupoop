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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Глобальное состояние темы
            var isDarkTheme by remember { mutableStateOf(true) }

            val colorScheme = if (isDarkTheme) darkColorScheme(
                background = Color(0xFF0F0F0F),
                surface = Color(0xFF1E1E1E),
                onBackground = Color.White,
                onSurface = Color.White
            ) else lightColorScheme(
                background = Color.White,
                surface = Color(0xFFF2F2F2),
                onBackground = Color.Black,
                onSurface = Color.Black
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RutubeApp(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ВЕРХНЯЯ ПАНЕЛЬ: Поиск + Смена темы
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Поиск") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(searchQuery) }
                                searchResults = resp.results
                            } catch (e: Exception) {}
                        }
                    })
                )
                IconButton(onClick = onThemeToggle) {
                    Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                }
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
            val playerHeight by animateDpAsState(
                targetValue = if (playerState == PlayerState.FULL) config.screenHeightDp.dp + 100.dp else 80.dp,
                label = ""
            )

            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(playerHeight)) {
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
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(16/9f).background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )
        Text(video.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        Text("${video.author?.name ?: "Автор"} • Rutube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

// ... Оставшиеся хелперы (findActivity, setScreenOrientation и т.д.) без изменений
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