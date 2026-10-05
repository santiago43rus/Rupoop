package com.santiago43rus.rupoop.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val registryManager: UserRegistryManager,
    private val settingsManager: SettingsManager
) {
    private val json = RetrofitClient.json

    fun exportBackupJson(): String {
        val appSettings = settingsManager.toAppSettings()
        val fullRegistry = registryManager.registry.copy(
            appSettings = appSettings,
            lastSynced = System.currentTimeMillis()
        )
        registryManager.updateRegistry(fullRegistry)
        return json.encodeToString(fullRegistry)
    }

    fun writeBackupToStream(outputStream: OutputStream): Boolean {
        return try {
            val content = exportBackupJson()
            outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createShareIntent(context: Context): Intent {
        val backupJson = exportBackupJson()
        val cacheFile = File(context.cacheDir, "rupoop_backup.json")
        cacheFile.writeText(backupJson, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Резервная копия Rupoop")
            putExtra(Intent.EXTRA_TEXT, "Резервная копия Rupoop (настройки, история, подписки, плейлисты)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun importBackupFromStream(inputStream: InputStream): Result<UserRegistry> {
        return try {
            val content = inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            importBackupJson(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importBackupJson(jsonString: String): Result<UserRegistry> {
        return try {
            val imported = json.decodeFromString<UserRegistry>(jsonString)
            registryManager.updateRegistry(imported)
            settingsManager.applyAppSettings(imported.appSettings)
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
