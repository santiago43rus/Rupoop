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
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var deepLinkVideoUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            var isDarkTheme by remember { mutableStateOf(vm.settingsManager.isDarkTheme) }

            SideEffect {
                val window = (this as Activity).window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }

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
        if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data != null && data.host == "rutube.ru" && data.path?.startsWith("/video/") == true) {
                deepLinkVideoUrl = data.toString()
            }
        }
    }
}
