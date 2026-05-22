package com.santiago43rus.rupoop.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rupoop_settings", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit { putString("access_token", value) }

    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_mode", value) }

    @Deprecated("Use themeMode instead", ReplaceWith("themeMode == \"dark\""))
    var isDarkTheme: Boolean
        get() = themeMode == "dark"
        set(value) {
            themeMode = if (value) "dark" else "light"
        }

    var downloadQuality: String
        get() = prefs.getString("download_quality", "1080") ?: "1080"
        set(value) = prefs.edit { putString("download_quality", value) }

    var syncFrequencyHours: Int
        get() = prefs.getInt("sync_frequency", 24)
        set(value) = prefs.edit { putInt("sync_frequency", value) }

    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0)
        set(value) = prefs.edit { putLong("last_sync_time", value) }

    var cachedGistId: String?
        get() = prefs.getString("cached_gist_id", null)
        set(value) = prefs.edit { putString("cached_gist_id", value) }

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(value) = prefs.edit { putBoolean("is_first_launch", value) }

    var adultContentEnabled: Boolean
        get() = prefs.getBoolean("adult_content", true)
        set(value) = prefs.edit { putBoolean("adult_content", value) }

    var kidsContentEnabled: Boolean
        get() = prefs.getBoolean("kids_content", true)
        set(value) = prefs.edit { putBoolean("kids_content", value) }

    var appIcon: String
        get() = prefs.getString("app_icon", "system") ?: "system"
        set(value) = prefs.edit { putString("app_icon", value) }

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("auto_play_next", true)
        set(value) = prefs.edit { putBoolean("auto_play_next", value) }

    var doubleTapSeekDuration: Int
        get() = prefs.getInt("double_tap_seek", 10)
        set(value) = prefs.edit { putInt("double_tap_seek", value) }

    // Sub-genre categories stored as comma-separated string
    var enabledGenres: Set<String>
        get() = prefs.getStringSet("enabled_genres", null) ?: setOf(
            "аниме", "боевики", "комедии", "фантастика", "ужасы",
            "драма", "документальные", "мультфильмы", "мультсериалы", "сериалы"
        )
        set(value) = prefs.edit { putStringSet("enabled_genres", value) }

    fun clearAuth() {
        prefs.edit {
            remove("access_token")
            remove("cached_gist_id")
        }
    }
}
