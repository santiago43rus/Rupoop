package com.santiago43rus.rupoop.lan

import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Мост к реестру приложения. Синхронизирует данные и настройки приложения по локальной сети.
 * Слияние и запись выполняются на главном потоке.
 */
class AppRegistryAccess(
    private val registryManager: UserRegistryManager,
    private val settingsManager: SettingsManager,
    private val json: Json,
    private val onApplied: (UserRegistry) -> Unit
) : RegistryAccess {

    private fun forWire(r: UserRegistry): UserRegistry {
        val currentSettings = settingsManager.toAppSettings()
        return r.copy(
            appSettings = currentSettings,
            lastSynced = if (r.lastSynced == 0L) System.currentTimeMillis() else r.lastSynced
        )
    }

    override fun hash(): String =
        LanCrypto.hex(LanCrypto.sha256(json.encodeToString(forWire(registryManager.registry)).toByteArray()))

    override fun snapshot(): String {
        val currentSettings = settingsManager.toAppSettings()
        val currentWithSettings = registryManager.registry.copy(appSettings = currentSettings)
        registryManager.updateRegistry(currentWithSettings)
        return json.encodeToString(forWire(currentWithSettings))
    }

    override fun mergeAndApply(remoteJson: String): String {
        val remote = json.decodeFromString<UserRegistry>(remoteJson)
        val merged = runBlocking(Dispatchers.Main.immediate) {
            val currentSettings = settingsManager.toAppSettings()
            val currentWithSettings = registryManager.registry.copy(appSettings = currentSettings)
            registryManager.updateRegistry(currentWithSettings)
            val m = registryManager.mergeWith(remote)
            settingsManager.applyAppSettings(m.appSettings)
            onApplied(m)
            m
        }
        return json.encodeToString(forWire(merged))
    }
}
