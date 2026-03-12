package com.santiago43rus.rupoop.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

enum class PlayerState { CLOSED, MINI, FULL }
enum class OverlayState { SEARCH, AUTHOR }
enum class NavItem { HOME, SUBSCRIPTIONS, LIBRARY, SETTINGS }
enum class LibrarySubScreen { NONE, LIKED, WATCH_LATER, PLAYLISTS, PLAYLIST_DETAIL, HISTORY }

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun setScreenOrientation(context: Context, orientation: Int) {
    context.findActivity()?.requestedOrientation = orientation
}

fun hideSystemBars(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
}

fun showSystemBars(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

fun extractId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }

