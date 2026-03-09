package com.example.rupoop

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class UserRegistryManager(private val context: Context) {
    private val json = RetrofitClient.json
    private val registryFile = File(context.filesDir, "user_registry.json")
    
    var registry: UserRegistry = loadLocal()
        private set

    fun updateRegistry(newRegistry: UserRegistry) {
        registry = newRegistry
        saveLocal(registry)
    }

    fun addWatchHistory(item: WatchHistoryItem) {
        val updatedHistory = registry.watchHistory.toMutableList()
        updatedHistory.removeAll { it.videoId == item.videoId }
        updatedHistory.add(0, item)
        updateRegistry(registry.copy(watchHistory = updatedHistory.take(500)))
    }

    fun removeFromHistory(videoId: String) {
        val updatedHistory = registry.watchHistory.filterNot { it.videoId == videoId }
        updateRegistry(registry.copy(watchHistory = updatedHistory))
    }

    fun clearWatchHistory() {
        updateRegistry(registry.copy(watchHistory = emptyList(), watchHistoryClearedAt = System.currentTimeMillis()))
    }

    fun updateWatchProgress(videoId: String, progress: Long, totalDuration: Long) {
        val updatedHistory = registry.watchHistory.toMutableList()
        val index = updatedHistory.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            val item = updatedHistory.removeAt(index)
            updatedHistory.add(0, item.copy(progress = progress, totalDuration = totalDuration, timestamp = System.currentTimeMillis()))
            updateRegistry(registry.copy(watchHistory = updatedHistory))
        } else {
            // If item not found (e.g. was removed from history while playing), don't add it back here
            // addWatchHistory should have been called when playback started.
        }
    }

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        val updatedSearch = (listOf(query) + registry.searchHistory)
            .distinct()
            .take(20)
        updateRegistry(registry.copy(searchHistory = updatedSearch))
    }

    fun removeSearchQuery(query: String) {
        val updatedSearch = registry.searchHistory.filterNot { it == query }
        updateRegistry(registry.copy(searchHistory = updatedSearch))
    }

    fun clearSearchHistory() {
        updateRegistry(registry.copy(searchHistory = emptyList(), searchHistoryClearedAt = System.currentTimeMillis()))
    }

    fun toggleLike(video: SearchResult): Boolean {
        val liked = registry.likedVideos.toMutableList()
        val exists = liked.any { it.videoUrl == video.videoUrl }
        if (exists) liked.removeAll { it.videoUrl == video.videoUrl }
        else liked.add(0, video)
        updateRegistry(registry.copy(likedVideos = liked))
        return !exists
    }

    fun toggleWatchLater(video: SearchResult): Boolean {
        val later = registry.watchLater.toMutableList()
        val exists = later.any { it.videoUrl == video.videoUrl }
        if (exists) later.removeAll { it.videoUrl == video.videoUrl }
        else later.add(0, video)
        updateRegistry(registry.copy(watchLater = later))
        return !exists
    }

    fun addToPlaylist(playlistName: String, video: SearchResult) {
        val playlists = registry.playlists.toMutableList()
        val index = playlists.indexOfFirst { it.name == playlistName }
        if (index != -1) {
            val p = playlists[index]
            if (!p.videos.any { it.videoUrl == video.videoUrl }) {
                playlists[index] = p.copy(videos = listOf(video) + p.videos)
            }
        } else {
            playlists.add(Playlist(id = System.currentTimeMillis().toString(), name = playlistName, videos = listOf(video)))
        }
        updateRegistry(registry.copy(playlists = playlists))
    }

    fun removeFromPlaylist(playlistId: String, videoUrl: String) {
        val playlists = registry.playlists.toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val p = playlists[index]
            playlists[index] = p.copy(videos = p.videos.filterNot { it.videoUrl == videoUrl })
            updateRegistry(registry.copy(playlists = playlists))
        }
    }

    fun deletePlaylist(playlistId: String) {
        val updatedPlaylists = registry.playlists.filterNot { it.id == playlistId }
        updateRegistry(registry.copy(playlists = updatedPlaylists))
    }

    fun updateTagWeights(tags: List<String>, weightIncrement: Float) {
        val currentWeights = registry.tagWeights.toMutableMap()
        tags.forEach { tag ->
            val current = currentWeights[tag] ?: 0f
            currentWeights[tag] = current + weightIncrement
        }
        updateRegistry(registry.copy(tagWeights = currentWeights))
    }

    private fun loadLocal(): UserRegistry {
        return if (registryFile.exists()) {
            try {
                json.decodeFromString(registryFile.readText())
            } catch (e: Exception) {
                Log.e("UserRegistry", "Load error", e)
                UserRegistry()
            }
        } else {
            UserRegistry()
        }
    }

    private fun saveLocal(data: UserRegistry) {
        try {
            registryFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            Log.e("UserRegistry", "Save error", e)
        }
    }

    fun mergeWith(remote: UserRegistry): UserRegistry {
        val local = registry
        
        val maxWatchClearedAt = maxOf(local.watchHistoryClearedAt, remote.watchHistoryClearedAt)
        val maxSearchClearedAt = maxOf(local.searchHistoryClearedAt, remote.searchHistoryClearedAt)

        val combinedHistory = (local.watchHistory + remote.watchHistory)
            .filter { it.timestamp > maxWatchClearedAt }
            .groupBy { it.videoId }
            .map { (_, items) ->
                items.maxByOrNull { it.timestamp }!!
            }
            .sortedByDescending { it.timestamp }
            .take(500)

        val combinedSearch = if (local.searchHistoryClearedAt > remote.lastSynced && local.searchHistoryClearedAt > remote.searchHistoryClearedAt) {
            local.searchHistory
        } else if (remote.searchHistoryClearedAt > local.lastSynced && remote.searchHistoryClearedAt > local.searchHistoryClearedAt) {
            remote.searchHistory
        } else {
            (local.searchHistory + remote.searchHistory).distinct().take(20)
        }

        val combinedWeights = local.tagWeights.toMutableMap()
        remote.tagWeights.forEach { (tag, weight) ->
            combinedWeights[tag] = (combinedWeights[tag] ?: 0f) + weight
        }

        val merged = UserRegistry(
            watchHistory = combinedHistory,
            searchHistory = combinedSearch,
            tagWeights = combinedWeights,
            subscriptions = (local.subscriptions + remote.subscriptions).distinctBy { it.name },
            likedVideos = (local.likedVideos + remote.likedVideos).distinctBy { it.videoUrl },
            watchLater = (local.watchLater + remote.watchLater).distinctBy { it.videoUrl },
            playlists = (local.playlists + remote.playlists).distinctBy { it.name },
            appSettings = remote.appSettings,
            lastSynced = System.currentTimeMillis(),
            watchHistoryClearedAt = maxWatchClearedAt,
            searchHistoryClearedAt = maxSearchClearedAt
        )
        
        updateRegistry(merged)
        return merged
    }
}
