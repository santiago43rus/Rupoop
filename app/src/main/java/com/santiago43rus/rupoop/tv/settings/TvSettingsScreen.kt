@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.santiago43rus.rupoop.tv.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
            .clipToBounds()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 32.dp, top = 24.dp, bottom = 24.dp)
    ) {
        item {
            TvSectionTitle(title = "Настройки")
        }

        // 1. ВНЕШНИЙ ВИД
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Внешний вид", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Тема приложения", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                val themes = remember(isEasterUnlocked) {
                    val list = mutableListOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                    if (isEasterUnlocked) list.add("easter" to "Секретная ✨")
                    list
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(themes) { (mode, label) ->
                        val isSelected = themeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                themeMode = mode
                                settingsManager.themeMode = mode
                                onThemeToggle(mode)
                                val updatedRegistry = registryManager.registry.copy(
                                    appSettings = registryManager.registry.appSettings.copy(theme = mode)
                                )
                                registryManager.updateRegistry(updatedRegistry)
                                vm.onRegistryUpdate(updatedRegistry)
                                vm.pushToGitHub()
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary,
                                selectedContainerColor = colorScheme.surfaceVariant,
                                selectedContentColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary,
                                focusedSelectedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. ПЛЕЕР
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Плеер", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Шаг перемотки (в секундах)", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                val durations = listOf(5, 10, 15, 20, 30)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(durations) { dur ->
                        val isSelected = doubleTapDuration == dur
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                doubleTapDuration = dur
                                settingsManager.doubleTapSeekDuration = dur
                                val updatedRegistry = registryManager.registry.copy(
                                    appSettings = registryManager.registry.appSettings.copy(doubleTapSeekDuration = dur)
                                )
                                registryManager.updateRegistry(updatedRegistry)
                                vm.onRegistryUpdate(updatedRegistry)
                                vm.pushToGitHub()
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary,
                                selectedContainerColor = colorScheme.surfaceVariant,
                                selectedContentColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary,
                                focusedSelectedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "$dur сек",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // УВЕДОМЛЕНИЯ О ЗАГРУЗКЕ
        item {
            Surface(
                onClick = {
                    val newVal = !downloadNotifications
                    downloadNotifications = newVal
                    settingsManager.showDownloadNotifications = newVal
                },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurface,
                    focusedContainerColor = colorScheme.primaryContainer,
                    focusedContentColor = colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Уведомления о загрузке", fontSize = 16.sp)
                        Text("Показывать прогресс и статус загрузки файлов", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Switch(checked = downloadNotifications, onCheckedChange = null)
                }
            }
        }

        // 3. ЖАНРЫ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Жанры", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allGenres) { genre ->
                        val isSelected = enabledGenres.value.contains(genre)
                        FilterChip(
                            selected = isSelected,
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
                                vm.onRegistryUpdate(updatedRegistry)
                                vm.pushToGitHub()
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary,
                                selectedContainerColor = colorScheme.surfaceVariant,
                                selectedContentColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary,
                                focusedSelectedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = genre.replaceFirstChar { it.uppercase() },
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ЗАГРУЗКА
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Загрузка", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Качество видео для скачивания", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                val qualities = listOf("144", "240", "360", "480", "720", "1080", "1440", "2160")
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(qualities) { q ->
                        val isSelected = downloadQuality == q
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                downloadQuality = q
                                settingsManager.downloadQuality = q
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary,
                                selectedContainerColor = colorScheme.surfaceVariant,
                                selectedContentColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary,
                                focusedSelectedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "${q}p",
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Максимум одновременных загрузок", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                val limits = listOf(1, 2, 3)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(limits) { limit ->
                        val isSelected = maxDownloads == limit
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                maxDownloads = limit
                                settingsManager.maxConcurrentDownloads = limit
                                com.santiago43rus.rupoop.service.DownloadService.updateConcurrency(context)
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary,
                                selectedContainerColor = colorScheme.surfaceVariant,
                                selectedContentColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary,
                                focusedSelectedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "$limit",
                                fontSize = 14.sp
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
                        focusedContainerColor = colorScheme.primaryContainer,
                        focusedContentColor = colorScheme.onPrimaryContainer
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
                    Button(
                        onClick = onOpenLogin,
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Войти через GitHub (Device Flow)")
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
                            onClick = {
                                settingsManager.cachedGistId = null
                                vm.showSnackbar("ID Gist сброшен")
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) { Text("Сбросить") }

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
                    Text("Периодичность (в часах)", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    val intervals = listOf(1, 6, 12, 24)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(intervals) { hours ->
                            val isSelected = syncFreq == hours
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    syncFreq = hours
                                    settingsManager.syncFrequencyHours = hours
                                    val updatedRegistry = registryManager.registry.copy(
                                        appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = hours)
                                    )
                                    registryManager.updateRegistry(updatedRegistry)
                                    vm.onRegistryUpdate(updatedRegistry)
                                    vm.pushToGitHub()
                                },
                                colors = FilterChipDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary,
                                    selectedContainerColor = colorScheme.surfaceVariant,
                                    selectedContentColor = colorScheme.primary,
                                    focusedSelectedContainerColor = colorScheme.primary,
                                    focusedSelectedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "$hours ч",
                                    fontSize = 14.sp
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
                        focusedContainerColor = colorScheme.primaryContainer,
                        focusedContentColor = colorScheme.onPrimaryContainer
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
                            focusedContainerColor = colorScheme.primaryContainer,
                            focusedContentColor = colorScheme.onPrimaryContainer
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

        // 9. О ПРИЛОЖЕНИИ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О приложении", color = colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = {},
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
