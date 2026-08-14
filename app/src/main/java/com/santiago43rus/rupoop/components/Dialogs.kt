package com.santiago43rus.rupoop.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.santiago43rus.rupoop.data.*

@Composable
fun ContentSelectionDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val allGenres = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "сериалы", "мультсериалы"
    )
    var enabledGenres by remember { mutableStateOf(settingsManager.enabledGenres) }

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(if (isLandscape) 640.dp else 340.dp),
        title = { Text("Что вам интересно?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "Новый плейлист" else "Добавить в плейлист") },
        text = {
            if (isCreating) {
                val playlistNameState = rememberTextFieldState("")
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                OutlinedTextField(
                    state = playlistNameState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(focusRequester),
                    lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp),
                    placeholder = {
                        Text("Название", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    onKeyboardAction = {
                        if (playlistNameState.text.isNotBlank()) {
                            onCreateNew(playlistNameState.text.toString())
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledBorderColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
                LaunchedEffect(playlistNameState.text) {
                    newName = playlistNameState.text.toString()
                }
            } else {
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
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    showGistCheckbox: Boolean = false,
    initialGistChecked: Boolean = true,
    confirmButtonText: String = "Удалить",
    onConfirm: (deleteFromGist: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteFromGist by remember { mutableStateOf(initialGistChecked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                if (showGistCheckbox) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFromGist = !deleteFromGist }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteFromGist,
                            onCheckedChange = { deleteFromGist = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Удалить также из GitHub Gist",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(if (showGistCheckbox) deleteFromGist else false) }) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DeleteConfirmationDialog(
        title = title,
        message = message,
        showGistCheckbox = false,
        onConfirm = { onConfirm() },
        onDismiss = onDismiss
    )
}

@Composable
fun OpenUrlDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var urlText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Открыть видео по ссылке") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Поддерживаются видео с Rutube, VK (vk.com / vk.ru / vkvideo.ru), Lordfilm и пиратских плееров, а также прямые ссылки .m3u8 и .mp4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("https://...") },
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    urlText = clip.trim()
                                }
                            }
                        ) {
                            Text("Вставить", fontSize = 12.sp)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (trimmed.isNotBlank()) {
                        onPlayUrl(trimmed)
                        onDismiss()
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text("Воспроизвести")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
