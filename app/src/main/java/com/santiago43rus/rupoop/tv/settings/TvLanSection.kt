@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.santiago43rus.rupoop.tv.settings

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.lan.LanStatus
import com.santiago43rus.rupoop.screen.lanStatusText
import com.santiago43rus.rupoop.util.generateQrCodeBitmap
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun TvLanSection(vm: AppViewModel) {
    val lan = vm.lan
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val status by lan.status.collectAsState()
    val peers by lan.peers.collectAsState()
    val info by lan.info.collectAsState()
    val offer by lan.offer.collectAsState()
    val pending by lan.pendingPair.collectAsState()

    @Composable
    fun colors() = ButtonDefaults.colors(
        containerColor = colorScheme.surfaceVariant,
        contentColor = colorScheme.onSurface,
        focusedContainerColor = colorScheme.primary,
        focusedContentColor = colorScheme.onPrimary
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Синхронизация по Wi‑Fi", color = colorScheme.primary, fontSize = 18.sp)
        Text(lanStatusText(status), color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            "Откройте Rupoop на телефоне в той же сети: Настройки, Синхронизация по Wi‑Fi, Сканировать QR.",
            color = colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val enabled = status !is LanStatus.Disabled
                Button(onClick = { if (lan.setEnabled(!enabled)) vm.onRequestLanPermission?.invoke() }, colors = colors()) {
                    Text(if (enabled) "Выключить" else "Включить")
                }
            }
            if (status is LanStatus.NoPermission) item {
                Button(onClick = { vm.onRequestLanPermission?.invoke() }, colors = colors()) { Text("Выдать разрешение") }
            }
            item { Button(onClick = { lan.startPairingOffer() }, colors = colors()) { Text("Добавить телефон (QR)") } }
            if (peers.isNotEmpty()) item { Button(onClick = { lan.syncNow() }, colors = colors()) { Text("Синхронизировать") } }
        }
        if (info.isNotBlank()) Text(info, color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
        peers.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val last = if (p.lastSyncAt == 0L) "ещё не синхронизировались"
                else "синхронизация: " + DateFormat.getTimeFormat(context).format(Date(p.lastSyncAt))
                Text("${p.name} ($last)", color = colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Button(onClick = { lan.removePeer(p.deviceId) }, colors = colors()) { Text("Удалить") }
            }
        }
    }

    offer?.let { o ->
        var left by remember(o.link) { mutableIntStateOf(((o.expiresAtMs - System.currentTimeMillis()) / 1000).toInt()) }
        LaunchedEffect(o.link) { while (left > 0) { delay(1000); left-- } }
        val bmp = remember(o.link) { generateQrCodeBitmap(o.link, 640) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Dialog(onDismissRequest = { lan.cancelOffer() }) {
            Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = colorScheme.surface)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Отсканируйте QR в Rupoop на телефоне", fontSize = 18.sp, color = colorScheme.onSurface)
                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(320.dp))
                    Text("Действует ещё $left с", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Button(onClick = { lan.cancelOffer() }, colors = colors(), modifier = Modifier.focusRequester(focus)) { Text("Отмена") }
                }
            }
        }
    }

    pending?.let { req ->
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Dialog(onDismissRequest = { req.decision.complete(false) }) {
            Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = colorScheme.surface)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Подключить устройство «${req.name}»?", fontSize = 18.sp, color = colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { req.decision.complete(true) }, colors = colors(), modifier = Modifier.focusRequester(focus)) { Text("Подключить") }
                        Button(onClick = { req.decision.complete(false) }, colors = colors()) { Text("Отклонить") }
                    }
                }
            }
        }
    }
}
