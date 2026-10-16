package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoSettingsDialog(
    exoPlayer: ExoPlayer,
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Качество, 1: Скорость
    val qualityList = listOf("Авто", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
    val speedList = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

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
                    text = "Настройки видео",
                    color = colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { selectedTab = 0 },
                        colors = ButtonDefaults.colors(
                            containerColor = if (selectedTab == 0) colorScheme.primary else colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Качество",
                            color = if (selectedTab == 0) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { selectedTab = 1 },
                        colors = ButtonDefaults.colors(
                            containerColor = if (selectedTab == 1) colorScheme.primary else colorScheme.surfaceVariant,
                            focusedContainerColor = colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Скорость",
                            color = if (selectedTab == 1) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qualityList.forEach { q ->
                            val isSelected = currentQuality == q
                            Surface(
                                onClick = {
                                    onQualitySelected(q)
                                    val params = exoPlayer.trackSelectionParameters.buildUpon()
                                    when (q) {
                                        "2160p" -> params.setMaxVideoSize(3840, 2160).setForceHighestSupportedBitrate(true)
                                        "1440p" -> params.setMaxVideoSize(2560, 1440).setForceHighestSupportedBitrate(true)
                                        "1080p" -> params.setMaxVideoSize(1920, 1080).setForceHighestSupportedBitrate(true)
                                        "720p" -> params.setMaxVideoSize(1280, 720).setForceHighestSupportedBitrate(true)
                                        "480p" -> params.setMaxVideoSize(854, 480).setForceHighestSupportedBitrate(true)
                                        "360p" -> params.setMaxVideoSize(640, 360).setForceHighestSupportedBitrate(true)
                                        "240p" -> params.setMaxVideoSize(426, 240).setForceHighestSupportedBitrate(true)
                                        "144p" -> params.setMaxVideoSize(256, 144).setForceHighestSupportedBitrate(true)
                                        else -> params.clearVideoSizeConstraints().setForceHighestSupportedBitrate(false)
                                    }
                                    exoPlayer.trackSelectionParameters = params.build()
                                    onDismiss()
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.background,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = q,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val currentSpeed = exoPlayer.playbackParameters.speed
                        speedList.forEach { speed ->
                            val isSelected = currentSpeed == speed
                            Surface(
                                onClick = {
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    onDismiss()
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.background,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
