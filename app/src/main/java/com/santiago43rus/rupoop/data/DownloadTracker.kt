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
import android.provider.MediaStore
import android.os.Environment
import android.content.ContentUris

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

    fun getUriFromFilePath(context: Context, filePath: String): android.net.Uri? {
        val resolver = context.contentResolver
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.MediaColumns.DATA + "=?"
        val selectionArgs = arrayOf(filePath)
        
        try {
            resolver.query(videoUri, arrayOf(MediaStore.Video.Media._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    return ContentUris.withAppendedId(videoUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error getting video Uri", e)
        }

        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        try {
            resolver.query(audioUri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return ContentUris.withAppendedId(audioUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error getting audio Uri", e)
        }

        return null
    }

    fun indexSavedFiles() {
        val parsedItems = mutableListOf<DownloadItem>()

        // 1. Query Video from MediaStore
        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_ADDED
            )
            val videoSelection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val videoSelectionArgs = arrayOf("%_rupoop_%")
            
            context.contentResolver.query(videoUri, videoProjection, videoSelection, videoSelectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val data = cursor.getString(dataIndex)
                    val dateAdded = cursor.getLong(dateIndex) * 1000
                    val file = File(data)
                    if (file.exists()) {
                        val parsed = parseRupoopFile(file)
                        if (parsed != null) {
                            parsedItems.add(parsed.copy(timestamp = dateAdded))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error querying MediaStore videos", e)
        }

        // 2. Query Audio from MediaStore
        try {
            val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val audioProjection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
            )
            val audioSelection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val audioSelectionArgs = arrayOf("%_rupoop_%")
            
            context.contentResolver.query(audioUri, audioProjection, audioSelection, audioSelectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val data = cursor.getString(dataIndex)
                    val dateAdded = cursor.getLong(dateIndex) * 1000
                    val file = File(data)
                    if (file.exists()) {
                        val parsed = parseRupoopFile(file)
                        if (parsed != null) {
                            parsedItems.add(parsed.copy(timestamp = dateAdded))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error querying MediaStore audio", e)
        }

        // 3. Manual directories fallback (Rupoop, Movies, Music)
        try {
            val rupoopDir = File(Environment.getExternalStorageDirectory(), "Rupoop")
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            
            val manualFiles = mutableListOf<File>()
            if (rupoopDir.exists()) rupoopDir.listFiles()?.let { manualFiles.addAll(it) }
            if (moviesDir.exists()) moviesDir.listFiles()?.let { manualFiles.addAll(it) }
            if (musicDir.exists()) musicDir.listFiles()?.let { manualFiles.addAll(it) }
            
            for (file in manualFiles) {
                if (file.name.contains("_rupoop_", ignoreCase = true)) {
                    val parsed = parseRupoopFile(file)
                    if (parsed != null && parsedItems.none { it.filePath == parsed.filePath }) {
                        parsedItems.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Error with manual directories fallback", e)
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

