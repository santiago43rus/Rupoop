@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.santiago43rus.rupoop.player

import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatTimeAgo
import com.santiago43rus.rupoop.util.formatViewCount
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import java.util.Locale


@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomVideoPlayer(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFullscreen: Boolean,
    currentVideo: SearchResult?,
    relatedVideos: List<SearchResult> = emptyList(),
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    isFirstVideo: Boolean = false,
    isLastVideo: Boolean = false,
    isTransitioning: Boolean = false,
    onPlayRelated: (SearchResult) -> Unit = {}
) {
    var showControls by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var showSettings by remember { mutableStateOf(false) }
    var draggingPos by remember { mutableStateOf<Long?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var showMoreVideos by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var showSeekAnimation by remember { mutableStateOf(false) }
    var moreVideosDragOffset by remember { mutableFloatStateOf(0f) }

    var swipeScale by remember { mutableFloatStateOf(1f) }
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    var restoreControlsAfterFullscreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsManager = remember { com.santiago43rus.rupoop.data.SettingsManager(context) }

    LaunchedEffect(isFullscreen) {
        if (restoreControlsAfterFullscreen) {
            delay(500) // Wait for screen orientation animation to finish
            showControls = true
            restoreControlsAfterFullscreen = false
        }
    }

    LaunchedEffect(showSeekAnimation) {
        if (showSeekAnimation) {
            delay(500)
            showSeekAnimation = false
        }
    }

    LaunchedEffect(showControls, isPlaying, isSeeking) {
        if (showControls && isPlaying && !isSeeking) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    var selectedQuality by remember { mutableStateOf("Авто") }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val shouldFillMax = isFullscreen && isLandscape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (shouldFillMax) Modifier.fillMaxSize() else Modifier.aspectRatio(16 / 9f))
            .offset { IntOffset(swipeOffsetX.roundToInt(), swipeOffsetY.roundToInt()) }
            .graphicsLayer {
                scaleX = swipeScale
                scaleY = swipeScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (shouldFillMax) 0.5f else 0f)
            }
            .background(Color.Black)
            .pointerInput(isFullscreen) {
                if (!isFullscreen) return@pointerInput
                var totalDragY = 0f
                var totalDragX = 0f
                var isMoreVideosGesture = false
                var isScreenTransitionGesture = false
                var wasControlsVisible = false
                val dragMultiplier = 1.3f // Сбалансированная чувствительность
                val maxDragDistanceVertical = 150f
                val maxDragDistanceFullScreen = 80f // Укороченные границы для быстрого срабатывания в горизонтальном

                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        totalDragX = 0f
                        isMoreVideosGesture = false
                        isScreenTransitionGesture = false
                        wasControlsVisible = showControls
                        showControls = false // Скрываем элементы на время жеста
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x

                        val absY = kotlin.math.abs(totalDragY)
                        val absX = kotlin.math.abs(totalDragX)

                        // Определяем тип жеста при первом достаточном смещении
                        if (!isMoreVideosGesture && !isScreenTransitionGesture && absY > 20f && absY > absX) {
                            if (isFullscreen && totalDragY < 0 && !showMoreVideos) {
                                isMoreVideosGesture = true
                            } else if (isFullscreen && totalDragY > 0) {
                                isScreenTransitionGesture = true
                            } else if (!isFullscreen && totalDragY < 0) {
                                isScreenTransitionGesture = true
                            }
                        }

                        if (isMoreVideosGesture) {
                            if (totalDragY < 0) {
                                moreVideosDragOffset = totalDragY * 1.25f // Быстрее открывается панель
                            } else {
                                moreVideosDragOffset = 0f
                            }
                        } else if (isScreenTransitionGesture) {
                            val activeDrag = if (isFullscreen) totalDragY else -totalDragY
                            
                            if (activeDrag > 0) {
                                val maxDist = if (isFullscreen) maxDragDistanceFullScreen else maxDragDistanceVertical
                                val boundedDrag = activeDrag.coerceAtMost(maxDist)
                                val progress = boundedDrag / maxDist

                                if (isFullscreen) {
                                    // В горизонтальном (свайп вниз) -> уменьшение
                                    swipeScale = 1f - (0.2f * progress)
                                    swipeOffsetX = 0f
                                    swipeOffsetY = boundedDrag * dragMultiplier * 0.4f
                                } else {
                                    // В вертикальном (свайп вверх) -> увеличение
                                    swipeScale = 1f + (0.25f * progress)
                                    swipeOffsetX = 0f
                                    swipeOffsetY = -boundedDrag * dragMultiplier * 0.4f
                                }
                            } else {
                                // Плавный возврат без рывков, если двигать назад
                                swipeScale = 1f
                                swipeOffsetX = 0f
                                swipeOffsetY = 0f
                            }
                        }
                    },
                    onDragEnd = {
                        var returnControls = false
                        if (isMoreVideosGesture) {
                            if (totalDragY < -75f) { // Вдвое меньший порог для шторки
                                showMoreVideos = true
                            } else {
                                returnControls = true
                            }
                        } else if (isScreenTransitionGesture) {
                            val activeDrag = if (isFullscreen) totalDragY else -totalDragY
                            val threshold = if (isFullscreen) 40f else 60f // Меньший порог для полноэкранного режима

                            if (activeDrag > threshold) {
                                onToggleFullscreen()
                                if (wasControlsVisible) {
                                    restoreControlsAfterFullscreen = true // Отложенный возврат контролов
                                }
                            } else {
                                returnControls = true
                            }
                        } else {
                            returnControls = true
                        }

                        if (returnControls && wasControlsVisible) {
                            showControls = true
                        }
                        
                        moreVideosDragOffset = 0f
                        swipeScale = 1f
                        swipeOffsetY = 0f
                        swipeOffsetX = 0f
                        isMoreVideosGesture = false
                        isScreenTransitionGesture = false
                    },
                    onDragCancel = {
                        if (wasControlsVisible) showControls = true
                        moreVideosDragOffset = 0f
                        swipeScale = 1f
                        swipeOffsetY = 0f
                        swipeOffsetX = 0f
                        isMoreVideosGesture = false
                        isScreenTransitionGesture = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        showSeekAnimation = true
                        val seekAmount = settingsManager.doubleTapSeekDuration * 1000L
                        if (offset.x < size.width / 2) {
                            seekDirection = -1
                            val target = (exoPlayer.currentPosition - seekAmount).coerceAtLeast(0)
                            currentTime = target
                            exoPlayer.seekTo(target)
                        } else {
                            seekDirection = 1
                            val target = (exoPlayer.currentPosition + seekAmount).coerceAtMost(exoPlayer.duration)
                            currentTime = target
                            exoPlayer.seekTo(target)
                        }
                    },
                    onLongPress = {
                        isFastForwarding = true
                        exoPlayer.playbackParameters = PlaybackParameters(2f)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isFastForwarding) {
                            isFastForwarding = false
                            exoPlayer.playbackParameters = PlaybackParameters(1f)
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.Red,
                strokeWidth = 4.dp
            )
        }

        // 2x Speed Indicator
        if (isFastForwarding) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 48.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Double tap seek animation overlays
        if (showSeekAnimation) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = seekDirection == -1,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.35f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FastRewind, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            Text("-10 сек", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = seekDirection == 1,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.35f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            Text("+10 сек", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = (showControls || isSeeking) && !showMoreVideos && !isTransitioning,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSeeking) Color.Transparent else Color.Black.copy(alpha = 0.4f))
                    .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
            ) {
                if (!isSeeking) {
                    // Top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = if (isFullscreen) 14.dp else 8.dp, end = if (isFullscreen) 20.dp else 4.dp, top = 8.dp, bottom = 8.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (isFullscreen) {
                                showControls = false
                                restoreControlsAfterFullscreen = true
                                onToggleFullscreen()
                            } else {
                                onMinimize()
                            }
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.Start) {
                            Text(currentVideo?.title ?: "", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentVideo?.author?.name ?: "", color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1)
                        }

                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, null, tint = Color.White)
                        }
                    }

                    // Center controls
                    if (!isBuffering) {
                        Row(Modifier.align(Alignment.Center).padding(horizontal = if (isFullscreen) 32.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onPrevious,
                                enabled = !isFirstVideo
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious, 
                                    null, 
                                    tint = if (isFirstVideo) Color.Gray else Color.White, 
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            Spacer(Modifier.width(32.dp))
                            IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(70.dp))
                            }
                            Spacer(Modifier.width(32.dp))
                            IconButton(
                                onClick = onNext,
                                enabled = !isLastVideo
                            ) {
                                Icon(
                                    Icons.Default.SkipNext, 
                                    null, 
                                    tint = if (isLastVideo) Color.Gray else Color.White, 
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isFullscreen) 16.dp else 4.dp)
                        .padding(start = if (isFullscreen) 22.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // YouTube Style Seeking Bubble
                    if (isSeeking && draggingPos != null) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .background(Color.Black.copy(0.8f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${formatTime(draggingPos!!)} ${currentVideo?.title?.take(20) ?: ""}...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = 12.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(draggingPos ?: currentTime)} / ${formatTime(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (!isSeeking) {
                            IconButton(onClick = {
                                showControls = false
                                restoreControlsAfterFullscreen = true
                                onToggleFullscreen()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Slider(
                        value = if (duration > 0) (draggingPos ?: currentTime).toFloat() / duration else 0f,
                        onValueChange = { isSeeking = true; draggingPos = (it * duration).toLong() },
                        onValueChangeFinished = { 
                            isSeeking = false
                            draggingPos?.let {
                                exoPlayer.seekTo(it)
                                currentTime = it
                            }
                            draggingPos = null 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(0.3f)
                        ),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(12.dp, 12.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.Red)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(2.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.White.copy(0.3f)
                                )
                            )
                        }
                    )
                }
            }
        }

        // More Videos Overlay
        val screenHeightPixels = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()
        var panelDragY by remember { mutableFloatStateOf(0f) }
        val isDraggingUp = moreVideosDragOffset < 0f

        val targetOffsetY = when {
            isDraggingUp -> (screenHeightPixels + moreVideosDragOffset).coerceAtLeast(0f)
            showMoreVideos -> panelDragY
            else -> screenHeightPixels
        }

        val animatedOffsetY by animateFloatAsState(
            targetValue = targetOffsetY,
            animationSpec = tween(durationMillis = if (isDraggingUp || panelDragY > 0f) 0 else 250),
            label = "moreVideosY"
        )

        if (showMoreVideos || animatedOffsetY < screenHeightPixels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                    .background(Color.Black.copy(0.9f))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                if (panelDragY > 100f) { // Уменьшнен порог закрытия с 200f
                                    showMoreVideos = false
                                }
                                panelDragY = 0f
                            },
                            onDragCancel = { panelDragY = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            panelDragY = (panelDragY + dragAmount).coerceAtLeast(0f)
                        }
                    }
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 20.dp else 4.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text("Ещё видео", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { showMoreVideos = false }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                    
                    // Filter Chips (as in screenshot)
                    Row(Modifier.padding(vertical = 8.dp).padding(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Все видео") }, colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Color.White.copy(0.2f)))
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = false, onClick = {}, label = { Text("Автор: ${currentVideo?.author?.name ?: "Автор"}") }, colors = FilterChipDefaults.filterChipColors(labelColor = Color.White))
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = if (isFullscreen) 48.dp else 16.dp, end = if (isFullscreen) 32.dp else 16.dp)
                    ) {
                        items(relatedVideos) { video ->
                            Column(
                                modifier = Modifier
                                    .width(240.dp)
                                    .clickable { 
                                        onPlayRelated(video)
                                        showMoreVideos = false 
                                    }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = video.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16 / 9f)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    video.duration?.let { dur ->
                                        Surface(
                                            color = Color.Black.copy(0.8f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        ) {
                                            Text(
                                                formatTime(dur.toLong() * 1000),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row {
                                    AsyncImage(
                                        model = video.author?.avatarUrl ?: "",
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(video.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        val viewsText = formatViewCount(video.hits)
                                        val timeAgoText = formatTimeAgo(video.publicationTs ?: video.createdTs)
                                        val metaText = buildString {
                                            append(video.author?.name ?: "Автор")
                                            append(" • Rutube")
                                            if (viewsText.isNotEmpty()) append(" • $viewsText")
                                            if (timeAgoText.isNotEmpty()) append(" • $timeAgoText")
                                        }
                                        Text(metaText, color = Color.White.copy(0.6f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(exoPlayer, selectedQuality, { selectedQuality = it }, { showSettings = false })
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SettingsDialog(exoPlayer: ExoPlayer, currentQuality: String, onQualitySelected: (String) -> Unit, onDismiss: () -> Unit) {
    var showQuality by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Настройки видео", style = MaterialTheme.typography.titleLarge)
                ListItem(headlineContent = { Text("Качество") }, trailingContent = { Text(currentQuality) }, modifier = Modifier.clickable { showQuality = true })
                ListItem(headlineContent = { Text("Скорость") }, trailingContent = { Text("${exoPlayer.playbackParameters.speed}x") }, modifier = Modifier.clickable { showSpeed = true })
            }
        }
    }
    if (showQuality) {
        OptionSelectionDialog("Качество", listOf("Авто", "1080p", "720p", "480p", "360p"), currentQuality, {
            onQualitySelected(it)
            val params = exoPlayer.trackSelectionParameters.buildUpon()
            when(it) {
                "1080p" -> params.setMaxVideoSize(1920, 1080).setForceHighestSupportedBitrate(true)
                "720p" -> params.setMaxVideoSize(1280, 720).setForceHighestSupportedBitrate(true)
                "480p" -> params.setMaxVideoSize(854, 480).setForceHighestSupportedBitrate(true)
                "360p" -> params.setMaxVideoSize(640, 360).setForceHighestSupportedBitrate(true)
                else -> params.clearVideoSizeConstraints().setForceHighestSupportedBitrate(false)
            }
            exoPlayer.trackSelectionParameters = params.build()
            onDismiss()
        }, { showQuality = false })
    }
    if (showSpeed) {
        OptionSelectionDialog("Скорость", listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), exoPlayer.playbackParameters.speed, { speed ->
            exoPlayer.playbackParameters = PlaybackParameters(speed)
            onDismiss()
        }, { showSpeed = false })
    }
}

@Composable
fun <T> OptionSelectionDialog(title: String, options: List<T>, currentValue: T, onOptionSelected: (T) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onOptionSelected(option) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == currentValue, onClick = null)
                        Text(option.toString(), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(video: SearchResult?, isPlaying: Boolean, exoPlayer: ExoPlayer, onClose: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(100.dp, 56.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
            AndroidView(factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(video?.author?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
    }
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms); val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60; val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
