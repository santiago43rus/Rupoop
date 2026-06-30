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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.util.LibrarySubScreen
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.OverlayState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    val scrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var cursorRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(searchState.selection, textLayoutResult) {
        val result = textLayoutResult ?: return@LaunchedEffect
        cursorRect = runCatching { result.getCursorRect(searchState.selection.start) }.getOrNull()
    }

    LaunchedEffect(
        scrollState,
        cursorRect,
        layoutCoords
    ) {
        val rect = cursorRect ?: return@LaunchedEffect
        val coords = layoutCoords ?: return@LaunchedEffect
        if (!coords.isAttached) return@LaunchedEffect
        val topLeft = coords.localToWindow(rect.topLeft)
        val screenWidthPx = Resources.getSystem().displayMetrics.widthPixels.toFloat()

        val cursorX = topLeft.x - scrollState.value
        val percentage = (cursorX / screenWidthPx) * 100f
    }

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

    TopAppBar(
        title = {
            val topVisible = vm.overlayOrder.lastOrNull {
                (it == OverlayState.SEARCH && vm.isSearchVisible) ||
                (it == OverlayState.AUTHOR && vm.isAuthorVisible && vm.currentNav == NavItem.HOME)
            }
            if (!vm.isSearchExpanded && topVisible != OverlayState.SEARCH) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topVisible != null || vm.isSettingsVisible || vm.currentLibSub != LibrarySubScreen.NONE) {
                        IconButton(onClick = {
                            vm.handleBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    }
                    Icon(Icons.Default.PlayCircleFilled, null, tint = Color(0xFFE53935), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
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
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Styled search bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.isSearchExpanded) {
                        IconButton(onClick = { 
                            vm.isSearchExpanded = false
                            focusManager.clearFocus()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    } else if (topVisible == OverlayState.SEARCH) {
                        IconButton(onClick = { vm.clearCurrentSearchStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        tonalElevation = 2.dp
                    ) {
                        BasicTextField(
                            state = searchState,
                            scrollState = scrollState,
                            interactionSource = interactionSource,
                            onTextLayout = { textLayoutResult = it() },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = LocalTextStyle.current.copy(MaterialTheme.colorScheme.onBackground, fontSize = 16.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            onKeyboardAction = {
                                focusManager.clearFocus()
                                vm.performSearch(vm.searchQuery)
                                onSearch()
                            },
                            decorator = { innerTextField ->
                                OutlinedTextFieldDefaults.DecorationBox(
                                    value = searchState.text.toString(),
                                    innerTextField = innerTextField,
                                    enabled = true,
                                    singleLine = true,
                                    visualTransformation = VisualTransformation.None,
                                    interactionSource = interactionSource,
                                    placeholder = {
                                        Text("Поиск видео...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 16.sp)
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (vm.searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { vm.searchQuery = ""; vm.isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Close, "Очистить", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                            }
                                            IconButton(onClick = {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    startVoiceInput()
                                                } else {
                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }, modifier = Modifier.size(36.dp)) {
                                                Icon(
                                                    if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                                    "Голосовой поиск",
                                                    tint = if (isListening) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    },
                                    container = {
                                        OutlinedTextFieldDefaults.Container(
                                            enabled = true,
                                            isError = false,
                                            interactionSource = interactionSource,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color.Transparent,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledBorderColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        vm.isSearchExpanded = true
                                    }
                                }
                                .onGloballyPositioned { layoutCoords = it }
                        )
                    }
                }
            }
        },
        actions = {
            val topVisible = vm.overlayOrder.lastOrNull {
                (it == OverlayState.SEARCH && vm.isSearchVisible) ||
                (it == OverlayState.AUTHOR && vm.isAuthorVisible)
            }
            if (!vm.isSearchExpanded && topVisible != OverlayState.SEARCH) {
                if (topVisible == null && vm.currentLibSub == LibrarySubScreen.NONE && !vm.isSettingsVisible) {
                    IconButton(onClick = { vm.isSearchExpanded = true }) { Icon(Icons.Default.Search, null) }
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startVoiceInput()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            "Голосовой поиск",
                            tint = if (isListening) Color(0xFFE53935) else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { vm.isSettingsVisible = true }) { Icon(Icons.Default.Settings, null) }
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = {
                            if (!vm.isAuthenticated && !vm.isAuthenticating) authLauncher.launch(vm.authManager.createAuthIntent())
                            else if (vm.isAuthenticated) vm.isAccountMenuExpanded = true
                        }) {
                            if (vm.isAuthenticating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(
                                if (vm.isAuthenticated) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle, null,
                                tint = if (vm.isAuthenticated) Color(0xFF4CAF50) else LocalContentColor.current
                            )
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
                    IconButton(onClick = { vm.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Выйти") }
                }
            } else {
                // Actions are hidden when search is expanded or showing results
            }
        }
    )
}
