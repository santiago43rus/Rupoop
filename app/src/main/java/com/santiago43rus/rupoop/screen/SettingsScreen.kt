package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.santiago43rus.rupoop.BuildConfig
import com.santiago43rus.rupoop.util.*
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.clearWatchHistory
import com.santiago43rus.rupoop.clearSearchHistory
import com.santiago43rus.rupoop.components.DeleteConfirmationDialog
import com.santiago43rus.rupoop.isAuthenticated
import androidx.compose.ui.Alignment

@androidx.media3.common.util.UnstableApi
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onThemeToggle: (String) -> Unit,
    onShowHiddenVideos: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onNotificationsChanged: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settingsManager = vm.settingsManager
    val registryManager = vm.registryManager

    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var concurrentDownloads by remember { mutableIntStateOf(settingsManager.maxConcurrentDownloads.coerceIn(1, 4)) }
    var syncFreq by remember { mutableStateOf(settingsManager.syncFrequencyHours.toString()) }
    var cacheSize by remember { mutableStateOf(getCacheSize(context)) }
    var themeMode by remember { mutableStateOf(settingsManager.themeMode) }
    var selectedIcon by remember { mutableStateOf(settingsManager.appIcon) }
    var isEasterUnlocked by remember { mutableStateOf(settingsManager.isEasterEggUnlocked) }

    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы"
    )
    var enabledGenres by remember { mutableStateOf(settingsManager.enabledGenres) }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.exportBackupToUri(it) }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.importBackupFromUri(it) {
                themeMode = settingsManager.themeMode
                selectedIcon = settingsManager.appIcon
                downloadQuality = settingsManager.downloadQuality
                concurrentDownloads = settingsManager.maxConcurrentDownloads.coerceIn(1, 4)
                syncFreq = settingsManager.syncFrequencyHours.toString()
                enabledGenres = settingsManager.enabledGenres
                onThemeToggle(settingsManager.themeMode)
            }
        }
    }

    var versionClicks by remember { mutableIntStateOf(0) }
    var lastVersionClickTime by remember { mutableLongStateOf(0L) }
    var toastRef by remember { mutableStateOf<android.widget.Toast?>(null) }

    var pendingClearAction by remember { mutableStateOf<((deleteFromGist: Boolean) -> Unit)?>(null) }
    var clearDialogTitle by remember { mutableStateOf("") }
    var clearDialogMessage by remember { mutableStateOf("") }
    var clearDialogShowGist by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        item {
            // ── Внешний вид ──
            Text("Внешний вид", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Text("Тема приложения", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val themes = remember(isEasterUnlocked) {
                    val list = mutableListOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                    if (isEasterUnlocked) {
                        list.add("easter" to "Секретная ✨")
                    }
                    list
                }
                themes.forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            onThemeToggle(mode)
                            registryManager.updateRegistry(registryManager.registry.copy(
                                appSettings = registryManager.registry.appSettings.copy(theme = mode)
                            ))
                            vm.onRegistryUpdate(registryManager.registry)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        label = { Text(label) },
                        leadingIcon = {
                            if (themeMode == mode) Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                        }
                    )
                }
            }
            
            // Icon selection
            Text("Иконка приложения", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val icons = remember(isEasterUnlocked) {
                    val list = mutableListOf("system" to "Системная", "default" to "Светлая", "dark" to "Тёмная")
                    if (isEasterUnlocked) {
                        list.add("easter" to "Секретная ✨")
                    }
                    list
                }
                icons.forEach { (iconId, label) ->
                    FilterChip(
                        selected = selectedIcon == iconId,
                        onClick = {
                            selectedIcon = iconId
                            settingsManager.appIcon = iconId
                            switchAppIcon(context, iconId)
                            registryManager.updateRegistry(registryManager.registry.copy(
                                appSettings = registryManager.registry.appSettings.copy(appIcon = iconId)
                            ))
                            vm.onRegistryUpdate(registryManager.registry)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        label = { Text(label) },
                        leadingIcon = {
                            if (selectedIcon == iconId) Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                        }
                    )
                }
            }
            
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Плеер ──
            Text("Плеер", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            var pipEnabled by remember { mutableStateOf(settingsManager.pipEnabled) }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Картинка в картинке (PiP)") },
                    supportingContent = { Text("Воспроизведение в плавающем окне при выходе из приложения и кнопка в плеере") },
                    leadingContent = { Icon(Icons.Default.PictureInPictureAlt, null, tint = Color.Gray) },
                    trailingContent = {
                        Switch(
                            checked = pipEnabled,
                            onCheckedChange = { checked ->
                                pipEnabled = checked
                                settingsManager.pipEnabled = checked
                                registryManager.updateRegistry(registryManager.registry.copy(
                                    appSettings = registryManager.registry.appSettings.copy(pipEnabled = checked)
                                ))
                                vm.onRegistryUpdate(registryManager.registry)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            var doubleTapSeek by remember { mutableIntStateOf(settingsManager.doubleTapSeekDuration) }
            Text("Время перемотки (двойное касание)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { seconds ->
                    FilterChip(
                        selected = doubleTapSeek == seconds,
                        onClick = {
                            doubleTapSeek = seconds
                            settingsManager.doubleTapSeekDuration = seconds
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        ),
                        label = { Text("${seconds}с") }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Уведомления ──
            Text("Уведомления", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Настройки уведомлений") },
                    supportingContent = { Text("Управление уведомлениями загрузки и воспроизведения") },
                    leadingContent = { Icon(Icons.Default.Notifications, null, tint = Color.Gray) },
                    trailingContent = {
                        val isAllOn = vm.showDownloadNotifications || vm.showBackgroundNotifications
                        Box(modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consumes click, stops propagation to ListItem
                        )) {
                            Switch(
                                checked = isAllOn,
                                onCheckedChange = { checked ->
                                    vm.updateDownloadNotifications(checked)
                                    vm.updateBackgroundNotifications(checked)
                                    onNotificationsChanged()
                                }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenNotificationSettings() }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Жанры ──
            Text("Жанры", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Выберите жанры для ленты рекомендаций", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                allGenres.forEach { genre ->
                    val selected = genre in enabledGenres
                    FilterChip(
                        selected = selected,
                        onClick = {
                            enabledGenres = if (selected) enabledGenres - genre else enabledGenres + genre
                            settingsManager.enabledGenres = enabledGenres
                            registryManager.updateRegistry(registryManager.registry.copy(
                                appSettings = registryManager.registry.appSettings.copy(enabledGenres = enabledGenres.toList())
                            ))
                            vm.onRegistryUpdate(registryManager.registry)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        label = { Text(genre.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Загрузка ──
            Text("Загрузка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Качество видео для скачивания", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        listOf("360", "480", "720", "1080").forEach { q ->
                            FilterChip(
                                selected = downloadQuality == q,
                                onClick = {
                                    downloadQuality = q
                                    settingsManager.downloadQuality = q
                                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(downloadQuality = q)))
                                    vm.onRegistryUpdate(registryManager.registry)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White,
                                    selectedLabelColor = Color.Black
                                ),
                                label = { Text(q + "p") }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Количество одновременных загрузок", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (concurrentDownloads > 1) {
                                    val next = concurrentDownloads - 1
                                    concurrentDownloads = next
                                    settingsManager.maxConcurrentDownloads = next
                                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(maxConcurrentDownloads = next)))
                                    vm.onRegistryUpdate(registryManager.registry)
                                    com.santiago43rus.rupoop.service.DownloadService.updateConcurrency(context)
                                }
                            },
                            enabled = concurrentDownloads > 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, "Меньше")
                        }

                        Text(
                            text = "$concurrentDownloads",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                if (concurrentDownloads < 4) {
                                    val next = concurrentDownloads + 1
                                    concurrentDownloads = next
                                    settingsManager.maxConcurrentDownloads = next
                                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(maxConcurrentDownloads = next)))
                                    vm.onRegistryUpdate(registryManager.registry)
                                    com.santiago43rus.rupoop.service.DownloadService.updateConcurrency(context)
                                }
                            },
                            enabled = concurrentDownloads < 4
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "Больше")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val next = 1
                                concurrentDownloads = next
                                settingsManager.maxConcurrentDownloads = next
                                registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(maxConcurrentDownloads = next)))
                                vm.onRegistryUpdate(registryManager.registry)
                                com.santiago43rus.rupoop.service.DownloadService.updateConcurrency(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Сбросить", color = Color.Black, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Кэш и история ──
            CacheAndHistorySection(
                context = context,
                cacheSize = cacheSize,
                onClearCache = {
                    clearAppCache(context)
                    cacheSize = getCacheSize(context)
                },
                onClearWatchHistory = {
                    clearDialogTitle = "Очистить историю просмотров"
                    clearDialogMessage = "Вы уверены, что хотите полностью очистить историю просмотров?"
                    clearDialogShowGist = vm.isAuthenticated
                    pendingClearAction = { fromGist ->
                        vm.clearWatchHistory(fromGist)
                    }
                },
                onClearSearchHistory = {
                    clearDialogTitle = "Очистить историю поиска"
                    clearDialogMessage = "Вы уверены, что хотите полностью очистить историю поиска?"
                    clearDialogShowGist = vm.isAuthenticated
                    pendingClearAction = { fromGist ->
                        vm.clearSearchHistory(fromGist)
                    }
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Контент и рекомендации ──
            Text("Контент и рекомендации", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Скрытые и неинтересные видео") },
                    supportingContent = { Text("Управление дизлайками и скрытыми видео") },
                    leadingContent = { Icon(Icons.Default.VisibilityOff, null, tint = Color.Gray) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onShowHiddenVideos() }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── GitHub Синхронизация ──
            Text("GitHub Синхронизация", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (vm.isAuthenticated) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.pullFromGist() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Загрузить", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    Button(
                        onClick = { vm.pushToGist() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Выгрузить", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Периодичность (в часах)", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val hoursInt = syncFreq.toIntOrNull() ?: 24

                                IconButton(
                                    onClick = {
                                        if (hoursInt > 1) {
                                            val nextHours = hoursInt - 1
                                            settingsManager.syncFrequencyHours = nextHours
                                            syncFreq = nextHours.toString()
                                            registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = nextHours)))
                                            vm.onRegistryUpdate(registryManager.registry)
                                        }
                                    },
                                    enabled = hoursInt > 1
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Меньше")
                                }

                                Text(
                                    text = "$hoursInt ч",
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        if (hoursInt < 48) {
                                            val nextHours = hoursInt + 1
                                            settingsManager.syncFrequencyHours = nextHours
                                            syncFreq = nextHours.toString()
                                            registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = nextHours)))
                                            vm.onRegistryUpdate(registryManager.registry)
                                        }
                                    },
                                    enabled = hoursInt < 48
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Больше")
                                }

                                Spacer(Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val nextHours = 24
                                        settingsManager.syncFrequencyHours = nextHours
                                        syncFreq = "24"
                                        registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = nextHours)))
                                        vm.onRegistryUpdate(registryManager.registry)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Сбросить", color = Color.Black, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Перенос данных (без аккаунта) ──
            Text("Перенос данных (без аккаунта)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Экспорт и импорт всех настроек, истории, подписок и плейлистов на другой телефон без входа в GitHub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        vm.exportBackupShare(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Поделиться", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
                Button(
                    onClick = {
                        createFileLauncher.launch("rupoop_backup.json")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("В файл", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
            Button(
                onClick = {
                    importFileLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Импортировать из файла", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ── Поддержать автора и О приложении ──
            SupportAndAboutSection(
                uriHandler = uriHandler,
                donateUrl = BuildConfig.DONATE_URL,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                onVersionClick = {
                    if (isEasterUnlocked || settingsManager.isEasterEggUnlocked) {
                        return@SupportAndAboutSection
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastVersionClickTime < 600) {
                        versionClicks++
                    } else {
                        versionClicks = 1
                    }
                    lastVersionClickTime = currentTime

                    if (versionClicks >= 10) {
                        settingsManager.isEasterEggUnlocked = true
                        isEasterUnlocked = true
                        registryManager.updateRegistry(registryManager.registry.copy(
                            appSettings = registryManager.registry.appSettings.copy(isEasterEggUnlocked = true)
                        ))
                        vm.onRegistryUpdate(registryManager.registry)

                        toastRef?.cancel()
                        toastRef = android.widget.Toast.makeText(context, "Пасхалка найдена! Секретная тема и иконка открыты! 🎉", android.widget.Toast.LENGTH_SHORT).apply { show() }
                        versionClicks = 0
                    } else if (versionClicks in 6..9) {
                        toastRef?.cancel()
                        toastRef = android.widget.Toast.makeText(context, "Осталось нажать: ${10 - versionClicks}", android.widget.Toast.LENGTH_SHORT).apply { show() }
                    }
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (pendingClearAction != null) {
        DeleteConfirmationDialog(
            title = clearDialogTitle,
            message = clearDialogMessage,
            showGistCheckbox = clearDialogShowGist,
            confirmButtonText = "Очистить",
            onConfirm = { deleteFromGist ->
                pendingClearAction?.invoke(deleteFromGist)
                pendingClearAction = null
            },
            onDismiss = {
                pendingClearAction = null
            }
        )
    }
}
