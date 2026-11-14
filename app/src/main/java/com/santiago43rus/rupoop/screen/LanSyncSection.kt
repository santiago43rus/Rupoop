package com.santiago43rus.rupoop.screen

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.lan.LanQrScanner
import com.santiago43rus.rupoop.lan.LanStatus
import com.santiago43rus.rupoop.util.generateQrCodeBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

fun lanStatusText(s: LanStatus): String = when (s) {
    is LanStatus.Disabled -> "Выключено"
    is LanStatus.NoPermission -> "Нужно разрешение на доступ к локальной сети"
    is LanStatus.Idle -> "Включено. Работает, пока приложение открыто"
    is LanStatus.Active -> "Готово: ${s.ip ?: "нет Wi‑Fi"}:${s.port}"
    is LanStatus.Error -> "Ошибка: ${s.text}"
}

@Composable
fun LanSyncSection(vm: AppViewModel) {
    val lan = vm.lan
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by lan.status.collectAsState()
    val peers by lan.peers.collectAsState()
    val info by lan.info.collectAsState()
    val offer by lan.offer.collectAsState()
    val pending by lan.pendingPair.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }

    Text("Синхронизация по Wi‑Fi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(
        "Телефон и ТВ в одной сети. Данные идут напрямую между устройствами, без интернета. Оба приложения должны быть открыты.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Включить", style = MaterialTheme.typography.bodyLarge)
            Text(lanStatusText(status), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = status !is LanStatus.Disabled,
            onCheckedChange = { on -> if (lan.setEnabled(on)) vm.onRequestLanPermission?.invoke() }
        )
    }
    if (status is LanStatus.NoPermission) {
        Button(onClick = { vm.onRequestLanPermission?.invoke() }, modifier = Modifier.fillMaxWidth()) { Text("Выдать разрешение") }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { scanning = true }, modifier = Modifier.weight(1f)) { Text("Сканировать QR") }
        OutlinedButton(onClick = { if (!lan.startPairingOffer()) resultText = "Нет Wi‑Fi или нет разрешения" }, modifier = Modifier.weight(1f)) { Text("Показать QR") }
    }
    if (resultText.isNotBlank()) Text(resultText, style = MaterialTheme.typography.bodySmall)
    if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)

    peers.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                val last = if (p.lastSyncAt == 0L) "ещё не синхронизировались"
                else "синхронизация: " + DateFormat.getTimeFormat(context).format(Date(p.lastSyncAt))
                Text(last, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { lan.removePeer(p.deviceId) }) { Text("Удалить") }
        }
    }
    if (peers.isNotEmpty()) TextButton(onClick = { lan.syncNow() }) { Text("Синхронизировать сейчас") }

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                LanQrScanner(
                    onResult = { text ->
                        scanning = false
                        scope.launch { resultText = lan.pairWithLink(text) }
                    },
                    onClose = { scanning = false }
                )
            }
        }
    }

    offer?.let { o ->
        var left by remember(o.link) { mutableIntStateOf(((o.expiresAtMs - System.currentTimeMillis()) / 1000).toInt()) }
        LaunchedEffect(o.link) { while (left > 0) { delay(1000); left-- } }
        val bmp = remember(o.link) { generateQrCodeBitmap(o.link, 640) }
        AlertDialog(
            onDismissRequest = { lan.cancelOffer() },
            title = { Text("Отсканируйте QR на втором устройстве") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(260.dp))
                    Text("Действует ещё $left с")
                }
            },
            confirmButton = { TextButton(onClick = { lan.cancelOffer() }) { Text("Отмена") } }
        )
    }

    pending?.let { req ->
        AlertDialog(
            onDismissRequest = { req.decision.complete(false) },
            title = { Text("Подключить устройство?") },
            text = { Text("«${req.name}» просит подключиться и синхронизировать данные.") },
            confirmButton = { TextButton(onClick = { req.decision.complete(true) }) { Text("Подключить") } },
            dismissButton = { TextButton(onClick = { req.decision.complete(false) }) { Text("Отклонить") } }
        )
    }
}
