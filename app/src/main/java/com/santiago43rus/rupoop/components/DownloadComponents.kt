package com.santiago43rus.rupoop.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.DownloadItem
import com.santiago43rus.rupoop.data.DownloadStatus

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPlay: (DownloadItem) -> Unit = {}
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Notification permission card
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Уведомления отключены",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF212121)
                            )
                            Text(
                                "Включите уведомления, чтобы следить за загрузками в шторке",
                                fontSize = 12.sp,
                                color = Color(0xFF616161)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Включить", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Download,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Нет загрузок", color = Color.Gray, fontSize = 16.sp)
                        Text(
                            "Скачанные видео будут отображаться здесь",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        items(downloads) { item ->
            DownloadCard(
                item = item,
                onPause = { onPause(item.videoId) },
                onResume = { onResume(item.videoId) },
                onCancel = { onCancel(item.videoId) },
                onDelete = { onDelete(item.videoId) },
                onRetry = { onRetry(item.videoId) },
                onPlay = { onPlay(item) }
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (item.status) {
            DownloadStatus.DOWNLOADING -> Color(0xFF2196F3)
            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
            DownloadStatus.ERROR -> Color(0xFFF44336)
            DownloadStatus.PAUSED -> Color(0xFFFF9800)
            DownloadStatus.CANCELLED -> Color.Gray
            DownloadStatus.PENDING -> Color(0xFF9E9E9E)
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail
                Box(
                    Modifier
                        .size(80.dp, 45.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Status overlay
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .background(statusColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (item.status) {
                                DownloadStatus.DOWNLOADING -> Icons.Default.Download
                                DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                DownloadStatus.ERROR -> Icons.Default.Error
                                DownloadStatus.PAUSED -> Icons.Default.Pause
                                DownloadStatus.CANCELLED -> Icons.Default.Cancel
                                DownloadStatus.PENDING -> Icons.Default.Schedule
                            },
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title and status
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (item.status) {
                            DownloadStatus.DOWNLOADING -> "Загрузка... ${item.progress}%"
                            DownloadStatus.COMPLETED -> "Завершено"
                            DownloadStatus.ERROR -> "Ошибка: ${item.error ?: "Неизвестная"}"
                            DownloadStatus.PAUSED -> "Приостановлено (${item.progress}%)"
                            DownloadStatus.CANCELLED -> "Отменено"
                            DownloadStatus.PENDING -> "Ожидание..."
                        },
                        fontSize = 12.sp,
                        color = statusColor
                    )
                }

                // Actions
                Row {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Pause, "Пауза", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, "Отмена", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PlayArrow, "Возобновить", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, "Отмена", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        DownloadStatus.ERROR -> {
                            IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Refresh, "Повторить", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Удалить", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PlayArrow, "Воспроизвести", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Удалить", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        DownloadStatus.CANCELLED, DownloadStatus.PENDING -> {
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Удалить", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Progress bar
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}

