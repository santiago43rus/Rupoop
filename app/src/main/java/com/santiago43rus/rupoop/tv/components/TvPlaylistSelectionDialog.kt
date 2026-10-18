package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.Playlist

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlaylistSelectionDialog(
    playlists: List<Playlist>,
    onPlaylistSelected: (String) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var isCreating by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colorScheme.surface,
                focusedContainerColor = colorScheme.surface
            ),
            modifier = Modifier.widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isCreating) "Новый плейлист" else "Добавить в плейлист",
                    color = colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isCreating) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Название плейлиста") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreateNew(newPlaylistName.trim())
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.primary,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Создать")
                        }

                        Button(
                            onClick = { isCreating = false },
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отмена", color = colorScheme.onSurface)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Surface(
                                onClick = { isCreating = true },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text("Создать новый плейлист", fontSize = 15.sp)
                                }
                            }
                        }

                        items(playlists) { playlist ->
                            Surface(
                                onClick = {
                                    onPlaylistSelected(playlist.id)
                                    onDismiss()
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.background,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(playlist.name, fontSize = 15.sp)
                                    Text("${playlist.videos.size} видео", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
