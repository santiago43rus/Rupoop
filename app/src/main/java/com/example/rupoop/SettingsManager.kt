package com.example.rupoop

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("is_dark_theme", true)
        set(value) = prefs.edit().putBoolean("is_dark_theme", value).apply()
}