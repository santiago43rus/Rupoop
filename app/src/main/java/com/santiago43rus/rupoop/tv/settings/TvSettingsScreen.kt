@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.santiago43rus.rupoop.tv.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.*
import com.santiago43rus.rupoop.tv.components.TvDeleteConfirmationDialog
import com.santiago43rus.rupoop.tv.components.TvSectionTitle
import com.santiago43rus.rupoop.util.clearAppCache
import com.santiago43rus.rupoop.util.formatFileSize
import com.santiago43rus.rupoop.util.getCacheSize

@Composable
fun TvSettingsScreen(
    vm: AppViewModel,
    donateUrl: String,
    versionName: String,
    versionCode: Int,
    onThemeToggle: (String) -> Unit,
    onOpenHiddenVideos: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colorScheme = MaterialTheme.colorScheme
    val settingsManager = vm.settingsManager
    val registryManager = vm.registryManager

    val isEasterUnlocked = settingsManager.isEasterEggUnlocked

    var themeMode by remember { mutableStateOf(settingsManager.themeMode) }
    var doubleTapDuration by remember { mutableIntStateOf(settingsManager.doubleTapSeekDuration) }
    var syncFreq by remember { mutableIntStateOf(settingsManager.syncFrequencyHours) }
    var downloadNotifications by remember { mutableStateOf(settingsManager.showDownloadNotifications) }
    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var maxDownloads by remember { mutableIntStateOf(settingsManager.maxConcurrentDownloads) }
    val enabledGenres = remember { mutableStateOf(settingsManager.enabledGenres) }
    var cacheSize by remember { mutableLongStateOf(getCacheSize(context)) }

    LaunchedEffect(vm.userRegistry.appSettings) {
        themeMode = settingsManager.themeMode
        doubleTapDuration = settingsManager.doubleTapSeekDuration
        syncFreq = settingsManager.syncFrequencyHours
        downloadNotifications = settingsManager.showDownloadNotifications
        downloadQuality = settingsManager.downloadQuality
        maxDownloads = settingsManager.maxConcurrentDownloads
        enabledGenres.value = settingsManager.enabledGenres
    }

    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 44.dp, end = 28.dp, top = 24.dp, bottom = 24.dp)
    ) {
        item {
            TvSectionTitle(title = "Настройки", modifier = Modifier.padding(start = 4.dp))
        }

        // 1. ВНЕШНИЙ ВИД
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Внешний вид", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                Text("Тема приложения", color = colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                val currentThemeMode = if (themeMode == "system") "dark" else themeMode
                val themes = remember(isEasterUnlocked) {
                    val list = mutableListOf("dark" to "Тёмная", "light" to "Светлая")
                    if (isEasterUnlocked) list.add("easter" to "Секретная ✨")
                    list
                }
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    firstItemFocusRequester.requestFocus()
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(themes) { index, (mode, label) ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            onClick = {
                                themeMode = mode
                                settingsManager.themeMode = mode
                                onThemeToggle(mode)
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                focusedContentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. ПЛЕЕР
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Плеер", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                
                var autoPlayNext by remember { mutableStateOf(settingsManager.autoPlayNext) }
                Surface(
                    onClick = {
                        val newValue = !autoPlayNext
                        autoPlayNext = newValue
                        settingsManager.autoPlayNext = newValue
                        val updatedRegistry = registryManager.registry.copy(
                            appSettings = registryManager.registry.appSettings.copy(autoPlayNext = newValue)
                        )
                        registryManager.updateRegistry(updatedRegistry)
                        vm.onRegistryUpdate(updatedRegistry, forceDirect = true)
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        focusedContentColor = colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Автовоспроизведение", fontSize = 16.sp)
                            Text(
                                if (autoPlayNext) "Включено (следующее видео/серия запускается автоматически)" else "Выключено",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = autoPlayNext,
                            onCheckedChange = null
                        )
                    }
                }

                Text("Шаг перемотки (в секундах)", color = colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                val durations = listOf(5, 10, 15, 20, 30)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(durations) { dur ->
                        val isSelected = doubleTapDuration == dur
                        Surface(
                            onClick = {
                                doubleTapDuration = dur
                                settingsManager.doubleTapSeekDuration = dur
                                val updatedRegistry = registryManager.registry.copy(
                                    appSettings = registryManager.registry.appSettings.copy(doubleTapSeekDuration = dur)
                                )
                                registryManager.updateRegistry(updatedRegistry)
                                vm.onRegistryUpdate(updatedRegistry, forceDirect = true)
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                focusedContentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Text(
                                text = "$dur сек",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                var autoPlayDelay by remember { mutableIntStateOf(settingsManager.autoPlayDelaySeconds) }
                if (autoPlayNext) {
                    Text("Задержка автовоспроизведения", color = colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    val delays = listOf(0 to "Сразу", 3 to "3 сек", 5 to "5 сек", 10 to "10 сек")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(delays) { (sec, label) ->
                            val isSelected = autoPlayDelay == sec
                            Surface(
                                onClick = {
                                    autoPlayDelay = sec
                                    settingsManager.autoPlayDelaySeconds = sec
                                    val updatedRegistry = registryManager.registry.copy(
                                        appSettings = registryManager.registry.appSettings.copy(autoPlayDelaySeconds = sec)
                                    )
                                    registryManager.updateRegistry(updatedRegistry)
                                    vm.onRegistryUpdate(updatedRegistry, forceDirect = true)
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                    focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                    focusedContentColor = androidx.compose.ui.graphics.Color.White
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. ЖАНРЫ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Жанры", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allGenres) { genre ->
                        val isSelected = enabledGenres.value.contains(genre)
                        Surface(
                            onClick = {
                                val updated = if (isSelected) {
                                    if (enabledGenres.value.size > 1) enabledGenres.value - genre else enabledGenres.value
                                } else {
                                    enabledGenres.value + genre
                                }
                                enabledGenres.value = updated
                                settingsManager.enabledGenres = updated
                                val updatedRegistry = registryManager.registry.copy(
                                    appSettings = registryManager.registry.appSettings.copy(enabledGenres = updated.toList())
                                )
                                registryManager.updateRegistry(updatedRegistry)
                                vm.onRegistryUpdate(updatedRegistry, forceDirect = true)
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                focusedContentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Text(
                                text = genre.replaceFirstChar { it.uppercase() },
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. КОНТЕНТ И РЕКОМЕНДАЦИИ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Контент и рекомендации", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = onOpenHiddenVideos,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        focusedContentColor = colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Скрытые и неинтересные видео", fontSize = 16.sp)
                            Text("Управление видео, исключенными из ленты (${vm.userRegistry.hiddenVideosList.size})", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        Text("Открыть", color = colorScheme.primary, fontSize = 15.sp)
                    }
                }
            }
        }

        // 5. GITHUB СИНХРОНИЗАЦИЯ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHub Синхронизация", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!vm.isAuthenticated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onOpenLogin,
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                                focusedContainerColor = colorScheme.surfaceVariant,
                                focusedContentColor = colorScheme.onSurface
                            )
                        ) {
                            Text("Войти через GitHub (Device Flow)")
                        }

                        if (vm.isAuthenticating) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary
                                )
                                Text("Выполняется вход...", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { vm.pullFromGist() },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) { Text("Загрузить") }

                        Button(
                            onClick = { vm.pushToGist() },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) { Text("Выгрузить") }

                        Button(
                            onClick = { vm.logout() },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) { Text("Выйти") }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Периодичность (в часах)", color = colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                    val intervals = listOf(1, 6, 12, 24)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(intervals) { hours ->
                            val isSelected = syncFreq == hours
                            Surface(
                                onClick = {
                                    syncFreq = hours
                                    settingsManager.syncFrequencyHours = hours
                                    val updatedRegistry = registryManager.registry.copy(
                                        appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = hours)
                                    )
                                    registryManager.updateRegistry(updatedRegistry)
                                    vm.onRegistryUpdate(updatedRegistry, forceDirect = true)
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.onSurface else colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) colorScheme.surface else colorScheme.onSurface,
                                    focusedContainerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF333333) else com.santiago43rus.rupoop.theme.RupoopRed,
                                    focusedContentColor = androidx.compose.ui.graphics.Color.White
                                )
                            ) {
                                Text(
                                    text = "$hours ч",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. КЭШ И ХРАНИЛИЩЕ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Кэш и хранилище", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = {
                        clearAppCache(context)
                        cacheSize = getCacheSize(context)
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        focusedContentColor = colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Размер кэша", fontSize = 16.sp)
                            Text(formatFileSize(cacheSize), color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        Text("Очистить кэш", color = colorScheme.primary, fontSize = 15.sp)
                    }
                }
            }
        }

        // 7. ИСТОРИЯ И КОНФИДЕНЦИАЛЬНОСТЬ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("История и конфиденциальность", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showClearWatchHistoryDialog = true },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Очистить историю просмотра")
                    }
                    Button(
                        onClick = { showClearSearchHistoryDialog = true },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Очистить историю поиска")
                    }
                }
            }
        }

        // 8. ПОДДЕРЖАТЬ АВТОРА
        if (donateUrl.isNotBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Поддержать автора", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        onClick = {
                            try {
                                uriHandler.openUri(donateUrl)
                            } catch (_: Exception) {}
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        focusedContentColor = colorScheme.onSurface
                    ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Поддержать рублём", fontSize = 16.sp)
                            Text("CloudTips — быстрый перевод", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 8.5. СИНХРОНИЗАЦИЯ ПО WI-FI
        item { TvLanSection(vm) }

        // 9. О ПРИЛОЖЕНИИ
        item {
            var versionClicks by remember { mutableIntStateOf(0) }
            var lastVersionClickTime by remember { mutableLongStateOf(0L) }
            val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О приложении", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = {
                        if (settingsManager.isEasterEggUnlocked) return@Surface

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastVersionClickTime < 600) {
                            versionClicks++
                        } else {
                            versionClicks = 1
                        }
                        lastVersionClickTime = currentTime

                        if (versionClicks >= 10) {
                            settingsManager.isEasterEggUnlocked = true
                            val updatedRegistry = registryManager.registry.copy(
                                appSettings = registryManager.registry.appSettings.copy(isEasterEggUnlocked = true)
                            )
                            registryManager.updateRegistry(updatedRegistry)
                            vm.onRegistryUpdate(updatedRegistry, forceDirect = true)

                            toastRef.value?.cancel()
                            toastRef.value = android.widget.Toast.makeText(context, "Пасхалка найдена! Секретная тема открыта! 🎉", android.widget.Toast.LENGTH_SHORT).apply { show() }
                            versionClicks = 0
                        } else if (versionClicks in 6..9) {
                            toastRef.value?.cancel()
                            toastRef.value = android.widget.Toast.makeText(context, "Осталось нажать: ${10 - versionClicks}", android.widget.Toast.LENGTH_SHORT).apply { show() }
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        focusedContentColor = colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Версия: $versionName ($versionCode)", fontSize = 15.sp)
                        Text("Telegram: t.me/rupoopapp", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("Исходный код: github.com/santiago43rus/Rupoop", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("Лицензия: MIT License", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showClearWatchHistoryDialog) {
        TvDeleteConfirmationDialog(
            title = "Очистить историю просмотров",
            message = "Вы уверены, что хотите полностью очистить историю просмотров?",
            confirmButtonText = "Очистить",
            onConfirm = {
                vm.clearWatchHistory()
                showClearWatchHistoryDialog = false
            },
            onDismiss = { showClearWatchHistoryDialog = false }
        )
    }

    if (showClearSearchHistoryDialog) {
        TvDeleteConfirmationDialog(
            title = "Очистить историю поиска",
            message = "Вы уверены, что хотите полностью очистить историю поиска?",
            confirmButtonText = "Очистить",
            onConfirm = {
                vm.clearSearchHistory()
                showClearSearchHistoryDialog = false
            },
            onDismiss = { showClearSearchHistoryDialog = false }
        )
    }
}
