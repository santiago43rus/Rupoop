package com.santiago43rus.rupoop.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiago43rus.rupoop.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val showDownloadNotifications = vm.showDownloadNotifications
    val showBackgroundNotifications = vm.showBackgroundNotifications

    val isAllOn = showDownloadNotifications || showBackgroundNotifications

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    // Master switch to toggle all notifications
                    Switch(
                        checked = isAllOn,
                        onCheckedChange = { checked ->
                            vm.updateDownloadNotifications(checked)
                            vm.updateBackgroundNotifications(checked)
                            onSettingsChanged()
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                ListItem(
                    headlineContent = { Text("Загрузка видео") },
                    supportingContent = { Text("Показывать уведомления о процессе и завершении скачивания видео") },
                    trailingContent = {
                        Switch(
                            checked = showDownloadNotifications,
                            onCheckedChange = { checked ->
                                vm.updateDownloadNotifications(checked)
                                onSettingsChanged()
                            }
                        )
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text("Фоновое воспроизведение") },
                    supportingContent = { Text("Показывать элементы управления плеером в области уведомлений") },
                    trailingContent = {
                        Switch(
                            checked = showBackgroundNotifications,
                            onCheckedChange = { checked ->
                                vm.updateBackgroundNotifications(checked)
                                onSettingsChanged()
                            }
                        )
                    }
                )
            }
        }
    }
}
