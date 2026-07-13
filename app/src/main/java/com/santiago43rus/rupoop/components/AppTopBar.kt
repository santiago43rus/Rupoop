package com.santiago43rus.rupoop.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.util.LibrarySubScreen
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.OverlayState
import java.util.Locale

@Composable
fun AppTopBar(
    vm: AppViewModel,
    searchState: TextFieldState,
    focusManager: FocusManager,
    authLauncher: ActivityResultLauncher<Intent>,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    // Speech recognizer setup
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    LaunchedEffect(vm.isSearchExpanded) {
        if (!vm.isSearchExpanded && isListening) {
            speechRecognizer.stopListening()
            isListening = false
        }
    }

    fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        isListening = true
        vm.isSearchExpanded = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    vm.searchQuery = matches[0]
                    vm.performSearch(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    vm.searchQuery = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput()
    }

    val topVisible = vm.overlayOrder.lastOrNull {
        (it == OverlayState.SEARCH && vm.isSearchVisible) ||
        (it == OverlayState.AUTHOR && vm.isAuthorVisible && vm.currentNav == NavItem.HOME)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!vm.isSearchExpanded && topVisible != OverlayState.SEARCH) {
                // Non-search mode: Rupoop logo & Title
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (topVisible != null || vm.isSettingsVisible || vm.currentLibSub != LibrarySubScreen.NONE) {
                        IconButton(
                            onClick = { vm.handleBack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            vm.isSettingsVisible -> "Настройки"
                            topVisible == OverlayState.AUTHOR -> vm.selectedAuthor?.name ?: "Канал"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.LIKED -> "Понравившиеся"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.WATCH_LATER -> "Смотреть позже"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.PLAYLISTS -> "Плейлисты"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.PLAYLIST_DETAIL -> vm.selectedPlaylist?.name ?: "Плейлист"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.HISTORY -> "История просмотра"
                            vm.currentNav == NavItem.LIBRARY && vm.currentLibSub == LibrarySubScreen.DOWNLOADS -> "Загрузки"
                            else -> when (vm.currentNav) {
                                NavItem.HOME -> "Rupoop"
                                NavItem.SUBSCRIPTIONS -> "Rupoop"
                                NavItem.LIBRARY -> "Rupoop"
                                else -> "Rupoop"
                            }
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Non-search actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topVisible == null && vm.currentLibSub == LibrarySubScreen.NONE && !vm.isSettingsVisible) {
                        IconButton(onClick = { vm.isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startVoiceInput()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Голосовой поиск",
                                tint = if (isListening) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { vm.isSettingsVisible = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                        }
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(onClick = {
                                if (!vm.isAuthenticated && !vm.isAuthenticating) authLauncher.launch(vm.authManager.createAuthIntent())
                                else if (vm.isAuthenticated) vm.isAccountMenuExpanded = true
                            }, modifier = Modifier.size(36.dp)) {
                                if (vm.isAuthenticating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (vm.isAuthenticated) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = if (vm.isAuthenticated) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (vm.isAuthenticated) {
                                DropdownMenu(expanded = vm.isAccountMenuExpanded, onDismissRequest = { vm.isAccountMenuExpanded = false }) {
                                    vm.githubUser?.let { user ->
                                        DropdownMenuItem(text = { Text(user.login, fontWeight = FontWeight.Bold) }, onClick = {}, enabled = false)
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(text = { Text("Синхронизировать") }, onClick = { vm.isAccountMenuExpanded = false; vm.syncWithGitHub() })
                                    DropdownMenuItem(text = { Text("Выйти") }, onClick = { vm.isAccountMenuExpanded = false; vm.logout() })
                                }
                            }
                        }
                    } else if (vm.isSettingsVisible && vm.isAuthenticated) {
                        IconButton(onClick = { vm.logout() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Выйти", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                // Search mode: Full width search bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vm.isSearchExpanded) {
                        IconButton(onClick = { 
                            vm.isSearchExpanded = false
                            focusManager.clearFocus()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", modifier = Modifier.size(20.dp))
                        }
                    } else if (topVisible == OverlayState.SEARCH) {
                        IconButton(onClick = { vm.clearCurrentSearchStack() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
                        }
                    }
                    OutlinedTextField(
                        state = searchState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    vm.isSearchExpanded = true
                                }
                            },
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        placeholder = {
                            Text("Поиск видео...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            focusManager.clearFocus()
                            vm.performSearch(vm.searchQuery)
                            onSearch()
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (vm.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.searchQuery = ""; vm.isSearchExpanded = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Очистить", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startVoiceInput()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                        contentDescription = "Голосовой поиск",
                                        tint = if (isListening) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            disabledBorderColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
