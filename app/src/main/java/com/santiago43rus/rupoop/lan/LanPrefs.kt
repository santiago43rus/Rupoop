package com.santiago43rus.rupoop.lan

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Настройки LAN, идентичность устройства и список сопряжённых. Приватные SharedPreferences приложения. */
class LanPrefs(context: Context) : PeerStore {
    private val prefs = context.getSharedPreferences("rupoop_lan", Context.MODE_PRIVATE)
    private val lock = Any()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit { putBoolean("enabled", v) }

    fun identity(): LanIdentity {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = LanCrypto.hex(LanCrypto.randomBytes(16))
            prefs.edit { putString("device_id", id) }
        }
        val name = prefs.getString("device_name", null) ?: (Build.MODEL ?: "Android")
        return LanIdentity(LanCrypto.unhex(id), name)
    }

    override fun all(): List<LanPeer> = synchronized(lock) { read() }

    override fun get(deviceId: String): LanPeer? = synchronized(lock) { read().firstOrNull { it.deviceId == deviceId } }

    override fun put(peer: LanPeer) = synchronized(lock) {
        write(read().filterNot { it.deviceId == peer.deviceId } + peer)
    }

    override fun remove(deviceId: String) = synchronized(lock) {
        write(read().filterNot { it.deviceId == deviceId })
    }

    private fun read(): List<LanPeer> {
        return try {
            val arr = JSONArray(prefs.getString("peers", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LanPeer(
                    deviceId = o.getString("id"),
                    name = o.getString("name"),
                    key = Base64.getDecoder().decode(o.getString("key")),
                    host = o.optString("host").ifBlank { null },
                    port = o.optInt("port", 0),
                    lastSyncAt = o.optLong("last", 0L),
                    lastHash = o.optString("hash").ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<LanPeer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.deviceId); put("name", it.name)
                put("key", Base64.getEncoder().encodeToString(it.key))
                put("host", it.host ?: ""); put("port", it.port)
                put("last", it.lastSyncAt); put("hash", it.lastHash ?: "")
            })
        }
        prefs.edit { putString("peers", arr.toString()) }
    }
}
