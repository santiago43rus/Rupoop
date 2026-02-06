package com.example.rupoop

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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

// --- 1. DATA LAYER (RETROFIT & SERIALIZATION) ---

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer")
    val videoBalancer: VideoBalancer? = null
)

@Serializable
data class VideoBalancer(
    @SerialName("m3u8")
    val m3u8: String? = null
)
interface RutubeApi {
    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
}

// Singleton Retrofit Client
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

    var urlInput by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Input Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Rutube URL") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    if (urlInput.isBlank()) return@Button
                    scope.launch {
                        isLoading = true
                        streamUrl = null // Reset previous video
                        try {
                            // 1. Extract ID
                            val videoId = extractRutubeId(urlInput)

                            if (videoId == null) {
                                Toast.makeText(context, "Invalid URL format", Toast.LENGTH_SHORT).show()
                            } else {
                                // 2. Fetch API
                                val response = withContext(Dispatchers.IO) {
                                    RetrofitClient.api.getVideoOptions(videoId)
                                }

                                // 3. Get m3u8 link
                                val m3u8Link = response.videoBalancer?.m3u8
                                if (m3u8Link != null) {
                                    streamUrl = m3u8Link
                                } else {
                                    Toast.makeText(context, "Stream link not found in API", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "..." else "Play")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video Player Area
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (streamUrl != null) {
                VideoPlayer(uri = Uri.parse(streamUrl))
            } else if (!isLoading) {
                Text("Enter a URL and press Play", modifier = Modifier.align(Alignment.Center))
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Load Media Source when URI changes
    LaunchedEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    // Dispose player when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // AndroidView to embed Legacy PlayerView into Compose
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- 3. HELPER FUNCTIONS ---

fun extractRutubeId(url: String): String? {
    // Regex to capture ID from standard URLs like https://rutube.ru/video/1234567890.../
    val regex = "video/([a-zA-Z0-9]+)".toRegex()
    val match = regex.find(url)
    return match?.groupValues?.get(1)
}