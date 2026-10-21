package com.santiago43rus.rupoop.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.santiago43rus.rupoop.network.NetworkMonitor
import java.io.File

enum class PlayerState { CLOSED, MINI, FULL }
enum class OverlayState { SEARCH, AUTHOR }
enum class NavItem { HOME, LIVE, SUBSCRIPTIONS, LIBRARY }
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

fun Context.isTv(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
}

fun isAutoRotationEnabled(context: Context): Boolean {
    return try {
        android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1
    } catch (_: Exception) {
        false
    }
}

fun formatViewCount(views: Int?): String {
    return views.formatViewCount()
}

fun formatTimeAgo(dateString: String?): String {
    return dateString.formatTimeAgo()
}

fun hideSystemBars(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val params = activity.window.attributes
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        activity.window.attributes = params
    } else {
        val params = activity.window.attributes
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        activity.window.attributes = params
    }
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
    val isEaster = iconMode == "easter" || iconMode == "secret"
    val useLightIcon = when (iconMode) {
        "default", "light" -> true
        "dark" -> false
        "easter", "secret" -> false
        else -> {
            val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            uiMode != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    val pm = context.packageManager
    val defaultComponent = android.content.ComponentName(context, "com.santiago43rus.rupoop.MainActivity")
    val lightComponent = android.content.ComponentName(context, "com.santiago43rus.rupoop.MainActivityLight")
    val easterComponent = android.content.ComponentName(context, "com.santiago43rus.rupoop.MainActivityEaster")

    val defaultState = if (!isEaster && !useLightIcon) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val lightState = if (!isEaster && useLightIcon) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val easterState = if (isEaster) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    pm.setComponentEnabledSetting(defaultComponent, defaultState, android.content.pm.PackageManager.DONT_KILL_APP)
    pm.setComponentEnabledSetting(lightComponent, lightState, android.content.pm.PackageManager.DONT_KILL_APP)
    pm.setComponentEnabledSetting(easterComponent, easterState, android.content.pm.PackageManager.DONT_KILL_APP)
}

fun generateQrCodeBitmap(content: String, sizePx: Int = 512): android.graphics.Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
