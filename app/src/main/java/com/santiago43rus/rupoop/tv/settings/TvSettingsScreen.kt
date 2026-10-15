package com.santiago43rus.rupoop.tv.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.tv.components.TvSectionTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    adultContentEnabled: Boolean,
    onAdultContentChange: (Boolean) -> Unit,
    isAuthenticated: Boolean,
    githubUserName: String?,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TvSectionTitle(title = "Настройки")
        }

        item {
            Surface(
                onClick = { onAutoPlayNextChange(!autoPlayNext) },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E2228),
                    focusedContainerColor = Color(0xFF282E38)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Автовоспроизведение следующего видео", color = Color.White, fontSize = 16.sp)
                        Text("Автоматически запускать следующее видео из списка", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoPlayNext,
                        onCheckedChange = null
                    )
                }
            }
        }

        item {
            Surface(
                onClick = { onAdultContentChange(!adultContentEnabled) },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E2228),
                    focusedContainerColor = Color(0xFF282E38)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Контент для взрослых (18+)", color = Color.White, fontSize = 16.sp)
                        Text("Отображение контента с возрастным ограничением", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = adultContentEnabled,
                        onCheckedChange = null
                    )
                }
            }
        }

        item {
            Surface(
                onClick = { if (isAuthenticated) onLogout() else onOpenLogin() },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E2228),
                    focusedContainerColor = Color(0xFF282E38)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isAuthenticated) "Аккаунт GitHub: ${githubUserName ?: ""}" else "Вход в GitHub (Device Flow)",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isAuthenticated) "Нажмите, чтобы выйти" else "Синхронизация истории, подписок и плейлистов через код с телефона",
                            color = Color(0xFF58A6FF),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
