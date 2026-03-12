package com.santiago43rus.rupoop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiago43rus.rupoop.ui.theme.RupoopTheme

class MainActivity : ComponentActivity() {
    private var deepLinkVideoUrl by mutableStateOf<String?>(null)
    private var pendingLocalFilePath by mutableStateOf<String?>(null)
    private var pendingLocalFileTitle by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            var isDarkTheme by remember { mutableStateOf(vm.settingsManager.isDarkTheme) }

            // Handle pending local file playback
            LaunchedEffect(pendingLocalFilePath) {
                pendingLocalFilePath?.let { path ->
                    vm.playLocalFile(path, pendingLocalFileTitle ?: "Видео")
                    pendingLocalFilePath = null
                    pendingLocalFileTitle = null
                }
            }

            SideEffect {
                val window = (this as Activity).window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }

            RupoopTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RutubeApp(
                        vm = vm,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            vm.settingsManager.isDarkTheme = isDarkTheme
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
        if (intent?.action == "PLAY_LOCAL_FILE") {
            pendingLocalFilePath = intent.getStringExtra("FILE_PATH")
            pendingLocalFileTitle = intent.getStringExtra("TITLE")
        } else if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data != null && data.host == "rutube.ru" && data.path?.startsWith("/video/") == true) {
                deepLinkVideoUrl = data.toString()
            }
        }
    }
}
