package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.formatDuration

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    video: SearchResult,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        onLongClick = onMenuClick,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 1f)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key.nativeKeyCode == 82 || event.key == Key.Menu)) {
                    if (onMenuClick != null) {
                        onMenuClick()
                        true
                    } else false
                } else false
            },
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.06f
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, colorScheme.border.copy(alpha = 0.2f))),
            focusedBorder = Border(BorderStroke(2.5.dp, colorScheme.primary))
        ),
        colors = CardDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            focusedContainerColor = colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(colorScheme.background)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                val duration = video.duration
                if (duration != null && duration > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(colorScheme.background.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(duration.toLong()),
                            color = colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = video.title,
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.author?.name ?: "",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
