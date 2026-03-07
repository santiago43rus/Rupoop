package com.example.rupoop

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GistSyncManager(
    private val gistApi: GistApi,
    private val registryManager: UserRegistryManager
) {
    private val SYNC_FILE_NAME = "rupoop_user_registry_v2.json"
    private val json = RetrofitClient.json

    suspend fun sync(token: String): UserRegistry {
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Searching for $SYNC_FILE_NAME in Gists")
        
        return try {
            val gists = gistApi.listGists(authHeader)
            val syncGistMeta = gists.find { it.files.containsKey(SYNC_FILE_NAME) }

            if (syncGistMeta != null) {
                Log.d("RupoopAuth", "Gist found: ${syncGistMeta.id}, fetching content")
                val syncGist = gistApi.getGist(authHeader, syncGistMeta.id)
                val file = syncGist.files[SYNC_FILE_NAME]
                val content = file?.content ?: "{}"
                val remoteRegistry = try {
                    json.decodeFromString<UserRegistry>(content)
                } catch (e: Exception) {
                    UserRegistry()
                }
                
                val mergedRegistry = registryManager.mergeWith(remoteRegistry)
                
                val updateRequest = GistRequest(
                    description = "Rupoop User Registry",
                    public = false,
                    files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(mergedRegistry)))
                )
                gistApi.updateGist(authHeader, syncGistMeta.id, updateRequest)
                mergedRegistry
            } else {
                Log.d("RupoopAuth", "Gist not found, creating new one")
                val localRegistry = registryManager.registry
                val request = GistRequest(
                    description = "Rupoop User Registry",
                    public = false,
                    files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(localRegistry)))
                )
                gistApi.createGist(authHeader, request)
                localRegistry
            }
        } catch (e: Exception) {
            Log.e("RupoopAuth", "Sync failed, returning local", e)
            registryManager.registry
        }
    }
}
