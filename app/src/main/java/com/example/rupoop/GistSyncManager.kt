package com.example.rupoop

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GistSyncManager(private val gistApi: GistApi) {
    private val SYNC_FILE_NAME = "rupoop_sync.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync(token: String): SyncData {
        val authHeader = "Bearer $token"
        Log.d("RupoopAuth", "Searching for $SYNC_FILE_NAME in Gists")
        val gists = gistApi.listGists(authHeader)
        val syncGistMeta = gists.find { it.files.containsKey(SYNC_FILE_NAME) }

        return if (syncGistMeta != null) {
            Log.d("RupoopAuth", "Gist found: ${syncGistMeta.id}, fetching content")
            val syncGist = gistApi.getGist(authHeader, syncGistMeta.id)
            val file = syncGist.files[SYNC_FILE_NAME]
            val content = file?.content ?: "{}"
            json.decodeFromString<SyncData>(content)
        } else {
            Log.d("RupoopAuth", "Gist not found, creating new one")
            val initialData = SyncData()
            val request = GistRequest(
                description = "Rupoop Sync Data",
                public = false,
                files = mapOf(SYNC_FILE_NAME to GistFile(content = json.encodeToString(initialData)))
            )
            gistApi.createGist(authHeader, request)
            initialData
        }
    }
}
