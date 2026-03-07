package com.example.rupoop

import android.content.Context
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
        updateRegistry(registry.copy(watchHistory = updatedHistory.take(200)))
    }

    fun updateWatchProgress(videoId: String, progress: Long, totalDuration: Long) {
        val updatedHistory = registry.watchHistory.map {
            if (it.videoId == videoId) {
                it.copy(progress = progress, totalDuration = totalDuration, timestamp = System.currentTimeMillis())
            } else it
        }
        updateRegistry(registry.copy(watchHistory = updatedHistory))
    }

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        val updatedSearch = (listOf(query) + registry.searchHistory)
            .distinct()
            .take(20)
        updateRegistry(registry.copy(searchHistory = updatedSearch))
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
                UserRegistry()
            }
        } else {
            UserRegistry()
        }
    }

    private fun saveLocal(data: UserRegistry) {
        registryFile.writeText(json.encodeToString(data))
    }

    fun mergeWith(remote: UserRegistry): UserRegistry {
        val local = registry
        
        // Merge watch history: union by videoId, keeping one with more progress or later timestamp
        val combinedHistory = (local.watchHistory + remote.watchHistory)
            .groupBy { it.videoId }
            .map { (_, items) ->
                items.maxByOrNull { it.timestamp }!!
            }
            .sortedByDescending { it.timestamp }
            .take(200)

        val combinedSearch = (local.searchHistory + remote.searchHistory)
            .distinct()
            .take(20)

        val combinedWeights = local.tagWeights.toMutableMap()
        remote.tagWeights.forEach { (tag, weight) ->
            combinedWeights[tag] = (combinedWeights[tag] ?: 0f) + weight
        }

        val merged = UserRegistry(
            watchHistory = combinedHistory,
            searchHistory = combinedSearch,
            tagWeights = combinedWeights,
            appSettings = remote.appSettings,
            lastSynced = System.currentTimeMillis()
        )
        
        updateRegistry(merged)
        return merged
    }
}
