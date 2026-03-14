package com.santiago43rus.rupoop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.santiago43rus.rupoop.ui.theme.RupoopTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingLocalFilePath by mutableStateOf<String?>(null)
    private var pendingLocalFileTitle by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.bindActivity(this)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
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
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.pausePlayback()
    }

    override fun onDestroy() {
        vm.unbindActivity(this)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "PLAY_LOCAL_FILE") {
            pendingLocalFilePath = intent.getStringExtra("FILE_PATH")
            pendingLocalFileTitle = intent.getStringExtra("TITLE")
        }
    }
}
