package com.santiago43rus.rupoop.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object DownloadIndexer {

    fun parseRupoopFile(file: File): DownloadItem? {
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

    fun getUriFromFilePath(context: Context, filePath: String): Uri? {
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
            Log.e("DownloadIndexer", "Error getting video Uri", e)
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
            Log.e("DownloadIndexer", "Error getting audio Uri", e)
        }

        return null
    }

    fun indexSavedFiles(context: Context): List<DownloadItem> {
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
            Log.e("DownloadIndexer", "Error querying MediaStore videos", e)
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
            Log.e("DownloadIndexer", "Error querying MediaStore audio", e)
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
            Log.e("DownloadIndexer", "Error with manual directories fallback", e)
        }

        return parsedItems
    }
}
