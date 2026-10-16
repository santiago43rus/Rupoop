package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.SearchResult

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDownloadDialog(
    video: SearchResult,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colorScheme.surface,
                focusedContainerColor = colorScheme.surface
            ),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Выберите формат загрузки",
                    color = colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Вы хотите скачать видео целиком или только звуковую дорожку?\n${video.title}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onDownloadVideo()
                            onDismiss()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.primary,
                            focusedContainerColor = colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Видео (MP4)", color = colorScheme.onPrimary)
                    }

                    Button(
                        onClick = {
                            onDownloadAudio()
                            onDismiss()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Аудио (M4A)", color = colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
