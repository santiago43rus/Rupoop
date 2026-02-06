package com.example.rupoop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh // Замена для Pause если она не находится
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.abs

enum class PlayerState { CLOSED, MINI, FULL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
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
    var isLoading by remember { mutableStateOf(false) }

    var playerState by remember { mutableStateOf(PlayerState.CLOSED) }
    var currentVideo by remember { mutableStateOf<SearchResult?>(null) }
    var isFullscreenVideo by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
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
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(searchQuery, onQueryChange = { searchQuery = it }) {
                scope.launch {
                    isLoading = true
                    try {
                        val response = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(searchQuery) }
                        searchResults = response.results
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка поиска", Toast.LENGTH_SHORT).show()
                    } finally { isLoading = false }
                }
            }

            if (isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(searchResults) { video ->
                        VideoItem(video) {
                            currentVideo = video
                            scope.launch {
                                extractRutubeId(video.videoUrl)?.let { id ->
                                    try {
                                        val options = withContext(Dispatchers.IO) { RetrofitClient.api.getVideoOptions(id) }
                                        options.videoBalancer?.m3u8?.let { url ->
                                            exoPlayer.setMediaItem(MediaItem.fromUri(url))
                                            exoPlayer.prepare()
                                            playerState = PlayerState.FULL
                                        }
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (playerState != PlayerState.CLOSED) {
            val playerHeight by animateDpAsState(
                targetValue = if (playerState == PlayerState.FULL) config.screenHeightDp.dp else 72.dp,
                label = "playerHeight"
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(playerHeight)
                    .padding(bottom = if (playerState == PlayerState.FULL) 0.dp else 8.dp)
                    .padding(horizontal = if (playerState == PlayerState.FULL) 0.dp else 8.dp)
                    .pointerInput(playerState, isFullscreenVideo) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60) {
                                if (isFullscreenVideo) toggleFullscreen(false)
                                else if (playerState == PlayerState.FULL) playerState = PlayerState.MINI
                            } else if (dragAmount < -60) {
                                if (playerState == PlayerState.MINI) playerState = PlayerState.FULL
                                else if (playerState == PlayerState.FULL && !isFullscreenVideo) toggleFullscreen(true)
                            }
                        }
                    },
                shape = if (playerState == PlayerState.FULL) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                if (playerState == PlayerState.FULL) {
                    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(if (isFullscreenVideo) 2.1f else 1.77f)) {
                            VideoPlayerView(exoPlayer, true) { toggleFullscreen(it) }
                            IconButton(
                                onClick = { playerState = PlayerState.MINI },
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(0.3f), RoundedCornerShape(20.dp))
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Свернуть", tint = Color.White)
                            }
                        }
                        if (!isFullscreenVideo) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(currentVideo?.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(currentVideo?.author?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (abs(dragAmount) > 50) { playerState = PlayerState.CLOSED; exoPlayer.stop() }
                                }
                            }
                            .clickable { playerState = PlayerState.FULL },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(120.dp).fillMaxHeight()) {
                            VideoPlayerView(exoPlayer, false) {}
                            Box(Modifier.fillMaxSize().background(Color.Transparent))
                        }
                        Text(currentVideo?.title ?: "", modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

                        // Используем прямое имя иконки, чтобы избежать ошибок импорта
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            val icon = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow
                            Icon(icon, null)
                        }
                        IconButton(onClick = { playerState = PlayerState.CLOSED; exoPlayer.stop() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerView(exoPlayer: ExoPlayer, showControls: Boolean, onFullscreenToggle: (Boolean) -> Unit = {}) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = showControls
                setFullscreenButtonClickListener { onFullscreenToggle(it) }
            }
        },
        update = {
            it.useController = showControls
            it.setFullscreenButtonClickListener { toggle -> onFullscreenToggle(toggle) }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = query, onValueChange = onQueryChange, label = { Text("Поиск...") }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = onSearch) { Text("Найти") }
    }
}

@Composable
fun VideoItem(video: SearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onClick() }) {
        Column {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16/9f), contentScale = ContentScale.Crop)
            Row(modifier = Modifier.padding(12.dp)) {
                Box(Modifier.size(40.dp).background(Color.Gray, RoundedCornerShape(20.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = video.author?.name ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

fun extractRutubeId(url: String): String? = "video/([a-zA-Z0-9]+)".toRegex().find(url)?.groupValues?.get(1)
fun setScreenOrientation(context: Context, orientation: Int) { context.findActivity()?.requestedOrientation = orientation }
fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }
fun hideSystemBars(activity: Activity) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
}
fun showSystemBars(activity: Activity) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.show(WindowInsetsCompat.Type.systemBars())
}

@Serializable
data class RutubeResponse(@SerialName("video_balancer") val videoBalancer: VideoBalancer? = null)
@Serializable
data class VideoBalancer(@SerialName("m3u8") val m3u8: String? = null)
@Serializable
data class SearchResponse(val results: List<SearchResult> = emptyList())
@Serializable
data class SearchResult(@SerialName("video_url") val videoUrl: String, val title: String, @SerialName("thumbnail_url") val thumbnailUrl: String? = null, val author: Author? = null)
@Serializable
data class Author(val name: String)

interface RutubeApi {
    @GET("api/search/video/?format=json")
    suspend fun searchVideos(@Query("query") query: String): SearchResponse
    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
}

object RetrofitClient {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    val api: RutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RutubeApi::class.java)
    }
}