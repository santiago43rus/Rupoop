package com.santiago43rus.rupoop.auth

import android.util.Log
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.network.GistApi
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

class GistSyncManager(
    private val gistApi: GistApi,
    private val registryManager: UserRegistryManager,
    private val settingsManager: SettingsManager
) {
    private val SYNC_FILE_NAME = "rupoop_user_registry_v2.json"
    private val json = RetrofitClient.json
    private val pushMutex = Mutex()
    private var lastSentLocal: String? = null

    suspend fun pull(token: String): UserRegistry {
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Starting Gist Pull")
        return try {
            val gistId = getOrFindGistId(authHeader)
            if (gistId != null) {
                Log.d("RupoopAuth", "Fetching Gist content: $gistId")
                val syncGist = gistApi.getGist(authHeader, gistId)
                val content = syncGist.files[SYNC_FILE_NAME]?.content ?: "{}"
                val remoteRegistry = try {
                    json.decodeFromString<UserRegistry>(content)
                } catch (e: Exception) {
                    UserRegistry()
                }

                val mergedRegistry = registryManager.mergeWith(remoteRegistry)
                applySettingsToPreferences(mergedRegistry.appSettings)
                mergedRegistry
            } else {
                Log.d("RupoopAuth", "No Gist found on Pull")
                registryManager.registry
            }
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Pull failed", e)
            registryManager.registry
        }
    }

    suspend fun push(token: String): UserRegistry = pushMutex.withLock {
        val localBefore = json.encodeToString(registryManager.registry.copy(appSettings = buildAppSettingsFromPreferences()))
        if (localBefore == lastSentLocal) {
            Log.d("RupoopAuth", "Local data unchanged, pulling only")
            return pull(token)
        }
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Starting Gist Push")
        return try {
            var gistId = getOrFindGistId(authHeader)

            val remoteRegistry = if (gistId != null) {
                try {
                    val syncGist = gistApi.getGist(authHeader, gistId)
                    val content = syncGist.files[SYNC_FILE_NAME]?.content ?: "{}"
                    json.decodeFromString<UserRegistry>(content)
                } catch (e: Exception) {
                    UserRegistry()
                }
            } else {
                UserRegistry()
            }

            val currentWithPrefs = registryManager.registry.copy(
                appSettings = buildAppSettingsFromPreferences()
            )
            registryManager.updateRegistry(currentWithPrefs)

            val mergedRegistry = registryManager.mergeWith(remoteRegistry)
            applySettingsToPreferences(mergedRegistry.appSettings)

            val request = GistRequest(
                description = "Rupoop User Registry",
                public = false,
                files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(mergedRegistry)))
            )

            if (gistId != null) {
                gistApi.updateGist(authHeader, gistId, request)
            } else {
                val newGist = gistApi.createGist(authHeader, request)
                gistId = newGist.id
                settingsManager.cachedGistId = gistId
            }

            lastSentLocal = json.encodeToString(registryManager.registry.copy(appSettings = buildAppSettingsFromPreferences()))
            mergedRegistry
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Push failed", e)
            registryManager.registry
        }
    }

    suspend fun pushDirect(token: String): UserRegistry = pushMutex.withLock {
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Starting Direct Gist Push")
        return try {
            var gistId = getOrFindGistId(authHeader)

            val currentWithPrefs = registryManager.registry.copy(
                appSettings = buildAppSettingsFromPreferences(),
                lastSynced = System.currentTimeMillis()
            )
            registryManager.updateRegistry(currentWithPrefs)

            val request = GistRequest(
                description = "Rupoop User Registry",
                public = false,
                files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(currentWithPrefs)))
            )

            if (gistId != null) {
                gistApi.updateGist(authHeader, gistId, request)
            } else {
                val newGist = gistApi.createGist(authHeader, request)
                gistId = newGist.id
                settingsManager.cachedGistId = gistId
            }

            currentWithPrefs
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Direct push failed", e)
            registryManager.registry
        }
    }

    suspend fun sync(token: String): UserRegistry {
        return push(token)
    }

    private suspend fun getOrFindGistId(authHeader: String): String? {
        var gistId = settingsManager.cachedGistId
        if (gistId == null) {
            var page = 1
            var foundId: String? = null
            while (page <= 5) {
                val gists = try {
                    gistApi.listGists(authHeader, perPage = 100, page = page)
                } catch (e: Exception) {
                    Log.e("RupoopAuth", "Error listing gists on page $page", e)
                    break
                }
                if (gists.isEmpty()) break
                val match = gists.find { it.files.containsKey(SYNC_FILE_NAME) }
                if (match != null) {
                    foundId = match.id
                    break
                }
                if (gists.size < 100) break
                page++
            }
            gistId = foundId
            settingsManager.cachedGistId = gistId
        }
        return gistId
    }

    private fun applySettingsToPreferences(appSettings: AppSettings) {
        settingsManager.applyAppSettings(appSettings)
    }

    private fun buildAppSettingsFromPreferences(): AppSettings {
        return settingsManager.toAppSettings()
    }
}
