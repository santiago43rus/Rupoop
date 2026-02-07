package com.example.rupoop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

// 1. Состояния плеера
enum class PlayerState { CLOSED, MINI, FULL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RutubeApp()
                }
            }
        }
    }
}

@Composable
fun RutubeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current

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

    // Функции управления экраном
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
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Поиск
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Поиск в Rutube") },
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(searchQuery) }
                            searchResults = resp.results
                        }
                    }) { Icon(Icons.Default.Search, null) }
                }
            )

            LazyColumn {
                items(searchResults) { video ->
                    VideoItem(video) {
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

        // Плеер с жестами
        if (playerState != PlayerState.CLOSED) {
            val playerHeight by animateDpAsState(
                targetValue = if (playerState == PlayerState.FULL) config.screenHeightDp.dp + 100.dp else 80.dp,
                label = ""
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(playerHeight)
                    .pointerInput(playerState, isFullscreenVideo) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 50) { // Свайп вниз
                                if (isFullscreenVideo) toggleFullscreen(false)
                                else playerState = PlayerState.MINI
                            } else if (dragAmount < -50) { // Свайп вверх
                                playerState = PlayerState.FULL
                            }
                        }
                    }
            ) {
                if (playerState == PlayerState.FULL) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if(isFullscreenVideo) 2.1f else 1.77f)) {
                            CustomVideoPlayer(
                                exoPlayer = exoPlayer,
                                isPlaying = isPlaying,
                                isFullscreen = isFullscreenVideo,
                                onMinimize = { playerState = PlayerState.MINI },
                                onToggleFullscreen = { toggleFullscreen(!isFullscreenVideo) }
                            )
                        }
                        if (!isFullscreenVideo) {
                            Text(currentVideo?.title ?: "", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
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
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(8.dp)) {
        AsyncImage(video.thumbnailUrl, null, Modifier.size(120.dp, 70.dp), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 8.dp)) {
            Text(video.title, maxLines = 2)
            Text(video.author?.name ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Поиск ID видео из ссылки
fun extractRutubeId(url: String): String? {
    return url.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?")
}

// Хелперы для Activity
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
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

fun showSystemBars(activity: Activity) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, true)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.show(WindowInsetsCompat.Type.systemBars())
}