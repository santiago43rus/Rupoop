package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.santiago43rus.rupoop.BuildConfig
import com.santiago43rus.rupoop.util.*
import com.santiago43rus.rupoop.AppViewModel
import androidx.compose.ui.Alignment

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
    var syncFreq by remember { mutableStateOf(settingsManager.syncFrequencyHours.toString()) }
    var cacheSize by remember { mutableStateOf(getCacheSize(context)) }
    var themeMode by remember { mutableStateOf(settingsManager.themeMode) }
    var selectedIcon by remember { mutableStateOf(settingsManager.appIcon) }

    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы"
    )
    var enabledGenres by remember { mutableStateOf(settingsManager.enabledGenres) }

    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        item {
            // ── Внешний вид ──
            Text("Внешний вид", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            
            Text("Тема приложения", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val themes = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                themes.forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            onThemeToggle(mode)
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            if (themeMode == mode) Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                        }
                    )
                }
            }
            
            // Icon selection
            Text("Иконка приложения", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icons = listOf("system" to "Системная", "default" to "Светлая", "dark" to "Тёмная")
                icons.forEach { (iconId, label) ->
                    FilterChip(
                        selected = selectedIcon == iconId,
                        onClick = {
                            selectedIcon = iconId
                            settingsManager.appIcon = iconId
                            switchAppIcon(context, iconId)
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            if (selectedIcon == iconId) Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                        }
                    )
                }
            }
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Плеер ──
            Text("Плеер", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))

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
                        label = { Text("${seconds}с") }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Уведомления ──
            Text("Уведомления", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
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
                modifier = Modifier.clickable { onOpenNotificationSettings() }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Жанры ──
            Text("Жанры", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Загрузка ──
            Text("Загрузка", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            Text("Качество видео для скачивания", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceEvenly) {
                listOf("360", "480", "720", "1080").forEach { q ->
                    FilterChip(selected = downloadQuality == q, onClick = {
                        downloadQuality = q; settingsManager.downloadQuality = q
                        registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(downloadQuality = q)))
                        vm.onRegistryUpdate(registryManager.registry)
                    }, label = { Text(q + "p") })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Кэш и история ──
            CacheAndHistorySection(
                context = context,
                cacheSize = cacheSize,
                onClearCache = {
                    clearAppCache(context)
                    cacheSize = getCacheSize(context)
                },
                onClearWatchHistory = {
                    registryManager.clearWatchHistory()
                    vm.onRegistryUpdate(registryManager.registry)
                },
                onClearSearchHistory = {
                    registryManager.clearSearchHistory()
                    vm.onRegistryUpdate(registryManager.registry)
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Контент и рекомендации ──
            Text("Контент и рекомендации", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Скрытые и неинтересные видео") },
                supportingContent = { Text("Управление дизлайками и скрытыми видео") },
                leadingContent = { Icon(Icons.Default.VisibilityOff, null, tint = Color.Gray) },
                modifier = Modifier.clickable { onShowHiddenVideos() }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── GitHub Синхронизация ──
            Text("GitHub Синхронизация", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
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

                            Spacer(Modifier.width(4.dp))

                            TextButton(
                                onClick = {
                                    val nextHours = 24
                                    settingsManager.syncFrequencyHours = nextHours
                                    syncFreq = "24"
                                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = nextHours)))
                                    vm.onRegistryUpdate(registryManager.registry)
                                }
                            ) {
                                Text("Сбросить", color = Color(0xFFE53935))
                            }
                        }
                    }
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Поддержать автора и О приложении ──
            SupportAndAboutSection(
                uriHandler = uriHandler,
                donateUrl = BuildConfig.DONATE_URL,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
