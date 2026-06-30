package com.santiago43rus.rupoop

import android.util.Log
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.focus.FocusManager
import androidx.core.content.ContextCompat
import com.santiago43rus.rupoop.service.DownloadService
import kotlinx.coroutines.delay
import java.io.File
import kotlin.compareTo
import kotlin.text.toInt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.santiago43rus.rupoop.components.*
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.player.CustomVideoPlayer
import com.santiago43rus.rupoop.player.MiniPlayer
import com.santiago43rus.rupoop.screen.*
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationResponse
import java.util.Locale

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutubeApp(
    vm: AppViewModel,
    onThemeToggle: (String) -> Unit,
    deepLinkVideoUrl: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val config = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect snackbar events from ViewModel
    LaunchedEffect(Unit) {
        var snackbarJob: kotlinx.coroutines.Job? = null
        vm.snackbarMessage.collect { message ->
            snackbarJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarJob = scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Auth launcher
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val response = AuthorizationResponse.fromIntent(result.data!!)
            if (response != null) {
                vm.processAuthResponse(response)
            }
        }
    }
    
    // Handle orientation changes based on fullscreen state
    val context = LocalContext.current
    LaunchedEffect(vm.isFullscreenVideo) {
        if (vm.isFullscreenVideo) {
            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            context.findActivity()?.let { hideSystemBars(it) }
        } else {
            setScreenOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            context.findActivity()?.let { showSystemBars(it) }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    // Scroll states
    val homeListState = rememberLazyListState()
    val subsListState = rememberLazyListState()
    val libListState = rememberLazyListState()
    val relatedListState = rememberLazyListState()

    // Search query textfield state
    val searchState = rememberTextFieldState(vm.searchQuery)

    LaunchedEffect(vm.searchQuery) {
        if (searchState.text.toString() != vm.searchQuery) {
            searchState.edit {
                replace(0, length, vm.searchQuery)
            }
        }
    }

    LaunchedEffect(searchState.text) {
        val newText = searchState.text.toString()
        if (newText != vm.searchQuery) {
            vm.updateSearchQuery(newText)
            vm.isSearchExpanded = true
        }
    }

    // Pause on background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!vm.isBackgroundPlaybackEnabled) {
                    vm.exoPlayer.pause()
                    vm.isPlaying = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(vm.isSettingsVisible) {
        if (vm.isSettingsVisible && vm.isPlaying) {
            if (!vm.isBackgroundPlaybackEnabled) {
                vm.exoPlayer.pause()
            }
            // Keep player state intact, it's just hidden via !vm.isSettingsVisible condition
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    // Deep link
    LaunchedEffect(deepLinkVideoUrl) {
        if (deepLinkVideoUrl != null) {
            vm.handleDeepLink(deepLinkVideoUrl)
            onDeepLinkConsumed()
        }
    }

    // Initialize app & periodic saving
    LaunchedEffect(Unit) { vm.initializeApp() }
    LaunchedEffect(vm.isPlaying, vm.currentVideo) {
        if (vm.isPlaying && vm.currentVideo != null) vm.startProgressSaving()
        else vm.stopProgressSaving()
    }
    LaunchedEffect(vm.currentNav) {
        if (vm.currentNav == NavItem.SUBSCRIPTIONS) vm.loadSubscriptions(false)
        if (vm.currentNav == NavItem.HOME && vm.homeVideos.isEmpty()) vm.loadHome(false)
    }

    // Back handler
    BackHandler(
        enabled = vm.playerState != PlayerState.CLOSED || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible || vm.isSettingsVisible || vm.currentLibSub != LibrarySubScreen.NONE || vm.currentNav != NavItem.HOME
    ) {
        focusManager.clearFocus()
        vm.handleBack()
    }

    // Onboarding
    if (vm.showOnboarding) {
        ContentSelectionDialog(settingsManager = vm.settingsManager, onDismiss = { vm.dismissOnboarding() })
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    var bottomPaddingPx by remember { mutableStateOf(0f) }
    var topPaddingPx by remember { mutableStateOf(0f) }
    val maxDragDistanceVertical = 150f
    val maxDrag = with(density) { config.screenHeightDp.dp.toPx() - topPaddingPx - bottomPaddingPx - 64.dp.toPx() }

    val dragOffsetY = remember { Animatable(if (vm.playerState == PlayerState.FULL) 0f else maxDrag) }
    val fullscreenDragOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(vm.playerState, vm.isFullscreenVideo, maxDrag) {
        if (vm.isFullscreenVideo) {
            dragOffsetY.snapTo(0f)
            fullscreenDragOffsetY.snapTo(0f)
        } else if (maxDrag > 0) {
            dragOffsetY.animateTo(
                targetValue = if (vm.playerState == PlayerState.FULL) 0f else maxDrag,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    val progress = if (maxDrag > 0 && dragOffsetY.value > 0) {
        (dragOffsetY.value / maxDrag).coerceIn(0f, 1f)
    } else 0f

    val fsProgress = (fullscreenDragOffsetY.value / maxDragDistanceVertical).coerceIn(0f, 1f)
    val fsScale = 1f + (0.25f * fsProgress)
    val fsOffsetY = -fullscreenDragOffsetY.value * 0.4f

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!vm.isFullscreenVideo && !vm.isSettingsVisible) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = size.height * (1f - progress.coerceIn(0f, 1f)) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(64.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Home
                            val isHome = vm.currentNav == NavItem.HOME
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable {
                                            if (vm.currentNav == NavItem.HOME) {
                                                if (vm.isSettingsVisible || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible) {
                                                    vm.isSettingsVisible = false
                                                    vm.isSearchExpanded = false
                                                    vm.clearCurrentSearchStack()
                                                    vm.isAuthorVisible = false
                                                } else {
                                                    scope.launch { homeListState.animateScrollToItem(0) }
                                                }
                                            } else {
                                                vm.currentNav = NavItem.HOME
                                            }
                                        }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(if (isHome) Icons.Filled.Home else Icons.Outlined.Home, "Home", tint = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    Text("Главная", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }

                            // Subscriptions
                            val isSubs = vm.currentNav == NavItem.SUBSCRIPTIONS
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable {
                                            if (vm.currentNav == NavItem.SUBSCRIPTIONS) {
                                                if (vm.isSettingsVisible || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible) {
                                                    vm.isSettingsVisible = false
                                                    vm.isSearchExpanded = false
                                                    vm.clearCurrentSearchStack()
                                                    vm.isAuthorVisible = false
                                                } else {
                                                    scope.launch { subsListState.animateScrollToItem(0) }
                                                }
                                            } else {
                                                vm.currentNav = NavItem.SUBSCRIPTIONS
                                            }
                                        }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(if (isSubs) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Subs", tint = if (isSubs) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    Text("Подписки", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isSubs) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }

                            // Library
                            val isLib = vm.currentNav == NavItem.LIBRARY
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable {
                                            if (vm.currentNav == NavItem.LIBRARY) {
                                                if (vm.isSettingsVisible || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible || vm.currentLibSub != LibrarySubScreen.NONE) {
                                                    vm.isSettingsVisible = false
                                                    vm.isSearchExpanded = false
                                                    vm.clearCurrentSearchStack()
                                                    vm.isAuthorVisible = false
                                                    vm.currentLibSub = LibrarySubScreen.NONE
                                                } else {
                                                    scope.launch { libListState.animateScrollToItem(0) }
                                                }
                                            } else {
                                                vm.currentNav = NavItem.LIBRARY
                                            }
                                        }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(if (isLib) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, "Lib", tint = if (isLib) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    Text("Библиотека", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isLib) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LaunchedEffect(padding.calculateBottomPadding(), padding.calculateTopPadding()) {
                bottomPaddingPx = with(density) { padding.calculateBottomPadding().toPx() }
                topPaddingPx = with(density) { padding.calculateTopPadding().toPx() }
            }
            val realProgress = if (maxDrag > 0) (dragOffsetY.value / maxDrag) else 0f
            val isMiniThresholdReached = realProgress >= 1f

            LaunchedEffect(vm.playerState, vm.isFullscreenVideo, maxDrag) {
                if (vm.isFullscreenVideo) {
                    dragOffsetY.snapTo(0f)
                    fullscreenDragOffsetY.snapTo(0f)
                } else if (maxDrag > 0) {
                    dragOffsetY.animateTo(
                        targetValue = if (vm.playerState == PlayerState.FULL) 0f else maxDrag,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Background layer with full padding
                Column(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = realProgress.coerceIn(0f, 1f) }.padding(bottom = if (vm.playerState == PlayerState.FULL && vm.isFullscreenVideo) 0.dp else padding.calculateBottomPadding())) {
                    if (!vm.isFullscreenVideo && !vm.isHiddenVideosVisible && !vm.isNotificationSettingsVisible) {
                        AppTopBar(vm = vm, searchState = searchState, focusManager = focusManager, authLauncher = authLauncher, onSearch = {
                            scope.launch { homeListState.scrollToItem(0) }
                        })
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Main content by nav with animated transitions
                        AnimatedContent(
                        targetState = vm.currentNav,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label = "nav_animation"
                    ) { targetNav ->
                        when (targetNav) {
                        NavItem.HOME -> {
                            MainFeedScreen(
                                videos = vm.homeVideos, userRegistry = vm.userRegistry,
                                isRefreshing = vm.isRefreshingHome, isLoadingMore = vm.isHomeLoadingMore,
                                onRefresh = { vm.loadHome(false) }, onLoadMore = { vm.loadHome(true) },
                                onVideoClick = { video, list -> vm.playVideo(video, list) },
                                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) },
                                listState = homeListState
                            )
                        }
                        NavItem.SUBSCRIPTIONS -> {
                            SubscriptionsScreen(
                                userRegistry = vm.userRegistry, subscriptionVideos = vm.subscriptionVideos,
                                isRefreshing = vm.isRefreshingSubs, isLoadingMore = vm.isSubsLoadingMore, hasMoreVideos = vm.hasMoreSubsVideos,
                                onRefresh = { vm.loadSubscriptions(false) }, onLoadMore = { vm.loadSubscriptions(true) },
                                onVideoClick = { video, list -> vm.playVideo(video, list) },
                                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) },
                                listState = subsListState
                            )
                        }
                        NavItem.LIBRARY -> LibraryContent(vm = vm, listState = libListState)
                        }
                    }

                    // Overlays with animation
                    vm.overlayOrder.forEach { overlay ->
                        androidx.compose.animation.AnimatedVisibility(
                            visible = overlay == OverlayState.SEARCH && vm.isSearchVisible,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
                            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
                        ) {
                            SearchOverlay(vm = vm)
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = overlay == OverlayState.AUTHOR && vm.isAuthorVisible,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
                            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
                        ) {
                            AuthorScreen(
                                author = vm.selectedAuthor, authorVideos = vm.authorVideos, userRegistry = vm.userRegistry,
                                isRefreshing = vm.isRefreshingAuthor, isLoadingMore = vm.isAuthorLoadingMore, hasMoreVideos = vm.hasMoreAuthorVideos,
                                onRefresh = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, false) } },
                                onLoadMore = { vm.selectedAuthor?.let { vm.loadAuthorVideos(it, true) } },
                                onVideoClick = { video, list -> vm.playVideo(video, list) },
                                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                onToggleSubscription = { vm.toggleSubscription(it) },
                                onMoreClick = { video, action -> vm.handleVideoMoreAction(video, action) },
                                currentSort = vm.authorSortOrder,
                                onSortChange = { newSort ->
                                    vm.authorSortOrder = newSort
                                    vm.selectedAuthor?.let { vm.loadAuthorVideos(it, false) }
                                }
                            )
                        }
                    }
                    if (vm.isSearchExpanded && vm.searchQuery.isNotEmpty()) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                            LazyColumn {
                                items(vm.searchSuggestions) { suggestion ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(suggestion, Modifier.weight(1f).clickable {
                                            focusManager.clearFocus()
                                            vm.performSearch(suggestion)
                                            scope.launch { homeListState.scrollToItem(0) }
                                        }, fontSize = 22.sp)
                                        IconButton(
                                            onClick = { vm.searchQuery = suggestion },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.NorthWest,
                                                null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (vm.isSearchExpanded && vm.userRegistry.searchHistory.isNotEmpty()) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                            LazyColumn {
                                items(vm.userRegistry.searchHistory) { query ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(query, Modifier.weight(1f).clickable {
                                            focusManager.clearFocus()
                                            vm.performSearch(query)
                                            scope.launch { homeListState.scrollToItem(0) }
                                        }, fontSize = 20.sp)
                                        IconButton(
                                            onClick = { vm.removeSearchQuery(query) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Settings overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = vm.isSettingsVisible,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
                        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            SettingsScreen(
                                vm = vm,
                                onThemeToggle = onThemeToggle,
                                onShowHiddenVideos = { vm.isHiddenVideosVisible = true },
                                onOpenNotificationSettings = { vm.isNotificationSettingsVisible = true },
                                onNotificationsChanged = {
                                    val showBg = vm.showBackgroundNotifications
                                    if (!showBg) {
                                        try {
                                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                                            context.stopService(intent)
                                        } catch (e: Exception) {
                                            Log.e("Rupoop", "Failed to stop PlaybackService", e)
                                        }
                                    } else if (vm.playerState != PlayerState.CLOSED) {
                                        try {
                                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("Rupoop", "Failed to start PlaybackService", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Notification Settings overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = vm.isNotificationSettingsVisible,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
                        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            com.santiago43rus.rupoop.screen.NotificationSettingsScreen(
                                vm = vm,
                                onDismiss = { vm.isNotificationSettingsVisible = false },
                                onSettingsChanged = {
                                    val showBg = vm.showBackgroundNotifications
                                    if (!showBg) {
                                        try {
                                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                                            context.stopService(intent)
                                        } catch (e: Exception) {
                                            Log.e("Rupoop", "Failed to stop PlaybackService", e)
                                        }
                                    } else if (vm.playerState != PlayerState.CLOSED) {
                                        try {
                                            val intent = Intent(context, com.santiago43rus.rupoop.service.PlaybackService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("Rupoop", "Failed to start PlaybackService", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Hidden and Disliked overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = vm.isHiddenVideosVisible,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
                        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            HiddenVideosScreen(
                                registryManager = vm.registryManager,
                                onRegistryUpdate = { vm.onRegistryUpdate(it) },
                                onDismiss = { vm.isHiddenVideosVisible = false }
                            )
                        }
                    }

                    // Playlist dialog
                    vm.showPlaylistDialog?.let { video ->
                        PlaylistSelectionDialog(
                            playlists = vm.userRegistry.playlists,
                            onDismiss = { vm.showPlaylistDialog = null },
                            onPlaylistSelected = { name -> vm.addToPlaylist(name, video) },
                            onCreateNew = { name -> vm.createPlaylistAndAdd(name, video) }
                        )
                    }

                    // Download option dialog
                    vm.showDownloadDialog?.let { video ->
                        AlertDialog(
                            onDismissRequest = { vm.showDownloadDialog = null },
                            title = { Text("Выберите формат загрузки") },
                            text = { Text("Вы хотите скачать видео целиком или только звуковую дорожку?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        vm.startDownload(video, isAudio = false)
                                        vm.showDownloadDialog = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Видео (MP4)")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = {
                                        vm.startDownload(video, isAudio = true)
                                        vm.showDownloadDialog = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Аудио (M4A)")
                                }
                            }
                        )
                    }
                    } // Closes Background Content Nested Box
                } // Closes Background Content Column

                // Player Overlay (Inside Scaffold, but only respects bottom padding)
                if (vm.playerState != PlayerState.CLOSED && !vm.isSettingsVisible) {
                    val topPadding = padding.calculateTopPadding()
                    val bottomPadding = padding.calculateBottomPadding()
                    val targetFullHeight = config.screenHeightDp.dp - topPadding
                    val miniPlayerHeight = 64.dp

                    val playerHeight = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) {
                        config.screenHeightDp.dp
                    } else {
                        targetFullHeight - (targetFullHeight - miniPlayerHeight) * realProgress.coerceIn(0f, 1f)
                    }

                    val playerOffsetY = if (vm.isFullscreenVideo && vm.playerState == PlayerState.FULL) 0.dp else {
                        topPadding + (config.screenHeightDp.dp - bottomPadding - miniPlayerHeight - topPadding) * realProgress.coerceIn(0f, 1f) + if (realProgress > 1f) ((realProgress - 1f) * maxDrag).dp else 0.dp
                    }

                    val playerWidthFraction = if (vm.isFullscreenVideo || isMiniThresholdReached) 1f else {
                        1f - (1f - (100f / config.screenWidthDp)) * realProgress.coerceIn(0f, 1f)
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = playerOffsetY)
                                .fillMaxWidth(playerWidthFraction)
                                .then(if (vm.playerState == PlayerState.FULL && vm.isFullscreenVideo) Modifier.fillMaxSize() else Modifier.height(playerHeight))
                                .background(MaterialTheme.colorScheme.background)
                                .pointerInput(vm.playerState, vm.isFullscreenVideo) {
                                    if (!vm.isFullscreenVideo) {
                                        var dragStartedFrom = vm.playerState
                                        var initialDragDirection = 0f
                                        var touchStartY = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { offset ->
                                                dragStartedFrom = vm.playerState
                                                initialDragDirection = 0f
                                                touchStartY = offset.y
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    initialDragDirection = 0f
                                                    if (fullscreenDragOffsetY.value > 0f) {
                                                        if (fullscreenDragOffsetY.value > 50f) {
                                                            vm.toggleFullscreen(true)
                                                        }
                                                        fullscreenDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                    } else if (dragOffsetY.value > maxDrag + 80f) {
                                                        vm.closePlayer()
                                                        dragOffsetY.snapTo(maxDrag)
                                                    } else {
                                                        val threshold = if (dragStartedFrom == PlayerState.MINI) maxDrag * 0.85f else maxDrag * 0.15f
                                                        if (dragOffsetY.value > threshold) {
                                                            vm.playerState = PlayerState.MINI
                                                            dragOffsetY.animateTo(maxDrag, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                                        } else {
                                                            vm.playerState = PlayerState.FULL
                                                            dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                                        }
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    initialDragDirection = 0f
                                                    fullscreenDragOffsetY.animateTo(0f)
                                                    dragOffsetY.animateTo(if (vm.playerState == PlayerState.FULL) 0f else maxDrag, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            if (initialDragDirection == 0f && kotlin.math.abs(dragAmount) > 1f) {
                                                initialDragDirection = kotlin.math.sign(dragAmount)
                                            }
                                            
                                            val activeDragAmount = dragAmount * 1.1f
                                            val isAtTop = relatedListState.firstVisibleItemIndex == 0 && relatedListState.firstVisibleItemScrollOffset == 0
                                            val isTouchOnPlayer = touchStartY < size.width * (9f / 16f)

                                            if (dragStartedFrom == PlayerState.MINI) {
                                                change.consume()
                                                if (initialDragDirection > 0f) {
                                                    // Swipe down to close
                                                    coroutineScope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceAtMost(maxDrag + 300f).coerceAtLeast(maxDrag)) }
                                                } else if (initialDragDirection < 0f) {
                                                    // Swipe up to expand
                                                    coroutineScope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                                                }
                                            } else if (dragStartedFrom == PlayerState.FULL && (isAtTop || isTouchOnPlayer)) {
                                                if (initialDragDirection > 0f) {
                                                    // Swipe down to minimize
                                                    change.consume()
                                                    coroutineScope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                                                } else if (initialDragDirection < 0f) {
                                                    // Swipe UP to fullscreen
                                                    change.consume()
                                                    coroutineScope.launch { fullscreenDragOffsetY.snapTo((fullscreenDragOffsetY.value - activeDragAmount).coerceIn(0f, maxDragDistanceVertical)) }
                                                }
                                            } else if (dragOffsetY.value > 0 && dragOffsetY.value < maxDrag) {
                                                // Handle drag while mid-transition
                                                change.consume()
                                                coroutineScope.launch { dragOffsetY.snapTo((dragOffsetY.value + activeDragAmount).coerceIn(0f, maxDrag)) }
                                            }
                                        }
                                    }
                                }
                        ) {
                            if (!isMiniThresholdReached) {
                                Column(modifier = Modifier.fillMaxSize()) {

                                    Box(modifier = Modifier.graphicsLayer {
                                        scaleX = fsScale
                                        scaleY = fsScale
                                        translationY = fsOffsetY
                                    }) {
                                        CustomVideoPlayer(
                                            exoPlayer = vm.exoPlayer, isPlaying = vm.isPlaying, isBuffering = vm.isBuffering, isFullscreen = vm.isFullscreenVideo,
                                            currentVideo = vm.currentVideo, relatedVideos = vm.relatedVideos,
                                            onMinimize = { vm.playerState = PlayerState.MINI },
                                            onToggleFullscreen = { vm.toggleFullscreen(!vm.isFullscreenVideo) },
                                            onNext = { vm.playNext() }, onPrevious = { vm.playPrevious() },
                                            isFirstVideo = vm.currentVideoIndex <= 0,
                                            isPreviousDisliked = vm.isPreviousVideoDislikedOrHidden(),
                                            isLastVideo = if (vm.isPlaylistMode) vm.currentVideoIndex >= vm.currentVideoList.size - 1 else (vm.currentVideoIndex >= vm.currentVideoList.size - 1 && vm.relatedVideos.isEmpty()),
                                            isTransitioning = fsProgress > 0f || realProgress > 0f,
                                            onPlayRelated = { vm.playVideo(it, vm.relatedVideos) }
                                        )
                                    }
                                    if (!vm.isFullscreenVideo) {
                                        LaunchedEffect(vm.currentVideo) {
                                            relatedListState.scrollToItem(0)
                                        }
                                        RelatedVideosList(
                                            modifier = Modifier.fillMaxSize(),
                                            listState = relatedListState,
                                            currentVideo = vm.currentVideo,
                                            relatedVideos = vm.relatedVideos,
                                            userRegistry = vm.userRegistry,
                                            onAuthorClick = { vm.loadAuthorVideos(it, false) },
                                            onToggleSub = { vm.toggleSubscription(it) },
                                            onLike = { vm.toggleLike(it) },
                                            onDislike = { vm.toggleDislike(it) },
                                            onShare = { vm.shareVideo(it) },
                                            onAddToPlaylist = { vm.showPlaylistDialog = it },
                                            onDownload = { vm.showDownloadDialog = it },
                                            onVideoClick = { v, list -> vm.playVideo(v, list, false) },
                                            onMoreClick = { item, action -> vm.handleVideoMoreAction(item, action) },
                                            alphaProgress = realProgress,
                                            isBackgroundEnabled = vm.isBackgroundPlaybackEnabled,
                                            onBackgroundPlayToggle = { vm.toggleBackgroundPlayback() }
                                        )
                                    }
                                }
                            } else {
                                MiniPlayer(vm.currentVideo, vm.isPlaying, vm.exoPlayer, onClose = { vm.closePlayer() }, onClick = { vm.playerState = PlayerState.FULL })
                            }
                        }
                    }
                } // Ends Player block
            } // Closes Root Scaffold Content Box
        } // Closes Scaffold

        // Custom Overlay Snackbar to float above player and UI outside Scaffold, in the root Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (vm.isFullscreenVideo) 24.dp else if (vm.playerState == PlayerState.FULL) 24.dp else if (vm.playerState == PlayerState.MINI) 134.dp else 74.dp)
                .padding(horizontal = if (vm.isFullscreenVideo) 24.dp else 16.dp)
                .then(if (!vm.isFullscreenVideo) Modifier.navigationBarsPadding() else Modifier),
            contentAlignment = if (vm.isFullscreenVideo) Alignment.BottomStart else Alignment.BottomCenter
        ) {
            SnackbarHost(
                hostState = snackbarHostState
            ) { data ->
                Snackbar(
                    modifier = Modifier.widthIn(max = 400.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurface,
                    snackbarData = data
                )
            }
        }
    }
}


