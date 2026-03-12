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
                json.decodeFromString(file.readText())
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

