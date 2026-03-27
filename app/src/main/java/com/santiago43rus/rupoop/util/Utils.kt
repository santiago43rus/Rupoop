package com.santiago43rus.rupoop.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Locale

enum class PlayerState { CLOSED, MINI, FULL }
enum class OverlayState { SEARCH, AUTHOR }
enum class NavItem { HOME, SUBSCRIPTIONS, LIBRARY }
enum class LibrarySubScreen { NONE, LIKED, WATCH_LATER, PLAYLISTS, PLAYLIST_DETAIL, HISTORY, DOWNLOADS }

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
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun showSystemBars(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

fun extractId(url: String): String? = url.split("/").lastOrNull { it.isNotEmpty() }

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.1f ГБ", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f МБ", mb)
        else -> String.format(Locale.getDefault(), "%.0f КБ", kb)
    }
}

fun switchAppIcon(context: Context, useLightIcon: Boolean) {
    val pm = context.packageManager
    val defaultComponent = ComponentName(context, "com.santiago43rus.rupoop.MainActivity")
    val lightComponent = ComponentName(context, "com.santiago43rus.rupoop.MainActivityLight")
    
    pm.setComponentEnabledSetting(
        defaultComponent,
        if (useLightIcon) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
        lightComponent,
        if (useLightIcon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
}

