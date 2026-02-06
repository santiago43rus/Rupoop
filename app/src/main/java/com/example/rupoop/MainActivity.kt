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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

// --- 1. DATA LAYER (БЕЗ ИЗМЕНЕНИЙ) ---

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer") val videoBalancer: VideoBalancer? = null
)
@Serializable
data class VideoBalancer(
    @SerialName("m3u8") val m3u8: String? = null
)
interface RutubeApi {
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
        // Включаем отображение "под" системными барами для корректной обработки отступов
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

    var urlInput by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Состояние полноэкранного режима
    var isFullscreen by remember { mutableStateOf(false) }

    // Обработка системной кнопки "Назад" для выхода из полного экрана
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    // Применяем отступы системных баров (status bar, navigation bar), чтобы UI не перекрывался камерой
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // Скрываем панель поиска, если включен полноэкранный режим
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                            streamUrl = null
                            try {
                                val videoId = extractRutubeId(urlInput)
                                if (videoId == null) {
                                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                } else {
                                    val response = withContext(Dispatchers.IO) {
                                        RetrofitClient.api.getVideoOptions(videoId)
                                    }
                                    streamUrl = response.videoBalancer?.m3u8
                                    if (streamUrl == null) {
                                        Toast.makeText(context, "Link not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
        }

        // Область плеера
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (streamUrl != null) {
                VideoPlayer(
                    uri = Uri.parse(streamUrl),
                    isFullscreen = isFullscreen,
                    onFullscreenToggle = { shouldBeFullscreen ->
                        isFullscreen = shouldBeFullscreen
                        val activity = context.findActivity()
                        if (shouldBeFullscreen) {
                            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            activity?.let { hideSystemBars(it) }
                        } else {
                            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                            activity?.let { showSystemBars(it) }
                        }
                    }
                )
            } else if (!isLoading) {
                Text("Enter a URL and press Play", modifier = Modifier.align(Alignment.Center))
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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

                // Настраиваем кнопку полноэкранного режима в контроллере ExoPlayer
                setFullscreenButtonClickListener { isCurrentlyFull ->
                    // Если плеер думает, что он полноэкранный, мы переключаем наше состояние
                    onFullscreenToggle(!isFullscreen)
                }
            }
        },
        update = { playerView ->
            // Можно вручную управлять иконкой или состоянием здесь, если нужно
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- 3. HELPERS ---

fun extractRutubeId(url: String): String? {
    val regex = "video/([a-zA-Z0-9]+)".toRegex()
    return regex.find(url)?.groupValues?.get(1)
}

// Вспомогательная функция для смены ориентации экрана
fun setScreenOrientation(context: Context, orientation: Int) {
    val activity = context.findActivity()
    activity?.requestedOrientation = orientation
}

// Поиск Activity в контексте
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Скрытие системных баров (Fullscreen mode)
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