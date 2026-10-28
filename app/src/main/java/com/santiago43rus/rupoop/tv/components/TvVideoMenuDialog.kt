package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.SearchResult

private data class TvMenuItem(val id: String, val title: String, val icon: ImageVector)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoMenuDialog(
    video: SearchResult,
    isInHistory: Boolean = false,
    isInWatchLater: Boolean = false,
    isInLiked: Boolean = false,
    isInPlaylist: Boolean = false,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember(isInHistory, isInWatchLater, isInLiked, isInPlaylist) {
        val list = mutableListOf<TvMenuItem>()
        if (isInHistory) list.add(TvMenuItem("remove_history", "Удалить из истории", Icons.Default.Delete))
        if (isInWatchLater) list.add(TvMenuItem("remove_later", "Удалить из «Смотреть позже»", Icons.Default.Delete))
        if (isInLiked) list.add(TvMenuItem("remove_liked", "Удалить из понравившихся", Icons.Default.Delete))
        if (isInPlaylist) list.add(TvMenuItem("remove_playlist", "Удалить из плейлиста", Icons.Default.Delete))
        list.add(TvMenuItem("not_interested", "Не интересно", Icons.Default.VisibilityOff))
        list.add(TvMenuItem("playlist", "В плейлист", Icons.AutoMirrored.Filled.PlaylistAdd))
        if (!isInWatchLater) list.add(TvMenuItem("later", "Смотреть позже", Icons.Default.WatchLater))
        list
    }

    val colorScheme = MaterialTheme.colorScheme
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = video.title,
                    color = colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                items.forEachIndexed { index, item ->
                    Surface(
                        onClick = {
                            onAction(item.id)
                            onDismiss()
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(imageVector = item.icon, contentDescription = null)
                            Text(text = item.title, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
