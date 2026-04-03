package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.BuildConfig
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.util.*

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onThemeToggle: () -> Unit,
    registryManager: UserRegistryManager,
    onRegistryUpdate: (UserRegistry) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var syncFreq by remember { mutableStateOf(settingsManager.syncFrequencyHours.toString()) }
    var autoPlayNext by remember { mutableStateOf(settingsManager.autoPlayNext) }
    var cacheSize by remember { mutableStateOf(getCacheSize(context)) }
    var selectedIcon by remember { mutableStateOf(settingsManager.appIcon) }

    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы", "музыка"
    )
    var enabledGenres by remember { mutableStateOf(settingsManager.enabledGenres) }

    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        item {
            // ── Внешний вид ──
            Text("Внешний вид", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Тёмная тема") },
                trailingContent = {
                    Switch(
                        checked = settingsManager.isDarkTheme,
                        onCheckedChange = { onThemeToggle() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFE53935))
                    )
                }
            )
            
            // Icon selection
            Text("Иконка приложения", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = selectedIcon == "default",
                    onClick = {
                        selectedIcon = "default"
                        settingsManager.appIcon = "default"
                        switchAppIcon(context, true)
                    },
                    label = { Text("Светлая") },
                    leadingIcon = {
                        Icon(Icons.Default.LightMode, null, modifier = Modifier.size(18.dp))
                    }
                )
                FilterChip(
                    selected = selectedIcon == "light",
                    onClick = {
                        selectedIcon = "light"
                        settingsManager.appIcon = "light"
                        switchAppIcon(context, false)
                    },
                    label = { Text("Тёмная") },
                    leadingIcon = {
                        Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(18.dp))
                    }
                )
            }
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Плеер ──
            Text("Плеер", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Автовоспроизведение") },
                supportingContent = { Text("Автоматически воспроизводить следующее видео") },
                trailingContent = {
                    Switch(
                        checked = autoPlayNext,
                        onCheckedChange = {
                            autoPlayNext = it
                            settingsManager.autoPlayNext = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFE53935))
                    )
                }
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
                            onRegistryUpdate(registryManager.registry)
                        },
                        label = { Text(genre.replaceFirstChar { it.uppercase() }) }
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
                        onRegistryUpdate(registryManager.registry)
                    }, label = { Text(q + "p") })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Кэш ──
            Text("Кэш и хранилище", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Размер кэша") },
                supportingContent = { Text(formatFileSize(cacheSize)) },
                trailingContent = {
                    FilledTonalButton(onClick = {
                        clearAppCache(context)
                        cacheSize = getCacheSize(context)
                    }) {
                        Text("Очистить")
                    }
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── История и конфиденциальность ──
            Text("История и конфиденциальность", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            Button(
                onClick = { registryManager.clearWatchHistory(); onRegistryUpdate(registryManager.registry) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text("Очистить историю просмотра") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { registryManager.clearSearchHistory(); onRegistryUpdate(registryManager.registry) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text("Очистить историю поиска") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── GitHub Синхронизация ──
            Text("GitHub Синхронизация", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Периодичность (в часах)") },
                trailingContent = {
                    TextField(
                        value = syncFreq,
                        onValueChange = { newValue ->
                            syncFreq = newValue
                            newValue.toIntOrNull()?.let { hours ->
                                settingsManager.syncFrequencyHours = hours
                                registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = hours)))
                                onRegistryUpdate(registryManager.registry)
                            }
                        },
                        modifier = Modifier.width(60.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Поддержать автора ──
            Text("Поддержать автора", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            Spacer(Modifier.height(4.dp))
            Text(
                "Если приложение полезно — поддержите разработку ❤️",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))

            // Донат
            if (BuildConfig.DONATE_URL.isNotBlank()) {
                ListItem(
                    headlineContent = { Text("Поддержать рублём") },
                    supportingContent = { Text("CloudTips — быстрый перевод") },
                    leadingContent = { Icon(Icons.Default.CreditCard, null, tint = Color(0xFFFFEB3B)) },
                    modifier = Modifier.clickable {
                        try {
                            val url = BuildConfig.DONATE_URL.trim()
                            // Fix: normalize scheme to lowercase (e.g. "Https" -> "https")
                            val schemeEnd = url.indexOf("://")
                            val normalizedUrl = if (schemeEnd > 0) {
                                url.substring(0, schemeEnd).lowercase() + url.substring(schemeEnd)
                            } else url
                            uriHandler.openUri(normalizedUrl)
                        } catch (_: Exception) { /* Не удалось открыть ссылку */ }
                    }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── О приложении ──
            Text("О приложении", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
            ListItem(
                headlineContent = { Text("Версия") },
                supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                leadingContent = { Icon(Icons.Default.Info, null, tint = Color.Gray) }
            )
            ListItem(
                headlineContent = { Text("Исходный код") },
                supportingContent = { Text("GitHub") },
                leadingContent = { Icon(Icons.Default.Code, null, tint = Color.Gray) },
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/santiago43rus/Rupoop")
                }
            )
            ListItem(
                headlineContent = { Text("Лицензия") },
                supportingContent = { Text("MIT License") },
                leadingContent = { Icon(Icons.Default.Description, null, tint = Color.Gray) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
