package com.santiago43rus.rupoop.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
    Text("Кэш и хранилище", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text("Размер кэша") },
            supportingContent = { Text(formatFileSize(cacheSize)) },
            trailingContent = {
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text("Очистить", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    // ── История и конфиденциальность ──
    Text("История и конфиденциальность", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onClearWatchHistory,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) { Text("Очистить историю просмотра", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onClearSearchHistory,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) { Text("Очистить историю поиска", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) }
}

@Composable
fun SupportAndAboutSection(
    uriHandler: UriHandler,
    donateUrl: String,
    versionName: String,
    versionCode: Int,
    onVersionClick: () -> Unit = {}
) {
    // ── Поддержать автора ──
    Text("Поддержать автора", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(
        "Если приложение полезно — поддержите разработку ❤️",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(8.dp))

    // Донат
    if (donateUrl.isNotBlank()) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("Поддержать рублём") },
                supportingContent = { Text("CloudTips — быстрый перевод") },
                leadingContent = { Icon(Icons.Default.CreditCard, null, tint = Color(0xFFFFEB3B)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    try {
                        val url = donateUrl.trim()
                        val schemeEnd = url.indexOf("://")
                        val normalizedUrl = if (schemeEnd > 0) {
                            url.substring(0, schemeEnd).lowercase() + url.substring(schemeEnd)
                        } else url
                        uriHandler.openUri(normalizedUrl)
                    } catch (_: Exception) { /* Не удалось открыть ссылку */ }
                }
            )
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    // ── О приложении ──
    Text("О приложении", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            ListItem(
                headlineContent = { Text("Версия") },
                supportingContent = { Text("$versionName ($versionCode)") },
                leadingContent = { Icon(Icons.Default.Info, null, tint = Color.Gray) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onVersionClick() }
            )
            ListItem(
                headlineContent = { Text("Telegram-канал") },
                supportingContent = { Text("t.me/rupoopapp • Сырые, тестовые и бета-сборки") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://t.me/rupoopapp")
                }
            )
            ListItem(
                headlineContent = { Text("Исходный код") },
                supportingContent = { Text("GitHub") },
                leadingContent = { Icon(Icons.Default.Code, null, tint = Color.Gray) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/santiago43rus/Rupoop")
                }
            )
            ListItem(
                headlineContent = { Text("Лицензия") },
                supportingContent = { Text("MIT License") },
                leadingContent = { Icon(Icons.Default.Description, null, tint = Color.Gray) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}
