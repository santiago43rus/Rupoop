package com.santiago43rus.rupoop.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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
    val timestamp: Long = System.currentTimeMillis(),
    val downloadedSegments: Int = 0,
    val totalSegments: Int = 0
)

class DownloadTracker(private val context: Context) {
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

    fun updateProgress(videoId: String, progress: Int, downloaded: Int = 0, total: Int = 0) {
        val list = _downloads.value.toMutableList()
        val index = list.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            list[index] = list[index].copy(
                progress = progress,
                status = DownloadStatus.DOWNLOADING,
                downloadedSegments = downloaded,
                totalSegments = total
            )
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

    fun deleteFile(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return true

        // Try to delete via MediaStore first
        val uri = MediaStore.Files.getContentUri("external")
        val selection = MediaStore.MediaColumns.DATA + "=?"
        val selectionArgs = arrayOf(filePath)
        
        try {
            val rowsDeleted = context.contentResolver.delete(uri, selection, selectionArgs)
            if (rowsDeleted > 0) {
                Log.d("DownloadTracker", "Deleted file via MediaStore: $filePath")
                return true
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error deleting via MediaStore", e)
        }

        // Fallback to direct File delete
        try {
            if (file.delete()) {
                Log.d("DownloadTracker", "Deleted file via File API: $filePath")
                return true
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error deleting via File API", e)
        }
        return false
    }

    fun getUriFromFilePath(context: Context, filePath: String): Uri? {
        return DownloadIndexer.getUriFromFilePath(context, filePath)
    }

    fun indexSavedFiles() {
        val parsedItems = DownloadIndexer.indexSavedFiles(context)
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
