package com.example.rupoop

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rupoop_settings", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    var downloadQuality: String
        get() = prefs.getString("download_quality", "1080") ?: "1080"
        set(value) = prefs.edit().putString("download_quality", value).apply()

    var syncFrequencyHours: Int
        get() = prefs.getInt("sync_frequency", 24)
        set(value) = prefs.edit().putInt("sync_frequency", value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0)
        set(value) = prefs.edit().putLong("last_sync_time", value).apply()

    var cachedGistId: String?
        get() = prefs.getString("cached_gist_id", null)
        set(value) = prefs.edit().putString("cached_gist_id", value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(value) = prefs.edit().putBoolean("is_first_launch", value).apply()

    var adultContentEnabled: Boolean
        get() = prefs.getBoolean("adult_content", true)
        set(value) = prefs.edit().putBoolean("adult_content", value).apply()

    var kidsContentEnabled: Boolean
        get() = prefs.getBoolean("kids_content", true)
        set(value) = prefs.edit().putBoolean("kids_content", value).apply()

    fun clearAuth() {
        prefs.edit().remove("access_token").remove("cached_gist_id").apply()
    }
}
