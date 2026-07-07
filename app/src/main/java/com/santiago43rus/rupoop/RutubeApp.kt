package com.santiago43rus.rupoop

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.santiago43rus.rupoop.components.AppTopBar
import com.santiago43rus.rupoop.components.ContentSelectionDialog
import com.santiago43rus.rupoop.screen.*
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationResponse

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

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val response = AuthorizationResponse.fromIntent(result.data!!)
            response?.let { vm.processAuthResponse(it) }
        }
    }
    
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    val homeListState = rememberLazyListState()
    val subsListState = rememberLazyListState()
    val libListState = rememberLazyListState()

    val searchState = rememberTextFieldState(vm.searchQuery)

    LaunchedEffect(vm.searchQuery) {
        if (searchState.text.toString() != vm.searchQuery) {
            searchState.edit { replace(0, length, vm.searchQuery) }
        }
    }

    LaunchedEffect(searchState.text) {
        val newText = searchState.text.toString()
        if (newText != vm.searchQuery) {
            vm.updateSearchQuery(newText)
            vm.isSearchExpanded = true
        }
    }

    LaunchedEffect(vm.isPlaying, vm.currentVideo) {
        if (vm.isPlaying && vm.currentVideo != null) vm.startProgressSaving()
        else vm.stopProgressSaving()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !vm.isBackgroundPlaybackEnabled) {
                vm.exoPlayer.pause()
                vm.isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(vm.isSettingsVisible) {
        if (vm.isSettingsVisible && vm.isPlaying && !vm.isBackgroundPlaybackEnabled) {
            vm.exoPlayer.pause()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(deepLinkVideoUrl) {
        if (deepLinkVideoUrl != null) {
            vm.handleDeepLink(deepLinkVideoUrl)
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(Unit) { vm.initializeApp() }
    LaunchedEffect(vm.currentNav) {
        if (vm.currentNav == NavItem.SUBSCRIPTIONS) vm.loadSubscriptions(false)
        if (vm.currentNav == NavItem.HOME && vm.homeVideos.isEmpty()) vm.loadHome(false)
    }

    BackHandler(
        enabled = vm.playerState != PlayerState.CLOSED || vm.isSearchExpanded || vm.isSearchVisible || vm.isAuthorVisible || vm.isSettingsVisible || vm.currentLibSub != LibrarySubScreen.NONE || vm.currentNav != NavItem.HOME
    ) {
        focusManager.clearFocus()
        vm.handleBack()
    }

    if (vm.showOnboarding) {
        ContentSelectionDialog(settingsManager = vm.settingsManager, onDismiss = { vm.dismissOnboarding() })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                val progress = if (vm.playerState == PlayerState.FULL) 0f else 1f
                RutubeBottomBar(
                    currentNav = vm.currentNav,
                    onNavChange = { vm.currentNav = it },
                    isSettingsVisible = vm.isSettingsVisible,
                    isSearchExpanded = vm.isSearchExpanded,
                    isSearchVisible = vm.isSearchVisible,
                    isAuthorVisible = vm.isAuthorVisible,
                    currentLibSub = vm.currentLibSub,
                    onResetOverlays = {
                        vm.isSettingsVisible = false
                        vm.isSearchExpanded = false
                        vm.clearCurrentSearchStack()
                        vm.isAuthorVisible = false
                        if (vm.currentNav == NavItem.LIBRARY) vm.currentLibSub = LibrarySubScreen.NONE
                    },
                    onScrollHome = { scope.launch { homeListState.animateScrollToItem(0) } },
                    onScrollSubs = { scope.launch { subsListState.animateScrollToItem(0) } },
                    onScrollLib = { scope.launch { libListState.animateScrollToItem(0) } },
                    progress = progress,
                    isFullscreenVideo = vm.isFullscreenVideo
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (vm.isFullscreenVideo) 0f else if (vm.playerState == PlayerState.CLOSED) 1f else vm.playerTransitionProgress }.padding(bottom = if (vm.playerState == PlayerState.FULL && vm.isFullscreenVideo) 0.dp else padding.calculateBottomPadding())) {
                    if (!vm.isFullscreenVideo && !vm.isHiddenVideosVisible && !vm.isNotificationSettingsVisible) {
                        AppTopBar(vm = vm, searchState = searchState, focusManager = focusManager, authLauncher = authLauncher, onSearch = {
                            scope.launch { homeListState.scrollToItem(0) }
                        })
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = vm.currentNav,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
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
                    }

                    SearchSuggestionsOverlay(
                        isSearchExpanded = vm.isSearchExpanded,
                        searchQuery = vm.searchQuery,
                        onSearchQueryChange = { vm.searchQuery = it },
                        searchSuggestions = vm.searchSuggestions,
                        searchHistory = vm.userRegistry.searchHistory,
                        onPerformSearch = {
                            focusManager.clearFocus()
                            vm.performSearch(it)
                            scope.launch { homeListState.scrollToItem(0) }
                        },
                        onRemoveSearchQuery = { vm.removeSearchQuery(it) }
                    )

                    RutubeAppOverlays(
                        vm = vm,
                        onThemeToggle = onThemeToggle,
                        context = context
                    )
                }

                RutubePlayerContainer(
                    vm = vm,
                    padding = padding
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (vm.isFullscreenVideo) 24.dp else if (vm.playerState == PlayerState.FULL) 24.dp else if (vm.playerState == PlayerState.MINI) 134.dp else 74.dp)
                .padding(horizontal = if (vm.isFullscreenVideo) 24.dp else 16.dp)
                .then(if (!vm.isFullscreenVideo) Modifier.navigationBarsPadding() else Modifier),
            contentAlignment = if (vm.isFullscreenVideo) Alignment.BottomStart else Alignment.BottomCenter
        ) {
            SnackbarHost(hostState = snackbarHostState) { data ->
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
