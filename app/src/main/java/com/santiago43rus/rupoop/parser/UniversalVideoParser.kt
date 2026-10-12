package com.santiago43rus.rupoop.parser

import android.util.Log
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

object UniversalVideoParser {

    private const val TAG = "UniversalVideoParser"

    private val DOMAIN_URL_PATTERN = Pattern.compile(
        """^[a-zA-Z0-9][-a-zA-Z0-9.]*\.[a-zA-Z]{2,}(?:/[^\s]*)?$""",
        Pattern.CASE_INSENSITIVE
    )

    fun isHttpUrl(queryOrUrl: String): Boolean {
        val trimmed = queryOrUrl.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return true
        if (trimmed.startsWith("vk.com/", ignoreCase = true) || trimmed.startsWith("vk.ru/", ignoreCase = true) || trimmed.startsWith("vkvideo.ru/", ignoreCase = true)) return true
        if (trimmed.startsWith("rutube.ru/", ignoreCase = true)) return true
        if (trimmed.startsWith("ok.ru/", ignoreCase = true) || trimmed.startsWith("odnoklassniki.ru/", ignoreCase = true) || trimmed.startsWith("m.ok.ru/", ignoreCase = true)) return true
        if (trimmed.startsWith("dzen.ru/", ignoreCase = true) || trimmed.startsWith("yandex.ru/", ignoreCase = true)) return true
        if (trimmed.startsWith("jut.su/", ignoreCase = true) || trimmed.startsWith("animego.", ignoreCase = true)) return true

        return DOMAIN_URL_PATTERN.matcher(trimmed).matches()
    }

    fun isRutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("rutube.ru")
    }

    fun extractRutubeId(url: String): String? {
        val clean = url.trim()
        if (clean.contains("/live/video/")) {
            return clean.substringAfter("/live/video/").substringBefore("/").substringBefore("?")
        }
        if (clean.contains("/live/")) {
            return clean.substringAfter("/live/").substringBefore("/").substringBefore("?")
        }
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
                            ?: resp.liveStreams?.hls?.firstOrNull { !it.url.isNullOrEmpty() }?.url
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

            // 4. Odnoklassniki (OK.ru) link
            if (OkVideoParser.isOkUrl(url)) {
                val okResult = OkVideoParser.parse(url)
                if (okResult != null) {
                    return@withContext okResult
                }
            }

            // 5. CdnVideoHub direct video API endpoint
            if (url.contains("plapi.cdnvideohub.com/api/v1/player/sv/video/")) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .header("Origin", "https://player.cdnvideohub.com")
                        .header("Referer", "https://player.cdnvideohub.com/")
                        .build()
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body.string()
                        val hls = Regex(""""hlsUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: Regex(""""mpegFullHdUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: Regex(""""mpegHighUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: Regex(""""mpegMediumUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        if (!hls.isNullOrBlank()) {
                            val cleanHls = hls.replace("\\/", "/").replace("\\u0026", "&")
                            return@withContext ParsedVideo(
                                videoUrl = url,
                                streamUrl = cleanHls,
                                title = "Серия",
                                authorName = "Lordfilm",
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                                    "Referer" to "https://player.cdnvideohub.com/",
                                    "Origin" to "https://player.cdnvideohub.com"
                                ),
                                sourceName = "Lordfilm"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing direct CdnVideoHub video API: $url", e)
                }
            }

            // 6. Web page (Lordfilm, pirate balancers, generic video sites, anime sites, etc.)
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
