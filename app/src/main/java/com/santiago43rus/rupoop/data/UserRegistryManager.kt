package com.santiago43rus.rupoop.data

import android.content.Context
import android.util.Log
import com.santiago43rus.rupoop.lan.RegistryKeys
import com.santiago43rus.rupoop.lan.RegistryMerge
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.serialization.encodeToString
import java.io.File

class UserRegistryManager(private val context: Context) {
    private val json = RetrofitClient.json
    private val registryFile = File(context.filesDir, "user_registry.json")
    
    @Volatile
    var registry: UserRegistry = RegistryKeys.migrateAddedAt(loadLocal())
        private set

    /** Вызывается после каждого локального изменения (LAN-синхронизация ставит сюда отправку). */
    @Volatile
    var onLocalChange: (() -> Unit)? = null

    /** Все локальные изменения идут сюда: проставляем время добавления и надгробия удалённым. */
    @Synchronized
    fun updateRegistry(newRegistry: UserRegistry) {
        val now = System.currentTimeMillis()
        val updatedSettings = if (newRegistry.appSettings != registry.appSettings) {
            newRegistry.appSettings.copy(updatedAt = now)
        } else {
            newRegistry.appSettings
        }
        val regWithSettings = newRegistry.copy(appSettings = updatedSettings)
        val stamped = RegistryKeys.stampLocalChange(registry, regWithSettings, now)
        registry = stamped.copy(lastSynced = now)
        saveLocal(registry)
        onLocalChange?.invoke()
    }

    /** Применить результат слияния. Без надгробий и без onLocalChange. */
    @Synchronized
    fun applyMerged(merged: UserRegistry) {
        registry = merged
        saveLocal(merged)
    }

    /** Слияние с реестром другого устройства (LAN). Вызывать на главном потоке. */
    @Synchronized
    fun mergeRemote(remote: UserRegistry): UserRegistry {
        return mergeWith(remote)
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
        
        val videoId = extractId(video.videoUrl)
        val disliked = registry.dislikedVideos.toMutableList()
        val dislikedList = registry.dislikedVideosList.toMutableList()
        val hidden = registry.hiddenVideos.toMutableList()
        val hiddenList = registry.hiddenVideosList.toMutableList()
        
        if (exists) {
            liked.removeAll { it.videoUrl == video.videoUrl }
        } else {
            liked.add(0, video)
            disliked.remove(videoId)
            dislikedList.removeAll { extractId(it.videoUrl) == videoId }
            hidden.remove(videoId)
            hiddenList.removeAll { extractId(it.videoUrl) == videoId }
        }
        
        updateRegistry(registry.copy(
            likedVideos = liked,
            dislikedVideos = disliked,
            dislikedVideosList = dislikedList,
            hiddenVideos = hidden,
            hiddenVideosList = hiddenList
        ))
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

    fun hideTitle(title: String) {
        val titles = registry.hiddenTitles.toMutableList()
        if (!titles.contains(title)) {
            titles.add(title)
            updateRegistry(registry.copy(hiddenTitles = titles))
        }
    }

    private fun extractId(url: String): String {
        return url.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?") ?: ""
    }

    fun toggleDislike(video: SearchResult): Boolean {
        val videoId = extractId(video.videoUrl)
        val disliked = registry.dislikedVideos.toMutableList()
        val dislikedList = registry.dislikedVideosList.toMutableList()
        val exists = disliked.contains(videoId)
        if (exists) {
            disliked.remove(videoId)
            dislikedList.removeAll { extractId(it.videoUrl) == videoId }
        } else {
            disliked.add(videoId)
            dislikedList.add(0, video)
        }
        
        // Also remove from liked if disliking
        val liked = registry.likedVideos.toMutableList()
        if (!exists) {
            liked.removeAll { extractId(it.videoUrl) == videoId }
        }
        
        updateRegistry(registry.copy(dislikedVideos = disliked, dislikedVideosList = dislikedList, likedVideos = liked))
        return !exists
    }

    fun hideVideo(video: SearchResult) {
        val videoId = extractId(video.videoUrl)
        val hidden = registry.hiddenVideos.toMutableList()
        val hiddenList = registry.hiddenVideosList.toMutableList()
        if (!hidden.contains(videoId)) {
            hidden.add(videoId)
            hiddenList.add(0, video)
            updateRegistry(registry.copy(hiddenVideos = hidden, hiddenVideosList = hiddenList))
        }
    }

    fun restoreVideo(videoId: String, title: String) {
        val hidden = registry.hiddenVideos.toMutableList()
        hidden.remove(videoId)
        
        val hiddenList = registry.hiddenVideosList.toMutableList()
        hiddenList.removeAll { extractId(it.videoUrl) == videoId }
        
        val disliked = registry.dislikedVideos.toMutableList()
        disliked.remove(videoId)
        
        val dislikedList = registry.dislikedVideosList.toMutableList()
        dislikedList.removeAll { extractId(it.videoUrl) == videoId }
        
        val titles = registry.hiddenTitles.toMutableList()
        titles.removeAll { it.equals(title, ignoreCase = true) }
        
        updateRegistry(registry.copy(
            hiddenVideos = hidden, 
            hiddenVideosList = hiddenList, 
            dislikedVideos = disliked, 
            dislikedVideosList = dislikedList, 
            hiddenTitles = titles
        ))
    }

    fun getHiddenAndDislikedVideos(): List<SearchResult> {
        return (registry.hiddenVideosList + registry.dislikedVideosList).distinctBy { it.videoUrl }
    }

    fun addToPlaylist(playlistName: String, video: SearchResult): Boolean {
        val playlists = registry.playlists.toMutableList()
        val index = playlists.indexOfFirst { it.name == playlistName }
        if (index != -1) {
            val p = playlists[index]
            if (!p.videos.any { it.videoUrl == video.videoUrl }) {
                playlists[index] = p.copy(videos = listOf(video) + p.videos)
                updateRegistry(registry.copy(playlists = playlists))
                return true
            } else {
                return false
            }
        } else {
            playlists.add(Playlist(id = System.currentTimeMillis().toString(), name = playlistName, videos = listOf(video)))
            updateRegistry(registry.copy(playlists = playlists))
            return true
        }
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
            val tmp = File(context.filesDir, "user_registry.json.tmp")
            tmp.writeText(json.encodeToString(data))
            java.nio.file.Files.move(
                tmp.toPath(), registryFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            Log.e("UserRegistry", "Save error", e)
        }
    }

    fun mergeWith(remote: UserRegistry): UserRegistry {
        val local = registry

        val remoteSettingsTime = if (remote.appSettings.updatedAt > 0L) remote.appSettings.updatedAt else remote.lastSynced
        val localSettingsTime = if (local.appSettings.updatedAt > 0L) local.appSettings.updatedAt else local.lastSynced
        val isRemoteSettingsNewer = remoteSettingsTime > localSettingsTime

        val baseSettings = if (isRemoteSettingsNewer) remote.appSettings else local.appSettings
        val combinedAppSettings = baseSettings.copy(
            theme = local.appSettings.theme,
            isEasterEggUnlocked = local.appSettings.isEasterEggUnlocked || remote.appSettings.isEasterEggUnlocked,
            updatedAt = maxOf(localSettingsTime, remoteSettingsTime)
        )

        val dataMerged = RegistryMerge.merge(local, remote, System.currentTimeMillis())
        val merged = dataMerged.copy(
            appSettings = combinedAppSettings,
            lastSynced = maxOf(local.lastSynced, remote.lastSynced)
        )
        applyMerged(merged)
        return merged
    }
}
