package com.santiago43rus.rupoop.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.SearchSource
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TvNavSection(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Default.Home),
    SEARCH("Поиск", Icons.Default.Search),
    LIBRARY("Библиотека", Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS("Настройки", Icons.Default.Settings)
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp(
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(TvNavSection.HOME) }

    // Полноэкранный плеер
    if (vm.playerState == PlayerState.FULL) {
        TvPlayerScreen(
            vm = vm,
            onClose = {
                vm.playerState = PlayerState.CLOSED
                vm.exoPlayer.pause()
            }
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        // Левый Navigation Drawer / Rail для TV
        NavigationDrawer(
            drawerContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 24.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvNavSection.entries.forEach { section ->
                        NavigationDrawerItem(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            leadingContent = {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    tint = if (selectedSection == section) Color(0xFF58A6FF) else Color(0xFF8B949E)
                                )
                            }
                        ) {
                            Text(
                                text = section.title,
                                color = if (selectedSection == section) Color.White else Color(0xFF8B949E),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 20.dp) // Безопасные отступы Overscan ~5%
            ) {
                Crossfade(targetState = selectedSection, label = "TvNavTransition") { section ->
                    when (section) {
                        TvNavSection.HOME -> TvHomeScreen(
                            vm = vm,
                            onVideoSelected = { video, list ->
                                vm.playVideo(video, list)
                                vm.playerState = PlayerState.FULL
                            }
                        )
                        TvNavSection.SEARCH -> TvSearchScreen(
                            vm = vm,
                            onVideoSelected = { video, list ->
                                vm.playVideo(video, list)
                                vm.playerState = PlayerState.FULL
                            }
                        )
                        TvNavSection.LIBRARY -> TvLibraryScreen(
                            vm = vm,
                            onVideoSelected = { video, list ->
                                vm.playVideo(video, list)
                                vm.playerState = PlayerState.FULL
                            }
                        )
                        TvNavSection.SETTINGS -> TvSettingsScreen(vm = vm)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TvHomeScreen(
    vm: AppViewModel,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit
) {
    LaunchedEffect(Unit) {
        if (vm.homeVideos.isEmpty()) {
            vm.loadHome(false)
        }
    }

    val feedVideos = vm.homeVideos
    val historyItems = vm.userRegistry.watchHistory
    val likedItems = vm.userRegistry.likedVideos

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Ряд 1: История просмотра
        if (historyItems.isNotEmpty()) {
            item {
                Text(
                    text = "История просмотра",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val historyVideos = remember(historyItems) {
                    historyItems.map { item ->
                        SearchResult(
                            videoUrl = item.videoUrl,
                            title = item.title ?: "",
                            thumbnailUrl = item.thumbnailUrl,
                            author = Author(
                                id = item.authorId,
                                name = item.authorName ?: "",
                                avatarUrl = item.authorAvatarUrl
                            ),
                            duration = (item.totalDuration / 1000).toInt()
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(historyVideos) { video ->
                        TvVideoCard(
                            video = video,
                            onClick = { onVideoSelected(video, historyVideos) }
                        )
                    }
                }
            }
        }

        // Ряд 2: Главная лента / Рекомендации
        item {
            Text(
                text = "Рекомендации",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (feedVideos.isEmpty()) {
                Text(text = "Загрузка рекомендаций...", color = Color.Gray, fontSize = 14.sp)
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(feedVideos) { video ->
                        TvVideoCard(
                            video = video,
                            onClick = { onVideoSelected(video, feedVideos) }
                        )
                    }
                }
            }
        }

        // Ряд 3: Понравившиеся видео
        if (likedItems.isNotEmpty()) {
            item {
                Text(
                    text = "Понравившиеся видео",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(likedItems) { video ->
                        TvVideoCard(
                            video = video,
                            onClick = { onVideoSelected(video, likedItems) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvLibraryScreen(
    vm: AppViewModel,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit
) {
    val historyItems = vm.userRegistry.watchHistory
    val watchLaterItems = vm.userRegistry.watchLater
    val likedItems = vm.userRegistry.likedVideos

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "История",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (historyItems.isEmpty()) {
                Text(text = "История пуста", color = Color.Gray, fontSize = 14.sp)
            } else {
                val historyVideos = remember(historyItems) {
                    historyItems.map { item ->
                        SearchResult(
                            videoUrl = item.videoUrl,
                            title = item.title ?: "",
                            thumbnailUrl = item.thumbnailUrl,
                            author = Author(
                                id = item.authorId,
                                name = item.authorName ?: "",
                                avatarUrl = item.authorAvatarUrl
                            ),
                            duration = (item.totalDuration / 1000).toInt()
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(historyVideos) { video ->
                        TvVideoCard(video = video, onClick = { onVideoSelected(video, historyVideos) })
                    }
                }
            }
        }

        if (watchLaterItems.isNotEmpty()) {
            item {
                Text(
                    text = "Смотреть позже",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(watchLaterItems) { video ->
                        TvVideoCard(video = video, onClick = { onVideoSelected(video, watchLaterItems) })
                    }
                }
            }
        }

        if (likedItems.isNotEmpty()) {
            item {
                Text(
                    text = "Понравившиеся",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(likedItems) { video ->
                        TvVideoCard(video = video, onClick = { onVideoSelected(video, likedItems) })
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    vm: AppViewModel,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit
) {
    var queryText by remember { mutableStateOf(vm.searchQuery) }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Поле ввода поискового запроса для ТВ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    vm.updateSearchQuery(it)
                },
                label = { Text("Поиск видео", color = Color(0xFF8B949E)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        vm.performSearch(queryText)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = Color(0xFF30363D)
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            focusManager.clearFocus()
                            vm.performSearch(queryText)
                            true
                        } else false
                    }
            )

            // Кнопка поиска
            Button(
                onClick = {
                    focusManager.clearFocus()
                    vm.performSearch(queryText)
                },
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF238636),
                    focusedContainerColor = Color(0xFF2EA043)
                )
            ) {
                Text("Искать", color = Color.White)
            }
        }

        // Фильтры по источникам (Rutube, VK, OK, Lordfilm)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(SearchSource.entries) { source ->
                val isSelected = vm.selectedSearchSource == source
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        vm.selectSearchSource(source)
                        if (queryText.isNotBlank()) {
                            vm.performSearch(queryText)
                        }
                    },
                    colors = FilterChipDefaults.colors(
                        containerColor = Color(0xFF1E2228),
                        focusedContainerColor = Color(0xFF282E38),
                        selectedContainerColor = Color(0xFF58A6FF),
                        focusedSelectedContainerColor = Color(0xFF79B8FF)
                    )
                ) {
                    Text(
                        text = source.displayName,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Результаты поиска
        val results = vm.searchResults
        if (vm.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Поиск видео...", color = Color.Gray, fontSize = 16.sp)
            }
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val chunkedResults = results.chunked(4)
                items(chunkedResults) { rowVideos ->
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(rowVideos) { video ->
                            TvVideoCard(
                                video = video,
                                onClick = { onVideoSelected(video, results) }
                            )
                        }
                    }
                }
            }
        } else if (queryText.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(vm: AppViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Настройки", color = Color.White, fontSize = 24.sp)
        }

        item {
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E2228),
                    focusedContainerColor = Color(0xFF282E38)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Автовоспроизведение следующего видео", color = Color.White, fontSize = 16.sp)
                        Text("Автоматически воспроизводить следующее видео из очереди", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = vm.settingsManager.autoPlayNext,
                        onCheckedChange = { vm.settingsManager.autoPlayNext = it }
                    )
                }
            }
        }

        item {
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E2228),
                    focusedContainerColor = Color(0xFF282E38)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Контент для взрослых (18+)", color = Color.White, fontSize = 16.sp)
                        Text("Отображение контента с возрастным ограничением", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = vm.settingsManager.adultContentEnabled,
                        onCheckedChange = { vm.settingsManager.adultContentEnabled = it }
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    vm: AppViewModel,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = coroutineScope.launch {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        scheduleAutoHide()
    }

    BackHandler {
        if (showControls) {
            showControls = false
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                            if (vm.isPlaying) {
                                vm.exoPlayer.pause()
                            } else {
                                vm.exoPlayer.play()
                            }
                            showControls = true
                            scheduleAutoHide()
                            true
                        }
                        Key.DirectionLeft, Key.MediaRewind -> {
                            val newPos = (vm.exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            vm.exoPlayer.seekTo(newPos)
                            showControls = true
                            scheduleAutoHide()
                            true
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            val duration = vm.exoPlayer.duration.coerceAtLeast(0L)
                            val newPos = (vm.exoPlayer.currentPosition + 10_000L).coerceAtMost(duration)
                            vm.exoPlayer.seekTo(newPos)
                            showControls = true
                            scheduleAutoHide()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls = !showControls
                            if (showControls) scheduleAutoHide() else autoHideJob?.cancel()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Видеоповерхность
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = vm.exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // OSD Controls Overlay
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .padding(40.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = vm.currentVideo?.title ?: "",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = vm.currentVideo?.author?.name ?: "",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
