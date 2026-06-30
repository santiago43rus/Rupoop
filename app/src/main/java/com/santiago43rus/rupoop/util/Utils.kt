package com.santiago43rus.rupoop.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.santiago43rus.rupoop.network.NetworkMonitor
import java.io.File

enum class PlayerState { CLOSED, MINI, FULL }
enum class OverlayState { SEARCH, AUTHOR }
enum class NavItem { HOME, SUBSCRIPTIONS, LIBRARY }
enum class LibrarySubScreen { NONE, LIKED, WATCH_LATER, PLAYLISTS, PLAYLIST_DETAIL, HISTORY, DOWNLOADS }

fun formatDuration(seconds: Long): String {
    return seconds.formatDuration()
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun setScreenOrientation(context: Context, orientation: Int) {
    context.findActivity()?.requestedOrientation = orientation
}

fun formatViewCount(views: Int?): String {
    return views.formatViewCount()
}

fun formatTimeAgo(dateString: String?): String {
    return dateString.formatTimeAgo()
}

fun hideSystemBars(activity: Activity) {
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun showSystemBars(activity: Activity) {
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

fun extractId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?")

fun isNetworkAvailable(context: Context): Boolean {
    return NetworkMonitor.isNetworkAvailable(context)
}

fun getCacheSize(context: Context): Long {
    return getDirSize(context.cacheDir)
}

private fun getDirSize(dir: File): Long {
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getDirSize(file) else file.length()
    }
    return size
}

fun clearAppCache(context: Context) {
    deleteDir(context.cacheDir)
}

private fun deleteDir(dir: File) {
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) deleteDir(file)
        file.delete()
    }
}

fun formatFileSize(bytes: Long): String {
    return bytes.formatFileSize()
}

fun switchAppIcon(context: Context, iconMode: String) {
    val useLightIcon = when (iconMode) {
        "default", "light" -> true
        "dark" -> false
        else -> {
            val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            uiMode != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    val pm = context.packageManager
    val defaultComponent = android.content.ComponentName(context, "com.santiago43rus.rupoop.MainActivity")
    val lightComponent = android.content.ComponentName(context, "com.santiago43rus.rupoop.MainActivityLight")

    pm.setComponentEnabledSetting(
        defaultComponent,
        if (useLightIcon) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        android.content.pm.PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
        lightComponent,
        if (useLightIcon) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP
    )
}
