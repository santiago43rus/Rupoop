package com.santiago43rus.rupoop

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.annotation.OptIn
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiago43rus.rupoop.theme.RupoopTheme
import com.santiago43rus.rupoop.util.*

private const val ACTION_PIP_PREV = "com.santiago43rus.rupoop.PIP_PREV"
private const val ACTION_PIP_PLAY_PAUSE = "com.santiago43rus.rupoop.PIP_PLAY_PAUSE"
private const val ACTION_PIP_NEXT = "com.santiago43rus.rupoop.PIP_NEXT"

@OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private var deepLinkVideoUrl by mutableStateOf<String?>(null)
    private var pendingLocalFilePath by mutableStateOf<String?>(null)
    private var pendingLocalFileTitle by mutableStateOf<String?>(null)
    private var pendingOpenDownloads by mutableStateOf(false)
    private var pendingOpenPlayer by mutableStateOf(false)
    private var appViewModel: AppViewModel? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val vm = appViewModel ?: return
            when (intent?.action) {
                ACTION_PIP_PREV -> vm.playPrevious()
                ACTION_PIP_PLAY_PAUSE -> {
                    if (vm.isPlaying) {
                        vm.exoPlayer.pause()
                    } else {
                        vm.exoPlayer.play()
                    }
                }
                ACTION_PIP_NEXT -> vm.playNext()
            }
            updatePipParams()
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))

        val vm = appViewModel
        if (vm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isFirst = vm.currentVideoIndex <= 0
            val isLast = if (vm.isPlaylistMode) vm.currentVideoIndex >= vm.currentVideoList.size - 1
            else (vm.currentVideoIndex >= vm.currentVideoList.size - 1 && vm.relatedVideos.isEmpty())

            val actions = mutableListOf<RemoteAction>()

            // 1. Previous
            val prevIntent = PendingIntent.getBroadcast(
                this, 101, Intent(ACTION_PIP_PREV).apply { `package` = packageName },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevAction = RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                "Предыдущее",
                "Предыдущее",
                prevIntent
            ).apply { isEnabled = !isFirst }
            actions.add(prevAction)

            // 2. Play / Pause
            val playPauseIntent = PendingIntent.getBroadcast(
                this, 102, Intent(ACTION_PIP_PLAY_PAUSE).apply { `package` = packageName },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val iconRes = if (vm.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val title = if (vm.isPlaying) "Пауза" else "Воспроизведение"
            val playPauseAction = RemoteAction(
                Icon.createWithResource(this, iconRes),
                title,
                title,
                playPauseIntent
            )
            actions.add(playPauseAction)

            // 3. Next
            val nextIntent = PendingIntent.getBroadcast(
                this, 103, Intent(ACTION_PIP_NEXT).apply { `package` = packageName },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextAction = RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_next),
                "Следующее",
                "Следующее",
                nextIntent
            ).apply { isEnabled = !isLast }
            actions.add(nextAction)

            builder.setActions(actions)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canAutoEnter = vm.settingsManager.pipEnabled &&
                        vm.currentVideo != null &&
                        vm.playerState != PlayerState.CLOSED &&
                        vm.isPlaying
                builder.setAutoEnterEnabled(canAutoEnter)
            }
        }
        return builder.build()
    }

    fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                setPictureInPictureParams(buildPipParams())
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error updating PiP params", e)
            }
        }
    }

    fun enterPipMode() {
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to enter PiP mode", e)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val vm = appViewModel ?: return
        if (vm.settingsManager.pipEnabled &&
            vm.currentVideo != null &&
            vm.playerState != PlayerState.CLOSED &&
            vm.isPlaying
        ) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        appViewModel?.isInPipMode = isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {}
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PREV)
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            pipReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        handleIntent(intent)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val vm: AppViewModel = viewModel()
            appViewModel = vm
            vm.onRequestPip = { enterPipMode() }

            // Update PiP actions whenever state changes
            LaunchedEffect(vm.settingsManager.pipEnabled, vm.currentVideo, vm.playerState, vm.isPlaying, vm.currentVideoIndex) {
                updatePipParams()
            }
            var themeMode by remember { mutableStateOf(vm.settingsManager.themeMode) }
            val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark", "easter", "secret" -> true
                else -> systemDarkTheme
            }

            // Handle pending player expansion from notification tap
            LaunchedEffect(pendingOpenPlayer) {
                if (pendingOpenPlayer) {
                    if (vm.currentVideo != null && vm.playerState == PlayerState.MINI) {
                        vm.playerState = PlayerState.FULL
                    }
                    pendingOpenPlayer = false
                }
            }

            // Handle pending local file playback
            LaunchedEffect(pendingLocalFilePath) {
                pendingLocalFilePath?.let { path ->
                    vm.playLocalFile(path, pendingLocalFileTitle ?: "Видео")
                    pendingLocalFilePath = null
                    pendingLocalFileTitle = null
                }
            }

            // Handle pending navigation to downloads
            LaunchedEffect(pendingOpenDownloads) {
                if (pendingOpenDownloads) {
                    if (vm.playerState == PlayerState.FULL) {
                        vm.playerState = PlayerState.MINI
                    }
                    vm.currentNav = NavItem.LIBRARY
                    vm.currentLibSub = LibrarySubScreen.DOWNLOADS
                    pendingOpenDownloads = false
                }
            }

            SideEffect {
                val window = (this as Activity).window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme && themeMode != "easter" && themeMode != "secret"
            }

            RupoopTheme(themeMode = themeMode, darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RutubeApp(
                        vm = vm,
                        onThemeToggle = { newThemeMode ->
                            themeMode = newThemeMode
                            vm.settingsManager.themeMode = newThemeMode
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
        if (intent?.action == "OPEN_PLAYER") {
            pendingOpenPlayer = true
        } else if (intent?.action == "OPEN_DOWNLOADS") {
            pendingOpenDownloads = true
        } else if (intent?.action == "PLAY_LOCAL_FILE") {
            pendingLocalFilePath = intent.getStringExtra("FILE_PATH")
            pendingLocalFileTitle = intent.getStringExtra("TITLE")
        } else if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data != null) {
                deepLinkVideoUrl = data.toString()
            }
        }
    }
}
