package com.example.rupoop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- 1. DATA LAYER ---

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer") val videoBalancer: VideoBalancer? = null
)

@Serializable
data class VideoBalancer(
    @SerialName("m3u8") val m3u8: String? = null
)

@Serializable
data class SearchResponse(
    val results: List<SearchResult> = emptyList()
)

@Serializable
data class SearchResult(
    @SerialName("video_url") val videoUrl: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val author: Author? = null
)

@Serializable
data class Author(
    val name: String
)

interface RutubeApi {
    @GET("api/search/video/?format=json")
    suspend fun searchVideos(@Query("query") query: String): SearchResponse

    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
}

object RetrofitClient {
    private val json = Json { ignoreUnknownKeys = true }
    val api: RutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RutubeApi::class.java)
    }
}

// --- 2. UI & LOGIC ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RutubePlayerScreen()
                }
            }
        }
    }
}

@Composable
fun RutubePlayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen || streamUrl != null) {
        if (isFullscreen) {
            isFullscreen = false
            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            showSystemBars(context.findActivity()!!)
        } else if (streamUrl != null) {
            streamUrl = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (!isFullscreen && streamUrl == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search video...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    RetrofitClient.api.searchVideos(searchQuery)
                                }
                                searchResults = response.results
                            } catch (e: Exception) {
                                Toast.makeText(context, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Text("Search")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (streamUrl != null) {
                VideoPlayer(
                    uri = Uri.parse(streamUrl),
                    isFullscreen = isFullscreen,
                    onFullscreenToggle = { shouldBeFull ->
                        isFullscreen = shouldBeFull
                        val activity = context.findActivity()
                        if (activity != null) {
                            if (shouldBeFull) {
                                setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                hideSystemBars(activity)
                            } else {
                                setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                                showSystemBars(activity)
                            }
                        }
                    }
                )
            } else {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn {
                        items(searchResults) { video ->
                            VideoItem(video) {
                                scope.launch {
                                    val id = extractRutubeId(video.videoUrl)
                                    if (id != null) {
                                        try {
                                            val options = withContext(Dispatchers.IO) {
                                                RetrofitClient.api.getVideoOptions(id)
                                            }
                                            streamUrl = options.videoBalancer?.m3u8
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Play error", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItem(video: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp, 68.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(text = video.author?.name ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun VideoPlayer(
    uri: Uri,
    isFullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                setFullscreenButtonClickListener { isCurrentlyFull ->
                    onFullscreenToggle(!isFullscreen)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- 3. HELPERS ---

fun extractRutubeId(url: String): String? {
    val regex = "video/([a-zA-Z0-9]+)".toRegex()
    return regex.find(url)?.groupValues?.get(1)
}

fun setScreenOrientation(context: Context, orientation: Int) {
    context.findActivity()?.requestedOrientation = orientation
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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