package com.santiago43rus.rupoop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RupoopRed,
    onPrimary = Color.White,
    secondary = RupoopRedDark,
    tertiary = Color(0xFF66BB6A),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF3A3A3A)
)

private val LightColorScheme = lightColorScheme(
    primary = RupoopRed,
    onPrimary = Color.White,
    secondary = RupoopRedDark,
    tertiary = Color(0xFF388E3C),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFD0D0D0)
)

private val EasterEggColorScheme = darkColorScheme(
    primary = EasterPrimary,
    onPrimary = Color.Black,
    secondary = EasterPrimaryDark,
    tertiary = Color(0xFF69F0AE),
    background = EasterBackground,
    surface = EasterSurface,
    surfaceVariant = EasterSurfaceVariant,
    onBackground = EasterOnBackground,
    onSurface = EasterOnSurface,
    onSurfaceVariant = EasterOnSurfaceVariant,
    outline = EasterOutline
)

@Composable
fun RupoopTheme(
    themeMode: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isEaster = themeMode == "easter" || themeMode == "secret"
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        "easter", "secret" -> true
        else -> darkTheme
    }

    val colorScheme = when {
        isEaster -> EasterEggColorScheme
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val selectionColor = if (isEaster) EasterPrimary else RupoopRed
    val customTextSelectionColors = TextSelectionColors(
        handleColor = selectionColor,
        backgroundColor = selectionColor.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
