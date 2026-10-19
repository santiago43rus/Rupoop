package com.santiago43rus.rupoop.tv.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.santiago43rus.rupoop.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RupoopTvTheme(
    themeMode: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isEaster = themeMode == "easter" || themeMode == "secret"
    val isDark = when (themeMode) {
        "light" -> false
        "dark", "easter", "secret" -> true
        else -> darkTheme
    }

    val mobileColors = when {
        isEaster -> EasterEggColorScheme
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val tvColorScheme: ColorScheme = if (isDark) {
        darkColorScheme(
            primary = mobileColors.primary,
            onPrimary = mobileColors.onPrimary,
            primaryContainer = mobileColors.surfaceVariant,
            onPrimaryContainer = mobileColors.onSurface,
            secondary = mobileColors.secondary,
            onSecondary = mobileColors.onSecondary,
            secondaryContainer = mobileColors.surfaceVariant,
            onSecondaryContainer = mobileColors.onSurface,
            tertiary = mobileColors.tertiary,
            onTertiary = mobileColors.onTertiary,
            background = mobileColors.background,
            onBackground = mobileColors.onBackground,
            surface = mobileColors.surface,
            onSurface = mobileColors.onSurface,
            surfaceVariant = mobileColors.surfaceVariant,
            onSurfaceVariant = mobileColors.onSurfaceVariant,
            inverseSurface = mobileColors.onSurface,
            inverseOnSurface = mobileColors.surface,
            border = mobileColors.outline
        )
    } else {
        lightColorScheme(
            primary = mobileColors.primary,
            onPrimary = mobileColors.onPrimary,
            primaryContainer = mobileColors.surfaceVariant,
            onPrimaryContainer = mobileColors.onSurface,
            secondary = mobileColors.secondary,
            onSecondary = mobileColors.onSecondary,
            secondaryContainer = mobileColors.surfaceVariant,
            onSecondaryContainer = mobileColors.onSurface,
            tertiary = mobileColors.tertiary,
            onTertiary = mobileColors.onTertiary,
            background = mobileColors.background,
            onBackground = mobileColors.onBackground,
            surface = mobileColors.surface,
            onSurface = mobileColors.onSurface,
            surfaceVariant = mobileColors.surfaceVariant,
            onSurfaceVariant = mobileColors.onSurfaceVariant,
            inverseSurface = mobileColors.onSurface,
            inverseOnSurface = mobileColors.surface,
            border = mobileColors.outline
        )
    }

    MaterialTheme(
        colorScheme = tvColorScheme,
        content = content
    )
}
