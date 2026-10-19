package com.santiago43rus.rupoop.tv.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*

data class VideoQualityOption(
    val label: String,
    val width: Int,
    val height: Int
)

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
    val speedList = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val tabQualityFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        tabQualityFocusRequester.requestFocus()
    }

    // Извлечение реально доступных разрешений из текущего видеопотока ExoPlayer
    val availableQualities = remember(exoPlayer) {
        val tracks = exoPlayer.currentTracks
        val qualityList = mutableListOf<VideoQualityOption>()
        qualityList.add(VideoQualityOption("Авто", 0, 0))

        val heights = mutableSetOf<Int>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height > 0) {
                        heights.add(format.height)
                    }
                }
            }
        }

        // Сортировка от большего к меньшему
        val sortedHeights = heights.sortedDescending()
        for (h in sortedHeights) {
            val (label, width) = when {
                h >= 2160 -> "2160p" to 3840
                h >= 1440 -> "1440p" to 2560
                h >= 1080 -> "1080p" to 1920
                h >= 720 -> "720p" to 1280
                h >= 480 -> "480p" to 854
                h >= 360 -> "360p" to 640
                h >= 240 -> "240p" to 426
                else -> "${h}p" to ((h * 16) / 9)
            }
            if (qualityList.none { it.label == label }) {
                qualityList.add(VideoQualityOption(label, width, h))
            }
        }

        // Если HLS / DASH стрим еще не передал trackGroup с отдельными битрейтами, предоставим стандартный набор
        if (qualityList.size == 1) {
            listOf(
                VideoQualityOption("Авто", 0, 0),
                VideoQualityOption("2160p", 3840, 2160),
                VideoQualityOption("1440p", 2560, 1440),
                VideoQualityOption("1080p", 1920, 1080),
                VideoQualityOption("720p", 1280, 720),
                VideoQualityOption("480p", 854, 480),
                VideoQualityOption("360p", 640, 360),
                VideoQualityOption("240p", 426, 240),
                VideoQualityOption("144p", 256, 144)
            )
        } else {
            qualityList
        }
    }

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
                            containerColor = if (selectedTab == 0) colorScheme.surfaceVariant else colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabQualityFocusRequester)
                    ) {
                        Text("Качество")
                    }

                    Button(
                        onClick = { selectedTab = 1 },
                        colors = ButtonDefaults.colors(
                            containerColor = if (selectedTab == 1) colorScheme.surfaceVariant else colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Скорость")
                    }
                }

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableQualities.forEach { qOption ->
                            val isSelected = currentQuality == qOption.label
                            Surface(
                                onClick = {
                                    onQualitySelected(qOption.label)
                                    val params = exoPlayer.trackSelectionParameters.buildUpon()
                                    if (qOption.label == "Авто" || qOption.height == 0) {
                                        params.clearVideoSizeConstraints()
                                            .setForceHighestSupportedBitrate(false)
                                    } else {
                                        params.setMaxVideoSize(qOption.width, qOption.height)
                                            .setMinVideoSize(qOption.width, qOption.height)
                                            .setForceHighestSupportedBitrate(true)
                                    }
                                    exoPlayer.trackSelectionParameters = params.build()
                                    onDismiss()
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = qOption.label,
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
                                    containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                                    contentColor = colorScheme.onSurface,
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
