package com.santiago43rus.rupoop.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.util.formatFileSize

@Composable
fun CacheAndHistorySection(
    context: Context,
    cacheSize: Long,
    onClearCache: () -> Unit,
    onClearWatchHistory: () -> Unit,
    onClearSearchHistory: () -> Unit
) {
    // ── Кэш ──
    Text("Кэш и хранилище", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
    ListItem(
        headlineContent = { Text("Размер кэша") },
        supportingContent = { Text(formatFileSize(cacheSize)) },
        trailingContent = {
            FilledTonalButton(onClick = onClearCache) {
                Text("Очистить")
            }
        }
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    // ── История и конфиденциальность ──
    Text("История и конфиденциальность", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
    Button(
        onClick = onClearWatchHistory,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    ) { Text("Очистить историю просмотра") }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onClearSearchHistory,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    ) { Text("Очистить историю поиска") }
}

@Composable
fun SupportAndAboutSection(
    uriHandler: UriHandler,
    donateUrl: String,
    versionName: String,
    versionCode: Int
) {
    // ── Поддержать автора ──
    Text("Поддержать автора", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
    Spacer(Modifier.height(4.dp))
    Text(
        "Если приложение полезно — поддержите разработку ❤️",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(8.dp))

    // Донат
    if (donateUrl.isNotBlank()) {
        ListItem(
            headlineContent = { Text("Поддержать рублём") },
            supportingContent = { Text("CloudTips — быстрый перевод") },
            leadingContent = { Icon(Icons.Default.CreditCard, null, tint = Color(0xFFFFEB3B)) },
            modifier = Modifier.clickable {
                try {
                    val url = donateUrl.trim()
                    // Fix: normalize scheme to lowercase (e.g. "Https" -> "https")
                    val schemeEnd = url.indexOf("://")
                    val normalizedUrl = if (schemeEnd > 0) {
                        url.substring(0, schemeEnd).lowercase() + url.substring(schemeEnd)
                    } else url
                    uriHandler.openUri(normalizedUrl)
                } catch (_: Exception) { /* Не удалось открыть ссылку */ }
            }
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    // ── О приложении ──
    Text("О приложении", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
    ListItem(
        headlineContent = { Text("Версия") },
        supportingContent = { Text("$versionName ($versionCode)") },
        leadingContent = { Icon(Icons.Default.Info, null, tint = Color.Gray) }
    )
    ListItem(
        headlineContent = { Text("Исходный код") },
        supportingContent = { Text("GitHub") },
        leadingContent = { Icon(Icons.Default.Code, null, tint = Color.Gray) },
        modifier = Modifier.clickable {
            uriHandler.openUri("https://github.com/santiago43rus/Rupoop")
        }
    )
    ListItem(
        headlineContent = { Text("Лицензия") },
        supportingContent = { Text("MIT License") },
        leadingContent = { Icon(Icons.Default.Description, null, tint = Color.Gray) }
    )
}
