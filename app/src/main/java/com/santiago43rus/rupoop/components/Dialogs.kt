package com.santiago43rus.rupoop.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.data.*

@Composable
fun ContentSelectionDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "сериалы", "музыка"
    )
    var enabledGenres by remember { mutableStateOf(settingsManager.enabledGenres) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Что вам интересно?") },
        text = {
            Column {
                Text("Выберите жанры, которые вам интересны. Это поможет нам настроить рекомендации.", modifier = Modifier.padding(bottom = 16.dp))
                // Genre chips in a flow layout
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allGenres.forEach { genre ->
                        val selected = genre in enabledGenres
                        FilterChip(
                            selected = selected,
                            onClick = {
                                enabledGenres = if (selected) enabledGenres - genre else enabledGenres + genre
                            },
                            label = { Text(genre.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    settingsManager.enabledGenres = enabledGenres
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


