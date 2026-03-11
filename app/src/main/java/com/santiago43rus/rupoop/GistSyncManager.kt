package com.santiago43rus.rupoop

import android.util.Log
import kotlinx.serialization.encodeToString

class GistSyncManager(
    private val gistApi: GistApi,
    private val registryManager: UserRegistryManager,
    private val settingsManager: SettingsManager
) {
    private val SYNC_FILE_NAME = "rupoop_user_registry_v2.json"
    private val json = RetrofitClient.json

    suspend fun sync(token: String): UserRegistry {
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Starting Gist Sync")
        
        return try {
            var gistId = settingsManager.cachedGistId
            if (gistId == null) {
                Log.d("RupoopAuth", "Searching for $SYNC_FILE_NAME in Gists")
                val gists = gistApi.listGists(authHeader)
                val syncGistMeta = gists.find { it.files.containsKey(SYNC_FILE_NAME) }
                gistId = syncGistMeta?.id
                settingsManager.cachedGistId = gistId
            }

            if (gistId != null) {
                Log.d("RupoopAuth", "Gist found: $gistId, fetching content")
                val syncGist = gistApi.getGist(authHeader, gistId)
                val file = syncGist.files[SYNC_FILE_NAME]
                val content = file?.content ?: "{}"
                val remoteRegistry = try {
                    json.decodeFromString<UserRegistry>(content)
                } catch (e: Exception) {
                    UserRegistry()
                }
                
                val mergedRegistry = registryManager.mergeWith(remoteRegistry)
                
                push(token, mergedRegistry, gistId)
                mergedRegistry
            } else {
                Log.d("RupoopAuth", "Gist not found, creating new one")
                val localRegistry = registryManager.registry
                push(token, localRegistry)
                localRegistry
            }
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Sync failed, returning local", e)
            registryManager.registry
        }
    }

    suspend fun push(token: String, registry: UserRegistry, gistId: String? = null) {
        val authHeader = "Bearer $token"
        try {
            var targetGistId = gistId ?: settingsManager.cachedGistId
            if (targetGistId == null) {
                val gists = gistApi.listGists(authHeader)
                targetGistId = gists.find { it.files.containsKey(SYNC_FILE_NAME) }?.id
                settingsManager.cachedGistId = targetGistId
            }

            val request = GistRequest(
                description = "Rupoop User Registry",
                public = false,
                files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(registry)))
            )

            if (targetGistId != null) {
                gistApi.updateGist(authHeader, targetGistId, request)
            } else {
                val newGist = gistApi.createGist(authHeader, request)
                settingsManager.cachedGistId = newGist.id
            }
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Push failed", e)
        }
    }
}
