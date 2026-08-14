package com.santiago43rus.rupoop.parser

import android.util.Log
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

object UniversalVideoParser {

    private const val TAG = "UniversalVideoParser"

    fun isHttpUrl(queryOrUrl: String): Boolean {
        val trimmed = queryOrUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
        if (trimmed.startsWith("vk.com/") || trimmed.startsWith("vk.ru/") || trimmed.startsWith("vkvideo.ru/")) return true
        if (trimmed.startsWith("rutube.ru/")) return true
        if (trimmed.contains(".lordfilm") || trimmed.contains("lordfilm.") || trimmed.contains("kinogo.") || trimmed.contains("rezka.")) return true
        return false
    }

    fun isRutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("rutube.ru")
    }

    fun extractRutubeId(url: String): String? {
        val clean = url.trim()
        if (clean.contains("/video/")) {
            return clean.substringAfter("/video/").substringBefore("/").substringBefore("?")
        }
        if (clean.contains("/play/embed/")) {
            return clean.substringAfter("/play/embed/").substringBefore("/").substringBefore("?")
        }
        return clean.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?")
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm")
    }

    suspend fun parse(rawUrl: String): ParsedVideo? = withContext(Dispatchers.IO) {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        try {
            // 1. Direct stream link
            if (isDirectMediaUrl(url)) {
                val fileName = url.substringBefore("?").split("/").lastOrNull() ?: "Медиа поток"
                return@withContext ParsedVideo(
                    videoUrl = url,
                    streamUrl = url,
                    title = fileName,
                    authorName = "Прямой поток",
                    sourceName = "Direct"
                )
            }

            // 2. Rutube link
            if (isRutubeUrl(url)) {
                val rutubeId = extractRutubeId(url)
                if (!rutubeId.isNullOrEmpty()) {
                    try {
                        val resp = RetrofitClient.api.getVideoOptions(rutubeId)
                        val m3u8 = resp.videoBalancer?.m3u8
                        if (!m3u8.isNullOrEmpty()) {
                            return@withContext ParsedVideo(
                                videoUrl = url,
                                streamUrl = m3u8,
                                title = "Rutube Видео",
                                thumbnailUrl = resp.thumbnailUrl,
                                authorName = "Rutube",
                                durationSeconds = resp.duration?.toLong(),
                                sourceName = "Rutube"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching Rutube options for $rutubeId", e)
                    }
                }
            }

            // 3. VK video link
            if (VkVideoParser.isVkUrl(url)) {
                val vkResult = VkVideoParser.parse(url)
                if (vkResult != null) {
                    return@withContext vkResult
                }
            }

            // 4. Web page (Lordfilm, pirate balancers, generic video sites)
            val webResult = UnifiedWebVideoParser.parse(url)
            if (webResult != null) {
                return@withContext webResult
            }

            Log.w(TAG, "UniversalVideoParser: could not extract video from $url")
            null
        } catch (e: Exception) {
            Log.e(TAG, "UniversalVideoParser error parsing: $url", e)
            null
        }
    }
}
