package com.santiago43rus.rupoop.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.*

@Composable
fun ContentSelectionDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    var adultEnabled by remember { mutableStateOf(settingsManager.adultContentEnabled) }
    var kidsEnabled by remember { mutableStateOf(settingsManager.kidsContentEnabled) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Что вам интересно?") },
        text = {
            Column {
                Text("Выберите, что вы планируете смотреть. Это поможет нам настроить рекомендации.", modifier = Modifier.padding(bottom = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { adultEnabled = !adultEnabled }) {
                    Checkbox(checked = adultEnabled, onCheckedChange = { adultEnabled = it })
                    Text("Фильмы и Сериалы")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { kidsEnabled = !kidsEnabled }) {
                    Checkbox(checked = kidsEnabled, onCheckedChange = { kidsEnabled = it })
                    Text("Мультфильмы и Детское")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!adultEnabled && !kidsEnabled) adultEnabled = true
                    settingsManager.adultContentEnabled = adultEnabled
                    settingsManager.kidsContentEnabled = kidsEnabled
                    settingsManager.isFirstLaunch = false
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        }
    )
}

@Composable
fun PlaylistSelectionDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "Новый плейлист" else "Добавить в плейлист") },
        text = {
            if (isCreating) TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Название") })
            else {
                LazyColumn {
                    items(playlists) { p -> Row(Modifier.fillMaxWidth().clickable { onPlaylistSelected(p.name) }.padding(12.dp)) { Text(p.name) } }
                    item { TextButton(onClick = { isCreating = true }) { Text("+ Создать новый") } }
                }
            }
        },
        confirmButton = { if (isCreating) Button(onClick = { if (newName.isNotBlank()) onCreateNew(newName) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onThemeToggle: () -> Unit,
    registryManager: UserRegistryManager,
    onRegistryUpdate: (UserRegistry) -> Unit
) {
    var downloadQuality by remember { mutableStateOf(settingsManager.downloadQuality) }
    var syncFreq by remember { mutableStateOf(settingsManager.syncFrequencyHours.toString()) }
    var adultEnabled by remember { mutableStateOf(settingsManager.adultContentEnabled) }
    var kidsEnabled by remember { mutableStateOf(settingsManager.kidsContentEnabled) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Внешний вид", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Темная тема") }, trailingContent = { Switch(checked = settingsManager.isDarkTheme, onCheckedChange = { onThemeToggle() }) })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Рекомендации контента", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Фильмы и Сериалы") }, trailingContent = {
                Switch(checked = adultEnabled, onCheckedChange = {
                    adultEnabled = it; settingsManager.adultContentEnabled = it
                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(adultContentEnabled = it)))
                    onRegistryUpdate(registryManager.registry)
                })
            })
            ListItem(headlineContent = { Text("Мультфильмы и Детское") }, trailingContent = {
                Switch(checked = kidsEnabled, onCheckedChange = {
                    kidsEnabled = it; settingsManager.kidsContentEnabled = it
                    registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(kidsContentEnabled = it)))
                    onRegistryUpdate(registryManager.registry)
                })
            })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Загрузка", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            Text("Качество видео для скачивания", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                listOf("360", "480", "720", "1080").forEach { q ->
                    FilterChip(selected = downloadQuality == q, onClick = {
                        downloadQuality = q; settingsManager.downloadQuality = q
                        registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(downloadQuality = q)))
                        onRegistryUpdate(registryManager.registry)
                    }, label = { Text(q + "p") })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("История и конфиденциальность", style = MaterialTheme.typography.titleMedium, color = Color.Red)
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

            Text("GitHub Синхронизация", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            ListItem(headlineContent = { Text("Периодичность (в часах)") }, trailingContent = {
                TextField(value = syncFreq, onValueChange = { newValue ->
                    syncFreq = newValue
                    newValue.toIntOrNull()?.let { hours ->
                        settingsManager.syncFrequencyHours = hours
                        registryManager.updateRegistry(registryManager.registry.copy(appSettings = registryManager.registry.appSettings.copy(syncFrequencyHours = hours)))
                        onRegistryUpdate(registryManager.registry)
                    }
                }, modifier = Modifier.width(60.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            })
        }
    }
}

