package com.santiago43rus.rupoop.tv.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.tv.components.TvSectionTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    settingsManager: SettingsManager,
    registry: UserRegistry,
    currentThemeMode: String,
    onThemeToggle: (String) -> Unit,
    onOpenHiddenVideos: () -> Unit,
    onPullFromGist: () -> Unit,
    onPushToGist: () -> Unit,
    onResetSync: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isEasterUnlocked = settingsManager.isEasterEggUnlocked

    var doubleTapDuration by remember { mutableIntStateOf(settingsManager.doubleTapSeekDuration) }
    var downloadNotifications by remember { mutableStateOf(settingsManager.showDownloadNotifications) }
    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var maxDownloads by remember { mutableIntStateOf(settingsManager.maxConcurrentDownloads) }
    var syncFreq by remember { mutableIntStateOf(settingsManager.syncFrequencyHours) }
    val enabledGenres = remember { mutableStateOf(settingsManager.enabledGenres) }

    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        item {
            TvSectionTitle(title = "Настройки")
        }

        // 1. ВНЕШНИЙ ВИД: ТЕМА
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Внешний вид (Тема)", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        val isSelected = currentThemeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                settingsManager.themeMode = mode
                                onThemeToggle(mode)
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                selectedContainerColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 2. ПЛЕЕР: ПЕРЕМОТКА ДВОЙНЫМ НАЖАТИЕМ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Плеер (Шаг перемотки)", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                selectedContainerColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "$dur сек",
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 3. УВЕДОМЛЕНИЯ О ЗАГРУЗКАХ
        item {
            Surface(
                onClick = {
                    val newVal = !downloadNotifications
                    downloadNotifications = newVal
                    settingsManager.showDownloadNotifications = newVal
                },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = colorScheme.surfaceVariant,
                    focusedContainerColor = colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Уведомления о загрузке", color = colorScheme.onSurface, fontSize = 16.sp)
                        Text("Показывать прогресс и статус загрузки файлов", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(checked = downloadNotifications, onCheckedChange = null)
                }
            }
        }

        // 4. ЖАНРЫ И ЧИПЫ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Жанры и рекомендации", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                selectedContainerColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary
                            )
                        ) {
                            Text(
                                text = genre.replaceFirstChar { it.uppercase() },
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 5. ЗАГРУЗКИ: КАЧЕСТВО И ОДНОВРЕМЕННЫЕ ЗАГРУЗКИ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Качество видео для скачивания", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                focusedContainerColor = colorScheme.primary,
                                selectedContainerColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "${q}p",
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Одновременные загрузки
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            },
                            colors = FilterChipDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                selectedContainerColor = colorScheme.primary,
                                focusedSelectedContainerColor = colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "$limit",
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 6. КОНТЕНТ И РЕКОМЕНДАЦИИ (СКРЫТЫЕ ВИДЕО)
        item {
            Surface(
                onClick = onOpenHiddenVideos,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = colorScheme.surfaceVariant,
                    focusedContainerColor = colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Скрытые и неинтересные видео", color = colorScheme.onSurface, fontSize = 16.sp)
                        Text("Управление видео, исключенными из ленты (${registry.hiddenVideosList.size})", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text("Открыть", color = colorScheme.primary, fontSize = 14.sp)
                }
            }
        }

        // 7. GITHUB СИНХРОНИЗАЦИЯ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Синхронизация GitHub Gist", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (settingsManager.accessToken == null) {
                    Button(
                        onClick = onOpenLogin,
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.primary,
                            focusedContainerColor = colorScheme.primary
                        )
                    ) {
                        Text("Войти через GitHub (Device Flow)", color = colorScheme.onPrimary)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(onClick = onPullFromGist) { Text("Загрузить из Gist") }
                        Button(onClick = onPushToGist) { Text("Выгрузить в Gist") }
                        Button(onClick = onResetSync) { Text("Сбросить ID гиста") }
                        Button(onClick = onLogout) { Text("Выйти") }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Периодичность фоновой синхронизации:", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                                },
                                colors = FilterChipDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.primary,
                                    selectedContainerColor = colorScheme.primary,
                                    focusedSelectedContainerColor = colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "$hours ч",
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. ИСТОРИЯ И КОНФИДЕНЦИАЛЬНОСТЬ
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("История и конфиденциальность", color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        focusedContainerColor = colorScheme.surface
                    )
                ) {
                    Text("Очистить историю просмотров", color = colorScheme.onSurface)
                }
            }
        }
    }
}
