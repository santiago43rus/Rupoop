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
import java.text.SimpleDateFormat
import java.util.TimeZone

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

fun formatViewCount(views: Int?): String {
    if (views == null || views == 0) return "Нет просмотров"
    return when {
        views >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f млн просмотров", views / 1_000_000.0).replace(",0", "").replace(".0", "")
        views >= 1_000 -> String.format(Locale.getDefault(), "%.1f тыс. просмотров", views / 1_000.0).replace(",0", "").replace(".0", "")
        else -> "$views ${getPlural(views, "просмотр", "просмотра", "просмотров")}"
    }
}

fun formatTimeAgo(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return ""
    try {
        var cleanDateStr = dateString
        if (cleanDateStr.contains(".")) {
            cleanDateStr = cleanDateStr.substringBefore(".")
        } else if (cleanDateStr.endsWith("Z")) {
            cleanDateStr = cleanDateStr.dropLast(1)
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        // Rutube API usually returns MSK time (UTC+3)
        format.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        val parsedDate = format.parse(cleanDateStr) ?: return ""
        var diff = System.currentTimeMillis() - parsedDate.time
        
        // Prevent negative time display if local clock differs
        if (diff < 0) diff = 0
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365
        
        return when {
            years > 0 -> "${years.toInt()} ${getPlural(years.toInt(), "год", "года", "лет")} назад"
            months > 0 -> "${months.toInt()} ${getPlural(months.toInt(), "месяц", "месяца", "месяцев")} назад"
            days > 0 -> "${days.toInt()} ${getPlural(days.toInt(), "день", "дня", "дней")} назад"
            hours > 0 -> "${hours.toInt()} ${getPlural(hours.toInt(), "час", "часа", "часов")} назад"
            minutes > 0 -> "${minutes.toInt()} ${getPlural(minutes.toInt(), "минуту", "минуты", "минут")} назад"
            else -> "Только что"
        }
    } catch (e: Exception) {
        return ""
    }
}

private fun getPlural(n: Int, form1: String, form2: String, form5: String): String {
    val n10 = n % 10
    val n100 = n % 100
    if (n10 == 1 && n100 != 11) return form1
    if (n10 in 2..4 && !(n100 in 12..14)) return form2
    return form5
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
