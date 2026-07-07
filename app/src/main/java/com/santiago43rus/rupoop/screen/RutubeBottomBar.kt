package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.LibrarySubScreen

@Composable
fun RutubeBottomBar(
    currentNav: NavItem,
    onNavChange: (NavItem) -> Unit,
    isSettingsVisible: Boolean,
    isSearchExpanded: Boolean,
    isSearchVisible: Boolean,
    isAuthorVisible: Boolean,
    currentLibSub: LibrarySubScreen,
    onResetOverlays: () -> Unit,
    onScrollHome: () -> Unit,
    onScrollSubs: () -> Unit,
    onScrollLib: () -> Unit,
    progress: Float,
    isFullscreenVideo: Boolean
) {
    if (isFullscreenVideo || isSettingsVisible) return

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = size.height * (1f - progress.coerceIn(0f, 1f)) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            val isHome = currentNav == NavItem.HOME
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (currentNav == NavItem.HOME) {
                                if (isSettingsVisible || isSearchExpanded || isSearchVisible || isAuthorVisible) {
                                    onResetOverlays()
                                } else {
                                    onScrollHome()
                                }
                            } else {
                                onNavChange(NavItem.HOME)
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Главная",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Subscriptions
            val isSubs = currentNav == NavItem.SUBSCRIPTIONS
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (currentNav == NavItem.SUBSCRIPTIONS) {
                                if (isSettingsVisible || isSearchExpanded || isSearchVisible || isAuthorVisible) {
                                    onResetOverlays()
                                } else {
                                    onScrollSubs()
                                }
                            } else {
                                onNavChange(NavItem.SUBSCRIPTIONS)
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSubs) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                        contentDescription = "Subs",
                        tint = if (isSubs) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Подписки",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isSubs) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Library
            val isLib = currentNav == NavItem.LIBRARY
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (currentNav == NavItem.LIBRARY) {
                                if (isSettingsVisible || isSearchExpanded || isSearchVisible || isAuthorVisible || currentLibSub != LibrarySubScreen.NONE) {
                                    onResetOverlays()
                                } else {
                                    onScrollLib()
                                }
                            } else {
                                onNavChange(NavItem.LIBRARY)
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isLib) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                        contentDescription = "Lib",
                        tint = if (isLib) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Библиотека",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isLib) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
