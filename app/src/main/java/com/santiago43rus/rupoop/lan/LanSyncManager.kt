package com.santiago43rus.rupoop.lan

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface

sealed interface LanStatus {
    object Disabled : LanStatus
    object NoPermission : LanStatus
    /** Включено, но приложение не на экране: сервер выключен. */
    object Idle : LanStatus
    class Active(val ip: String?, val port: Int) : LanStatus
    class Error(val text: String) : LanStatus
}

class PairRequest(val name: String, val id: String) {
    val decision = CompletableDeferred<Boolean>()
}

class OfferState(val link: String, val expiresAtMs: Long)

/**
 * Главный объект LAN-синхронизации. Один на приложение (создаётся в AppViewModel).
 * Сервер работает, пока приложение на экране (start/stop из MainActivity).
 */
class LanSyncManager(
    context: Context,
    private val registryManager: UserRegistryManager,
    private val settingsManager: com.santiago43rus.rupoop.data.SettingsManager,
    private val scope: CoroutineScope,
    onRegistryChanged: (UserRegistry) -> Unit
) {
    private val appContext = context.applicationContext
    val prefs = LanPrefs(appContext)
    private val identity = prefs.identity()
    private val access = AppRegistryAccess(registryManager, settingsManager, RetrofitClient.json, onRegistryChanged)
    private val client = LanClient(identity, prefs, access)
    private val server = LanServer(
        identity, prefs, access,
        onPairRequest = { name, id -> askUser(name, id) },
        onPaired = { onPaired() },
        log = { Log.d(TAG, it) }
    )

    val status = MutableStateFlow<LanStatus>(if (prefs.enabled) LanStatus.Idle else LanStatus.Disabled)
    val peers = MutableStateFlow(prefs.all())
    val offer = MutableStateFlow<OfferState?>(null)
    val pendingPair = MutableStateFlow<PairRequest?>(null)
    val info = MutableStateFlow("")

    private var running = false
    private var loop: Job? = null
    private var debounce: Job? = null
    private val syncMutex = Mutex()

    val deviceName: String get() = identity.name

    // ───── разрешение (Android 17+, targetSdk 37) ─────
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(appContext, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Включить или выключить. Возвращает true, если сначала нужно запросить разрешение. */
    fun setEnabled(on: Boolean): Boolean {
        prefs.enabled = on
        if (!on) { stop(); status.value = LanStatus.Disabled; return false }
        if (!hasPermission()) { status.value = LanStatus.NoPermission; return true }
        start()
        return false
    }

    // ───── жизненный цикл ─────
    @Synchronized
    fun start() {
        if (!prefs.enabled) { status.value = LanStatus.Disabled; return }
        if (!hasPermission()) { status.value = LanStatus.NoPermission; return }
        if (running) return
        try {
            val port = server.start()
            running = true
            status.value = LanStatus.Active(localIpv4(), port)
            registryManager.onLocalChange = { requestSync() }
            loop = scope.launch {
                while (isActive) {
                    syncAll()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            status.value = LanStatus.Error(e.javaClass.simpleName)
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        loop?.cancel(); debounce?.cancel()
        registryManager.onLocalChange = null
        server.stop()
        offer.value = null
        status.value = if (prefs.enabled) LanStatus.Idle else LanStatus.Disabled
    }

    // ───── синхронизация ─────
    /** Вызывается после каждого локального изменения. Отправка через 15 с тишины. */
    fun requestSync() {
        if (!running) return
        debounce?.cancel()
        debounce = scope.launch { delay(DEBOUNCE_MS); syncAll() }
    }

    fun syncNow() { scope.launch { syncAll() } }

    private suspend fun syncAll() {
        if (!running) return
        syncMutex.withLock {
            withContext(Dispatchers.IO) { prefs.all().forEach { syncPeer(it) } }
            peers.value = prefs.all()
        }
    }

    private suspend fun syncPeer(p: LanPeer) {
        val host = p.host
        if (host == null || p.port <= 0) return
        val out = client.syncOnce(p, host, p.port, myPort = server.port)
        when (out) {
            is SyncOutcome.Merged, is SyncOutcome.Same -> info.value = "«${p.name}»: синхронизировано"
            is SyncOutcome.Forgotten, is SyncOutcome.KeyMismatch ->
                info.value = "«${p.name}» не принимает это устройство. Удалите его в списке и сопрягите заново"
            else -> Log.d(TAG, "peer ${p.name} unreachable")
        }
    }

    // ───── сопряжение ─────
    /** Показать QR (ТВ или второй телефон). Возвращает false, если сервер не запущен. */
    fun startPairingOffer(): Boolean {
        if (!prefs.enabled) prefs.enabled = true
        start()
        val ip = localIpv4()
        if (!running || ip == null) return false
        val o = PairingOffer(LanCrypto.randomBytes(32), System.currentTimeMillis() + OFFER_TTL_MS)
        server.offer = o
        val link = PairingQr.build(PairingQr.Data(identity.idHex, ip, server.port, o.key, identity.name))
        offer.value = OfferState(link, o.expiresAtMs)
        scope.launch {
            delay(OFFER_TTL_MS)
            if (server.offer === o) server.offer = null
            if (offer.value?.link == link) offer.value = null
        }
        return true
    }

    fun cancelOffer() { server.offer = null; offer.value = null }

    /** Сопряжение по тексту из QR (сканер в приложении или ссылка из системной камеры). */
    suspend fun pairWithLink(text: String): String {
        val qr = PairingQr.parse(text) ?: return "Это не QR-код Rupoop"
        if (!prefs.enabled) prefs.enabled = true
        if (!hasPermission()) { status.value = LanStatus.NoPermission; return "Нужно разрешение на доступ к локальной сети" }
        start()
        if (!running) return "Не удалось запустить синхронизацию"
        val r = withContext(Dispatchers.IO) { client.pair(qr, server.port) }
        return when (r) {
            is PairOutcome.Paired -> { peers.value = prefs.all(); syncNow(); "Подключено: ${r.peer.name}" }
            is PairOutcome.Rejected -> "Подключение отклонено на втором устройстве"
            is PairOutcome.Expired -> "QR устарел или недоступен. Покажите новый QR и повторите"
            is PairOutcome.Failed -> "Не удалось подключиться (${r.reason}). Оба устройства в одной Wi‑Fi сети?"
        }
    }

    private fun askUser(name: String, id: String): Boolean {
        val req = PairRequest(name, id)
        pendingPair.value = req
        return try {
            runBlocking { withTimeoutOrNull(60_000) { req.decision.await() } } ?: false
        } finally {
            pendingPair.value = null
        }
    }

    private fun onPaired() {
        offer.value = null
        peers.value = prefs.all()
        syncNow()
    }

    fun removePeer(id: String) {
        scope.launch(Dispatchers.IO) {
            prefs.get(id)?.let { client.unpair(it, it.host, it.port) }
            peers.value = prefs.all()
        }
    }

    fun localIpv4(): String? {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            val a = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (a != null) return a.hostAddress
        } catch (_: Exception) {}
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        } catch (_: Exception) { null }
    }

    companion object {
        const val TAG = "RupoopLan"
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val SYNC_INTERVAL_MS = 60_000L
        private const val DEBOUNCE_MS = 15_000L
        private const val OFFER_TTL_MS = 120_000L
    }
}
