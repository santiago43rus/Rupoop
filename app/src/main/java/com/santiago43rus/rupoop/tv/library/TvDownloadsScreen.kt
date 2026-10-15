package com.santiago43rus.rupoop.tv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
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
                Text("Нет скачанных видео", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            items(downloads, key = { it.videoId }) { download ->
                Surface(
                    onClick = { onPlay(download) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1E2228),
                        focusedContainerColor = Color(0xFF282E38)
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
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Статус: ${download.status} • Прогресс: ${download.progress}%",
                                color = Color(0xFF8B949E),
                                fontSize = 13.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { onPlay(download) },
                                colors = IconButtonDefaults.colors(
                                    containerColor = Color(0xFF238636),
                                    focusedContainerColor = Color(0xFF2EA043)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Играть", tint = Color.White)
                            }
                            IconButton(
                                onClick = { onDelete(download.videoId) },
                                colors = IconButtonDefaults.colors(
                                    containerColor = Color(0xFFDA3633),
                                    focusedContainerColor = Color(0xFFF85149)
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
