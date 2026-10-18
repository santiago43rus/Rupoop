package com.santiago43rus.rupoop.tv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.components.DeleteConfirmationDialog
import com.santiago43rus.rupoop.data.DownloadItem
import com.santiago43rus.rupoop.tv.components.TvSectionTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDownloadsScreen(
    downloads: List<DownloadItem>,
    onPlay: (DownloadItem) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var downloadToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        item {
            TvSectionTitle(title = "Загрузки")
        }

        if (downloads.isEmpty()) {
            item {
                Text("Нет скачанных видео", color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            items(downloads, key = { it.videoId }) { download ->
                Surface(
                    onClick = { onPlay(download) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        focusedContainerColor = colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = download.title,
                                color = colorScheme.onSurface,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Статус: ${download.status} • Прогресс: ${download.progress}%",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { onPlay(download) },
                                colors = IconButtonDefaults.colors(
                                    containerColor = colorScheme.primary,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Играть")
                            }
                            IconButton(
                                onClick = { downloadToDelete = download },
                                colors = IconButtonDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    val item = downloadToDelete
    if (item != null) {
        DeleteConfirmationDialog(
            title = "Удалить загрузку",
            message = "Вы уверены, что хотите удалить «${item.title}»?",
            onConfirm = { _: Boolean ->
                onDelete(item.videoId)
                downloadToDelete = null
            },
            onDismiss = { downloadToDelete = null }
        )
    }
}
