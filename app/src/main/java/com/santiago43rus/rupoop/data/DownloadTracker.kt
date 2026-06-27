package com.santiago43rus.rupoop.data

import android.content.Context
import android.util.Log
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR, CANCELLED
}

@Serializable
data class DownloadItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val filePath: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class DownloadTracker(context: Context) {
    private val json = RetrofitClient.json
    private val file = File(context.filesDir, "downloads.json")
    
    private val _downloads = MutableStateFlow<List<DownloadItem>>(loadLocal())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun addDownload(item: DownloadItem) {
        val list = _downloads.value.toMutableList()
        list.removeAll { it.videoId == item.videoId }
        list.add(0, item)
        update(list)
    }

    fun updateProgress(videoId: String, progress: Int) {
        val list = _downloads.value.toMutableList()
        val index = list.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            list[index] = list[index].copy(progress = progress, status = DownloadStatus.DOWNLOADING)
            update(list)
        }
    }

    fun updateStatus(videoId: String, status: DownloadStatus, error: String? = null, filePath: String? = null) {
        val list = _downloads.value.toMutableList()
        val index = list.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            list[index] = list[index].copy(
                status = status,
                error = error,
                filePath = filePath ?: list[index].filePath,
                progress = if (status == DownloadStatus.COMPLETED) 100 else list[index].progress
            )
            update(list)
        }
    }

    fun removeDownload(videoId: String) {
        val list = _downloads.value.toMutableList()
        list.removeAll { it.videoId == videoId }
        update(list)
    }

    fun indexSavedFiles() {
        val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        
        val files = mutableListOf<File>()
        if (moviesDir.exists()) {
            moviesDir.listFiles()?.let { files.addAll(it) }
        }
        if (musicDir.exists()) {
            musicDir.listFiles()?.let { files.addAll(it) }
        }
        
        val parsedItems = files.mapNotNull { file ->
            parseRupoopFile(file)
        }
        
        if (parsedItems.isEmpty()) return
        
        val currentList = _downloads.value.toMutableList()
        var changed = false
        
        for (parsedItem in parsedItems) {
            val existing = currentList.find { it.videoId == parsedItem.videoId }
            if (existing == null) {
                currentList.add(parsedItem)
                changed = true
            } else if (existing.filePath != parsedItem.filePath || existing.status != DownloadStatus.COMPLETED) {
                val idx = currentList.indexOf(existing)
                currentList[idx] = existing.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    filePath = parsedItem.filePath
                )
                changed = true
            }
        }
        
        if (changed) {
            currentList.sortByDescending { it.timestamp }
            update(currentList)
        }
    }

    private fun parseRupoopFile(file: File): DownloadItem? {
        val name = file.name
        val regex = """^(.*)_rupoop_([a-zA-Z0-9_-]+___[0-9]+)\.(mp4|m4a|mp3)$""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(name) ?: return null
        val sanitizedTitle = match.groupValues[1]
        val videoId = match.groupValues[2]
        
        val displayTitle = sanitizedTitle.replace('_', ' ').trim()
        val parts = videoId.split("___")
        val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: file.lastModified()
        
        return DownloadItem(
            videoId = videoId,
            title = displayTitle.ifEmpty { "Видео" },
            thumbnailUrl = null,
            filePath = file.absolutePath,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            timestamp = timestamp
        )
    }

    fun refresh() {
        _downloads.value = loadLocal()
    }

    private fun update(list: List<DownloadItem>) {
        _downloads.value = list
        saveLocal(list)
    }

    private fun loadLocal(): List<DownloadItem> {
        return if (file.exists()) {
            try {
                val text = file.readText().trim()
                if (text.isEmpty()) emptyList() else json.decodeFromString(text)
            } catch (e: Exception) {
                Log.e("DownloadTracker", "Load error", e)
                emptyList()
            }
        } else emptyList()
    }

    private fun saveLocal(data: List<DownloadItem>) {
        try {
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Save error", e)
        }
    }
}

