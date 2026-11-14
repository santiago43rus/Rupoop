package com.santiago43rus.rupoop.lan

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** Сопряжённое устройство. [key] — общий ключ пары, получен при сопряжении. */
data class LanPeer(
    val deviceId: String,          // 32 hex
    val name: String,
    val key: ByteArray,
    val host: String? = null,
    val port: Int = 0,
    val lastSyncAt: Long = 0L,
    val lastHash: String? = null
)

interface PeerStore {
    fun all(): List<LanPeer>
    fun get(deviceId: String): LanPeer?
    fun put(peer: LanPeer)
    fun remove(deviceId: String)
}

/** Мост между сетевым кодом и реестром. Реализация в приложении сама уходит на нужный поток. */
interface RegistryAccess {
    /** Хэш синхронизируемой части реестра. */
    fun hash(): String
    /** Текущий реестр в JSON. */
    fun snapshot(): String
    /** Слить удалённый реестр в локальный, применить и вернуть результат в JSON. */
    fun mergeAndApply(remoteJson: String): String
}

fun interface PairDecision {
    /** Блокирующий вызов: спросить пользователя на экране. true = подключить. */
    fun onRequest(peerName: String, peerIdHex: String): Boolean
}

class LanIdentity(val deviceId: ByteArray, val name: String) {
    val idHex: String get() = LanCrypto.hex(deviceId)
}

/** Действующий QR: одноразовый ключ и срок жизни. Хранится только в памяти ТВ. */
class PairingOffer(val key: ByteArray, val expiresAtMs: Long) {
    @Volatile var failures = 0
    @Volatile var busy = false
    @Volatile var used = false
}

/** Содержимое QR: rupoop-sync://pair?v=1&id=..&h=..&p=..&k=..&n=.. */
object PairingQr {
    const val SCHEME = "rupoop-sync"

    class Data(val deviceId: String, val host: String, val port: Int, val key: ByteArray, val name: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    fun build(d: Data): String {
        val k = Base64.getUrlEncoder().withoutPadding().encodeToString(d.key)
        return "$SCHEME://pair?v=1&id=${d.deviceId}&h=${enc(d.host)}&p=${d.port}&k=$k&n=${enc(d.name.take(32))}"
    }

    /** Только адреса локальной сети: 10/8, 172.16/12, 192.168/16, 169.254/16. */
    fun isPrivateIpv4(h: String): Boolean {
        val p = h.split(".")
        if (p.size != 4) return false
        val n = p.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
    }

    fun parse(text: String): Data? {
        return try {
            val t = text.trim()
            if (!t.startsWith("$SCHEME://pair?")) return null
            val q = t.substringAfter('?').split("&").associate {
                val i = it.indexOf('=')
                if (i < 0) it to "" else it.substring(0, i) to dec(it.substring(i + 1))
            }
            if (q["v"] != "1") return null
            val id = q["id"] ?: return null
            if (id.length != 32 || !id.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val key = Base64.getUrlDecoder().decode(q["k"] ?: return null)
            if (key.size != 32) return null
            val port = q["p"]?.toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            val host = q["h"]?.takeIf { isPrivateIpv4(it) } ?: return null
            Data(id, host, port, key, q["n"] ?: "ТВ")
        } catch (_: Exception) {
            null
        }
    }
}
