package com.santiago43rus.rupoop.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VkVideoParser {

    private const val TAG = "VkVideoParser"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isVkUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vk.com") || lower.contains("vk.ru") || lower.contains("vkvideo.ru")
    }

    suspend fun parse(url: String): ParsedVideo? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeVkUrl(url)
            val (oid, id, hash) = extractVkVideoParams(normalizedUrl)

            // Method 1: Use VK AJAX API (al_video.php) - the most reliable method
            if (oid != null && id != null) {
                val ajaxResult = fetchFromAlVideo(oid, id, normalizedUrl)
                if (ajaxResult != null) return@withContext ajaxResult
            }

            // Method 2: Use VK Video Ext Embed
            if (oid != null && id != null) {
                val embedUrl = "https://vk.com/video_ext.php?oid=$oid&id=$id" + (if (!hash.isNullOrEmpty()) "&hash=$hash" else "")
                val embedResult = fetchFromHtml(embedUrl, normalizedUrl)
                if (embedResult != null) return@withContext embedResult
            }

            // Method 3: Load page HTML directly
            val pageResult = fetchFromHtml(normalizedUrl, normalizedUrl)
            if (pageResult != null) return@withContext pageResult

            Log.w(TAG, "All VK parsing methods failed for $url")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VK video: $url", e)
            null
        }
    }

    private fun fetchFromAlVideo(oid: String, id: String, originalUrl: String): ParsedVideo? {
        try {
            val formBody = FormBody.Builder()
                .add("act", "show")
                .add("al", "1")
                .add("video", "${oid}_$id")
                .build()

            val request = Request.Builder()
                .url("https://vk.com/al_video.php")
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://vk.com/")
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body.string()

            val streamUrl = extractStreamUrl(body) ?: return null

            var title = extractRegex(body, """["']md_title["']\s*:\s*["']([^"']+)["']""")
                ?: extractRegex(body, """<!title>(.*?)<!>""")
                ?: "VK Видео"
            title = cleanTitle(unescapeJson(title))

            val thumbnail = extractRegex(body, """["']thumb["']\s*:\s*["']([^"']+)["']""")
                ?.let { unescapeJson(it) }

            val author = extractRegex(body, """["']md_author["']\s*:\s*["']([^"']+)["']""")
                ?.let { unescapeJson(it) }
                ?: "VK Видео"

            val durationStr = extractRegex(body, """["']duration["']\s*:\s*(\d+)""")
            val duration = durationStr?.toLongOrNull()

            return ParsedVideo(
                videoUrl = originalUrl,
                streamUrl = streamUrl,
                title = title,
                thumbnailUrl = thumbnail,
                authorName = author,
                durationSeconds = duration,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://vk.com/"
                ),
                sourceName = "VK"
            )
        } catch (e: Exception) {
            Log.w(TAG, "al_video.php failed for ${oid}_$id", e)
            return null
        }
    }

    private fun fetchFromHtml(targetUrl: String, originalUrl: String): ParsedVideo? {
        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body.string()

            val streamUrl = extractStreamUrl(html) ?: return null

            var title = extractMetaContent(html, "og:title")
                ?: extractTagContent(html, "title")
                ?: extractRegex(html, """["']md_title["']\s*:\s*["']([^"']+)["']""")
                ?: "VK Видео"
            title = cleanTitle(unescapeHtml(unescapeJson(title)))

            val thumbnail = extractMetaContent(html, "og:image")
                ?: extractRegex(html, """["']thumb["']\s*:\s*["']([^"']+)["']""")?.let { unescapeJson(it) }

            val author = extractRegex(html, """["']md_author["']\s*:\s*["']([^"']+)["']""")?.let { unescapeJson(it) }
                ?: extractRegex(html, """["']author_name["']\s*:\s*["']([^"']+)["']""")?.let { unescapeJson(it) }
                ?: extractMetaContent(html, "og:site_name")
                ?: "VK Видео"

            return ParsedVideo(
                videoUrl = originalUrl,
                streamUrl = streamUrl,
                title = title,
                thumbnailUrl = thumbnail,
                authorName = author,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://vk.com/"
                ),
                sourceName = "VK"
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchFromHtml failed for $targetUrl", e)
            return null
        }
    }

    private fun extractVkVideoParams(url: String): Triple<String?, String?, String?> {
        var oid: String? = null
        var id: String? = null
        var hash: String? = null

        val oidIdPattern = Pattern.compile("""video(-?\d+)_(\d+)""")
        val oidIdMatcher = oidIdPattern.matcher(url)
        if (oidIdMatcher.find()) {
            oid = oidIdMatcher.group(1)
            id = oidIdMatcher.group(2)
        }

        val clipPattern = Pattern.compile("""clip(-?\d+)_(\d+)""")
        val clipMatcher = clipPattern.matcher(url)
        if (clipMatcher.find()) {
            oid = clipMatcher.group(1)
            id = clipMatcher.group(2)
        }

        val queryOid = extractQueryParam(url, "oid")
        val queryId = extractQueryParam(url, "id")
        val queryHash = extractQueryParam(url, "hash")

        if (queryOid != null) oid = queryOid
        if (queryId != null) id = queryId
        if (queryHash != null) hash = queryHash

        return Triple(oid, id, hash)
    }

    private fun extractQueryParam(url: String, paramName: String): String? {
        val pattern = Pattern.compile("""[?&]$paramName=([^&#]+)""")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractStreamUrl(htmlOrJson: String): String? {
        // Priority 1: HLS
        val hlsRegex = """(?:["']hls["']|hls)\s*:\s*["']([^"']+)["']"""
        val hlsMatch = extractRegex(htmlOrJson, hlsRegex)
        if (!hlsMatch.isNullOrBlank()) {
            return unescapeJson(hlsMatch)
        }

        // Priority 2: Direct MP4 resolutions (1080p, 720p, 480p, 360p, 240p)
        val qualities = listOf("url1080", "url720", "url480", "url360", "url240")
        for (q in qualities) {
            val qRegex = """(?:["']$q["']|$q)\s*:\s*["']([^"']+)["']"""
            val match = extractRegex(htmlOrJson, qRegex)
            if (!match.isNullOrBlank()) {
                return unescapeJson(match)
            }
        }

        // Priority 3: Any direct .m3u8 link in scripts
        val generalHls = extractRegex(htmlOrJson, """(https?:\\\/\\\/[^"'\s]+?\.m3u8[^"'\s]*)""")
            ?: extractRegex(htmlOrJson, """(https?:\/\/[^"'\s]+?\.m3u8[^"'\s]*)""")
        if (generalHls != null) {
            return unescapeJson(generalHls)
        }

        return null
    }

    private fun normalizeVkUrl(url: String): String {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        return cleanUrl
    }

    private fun unescapeJson(text: String): String {
        return text
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "")
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .trim()
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*property=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = pattern.matcher(html)
        if (matcher.find()) return matcher.group(1)

        val namePattern = Pattern.compile(
            """<meta\s+[^>]*name=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        matcher = namePattern.matcher(html)
        if (matcher.find()) return matcher.group(1)

        return null
    }

    private fun extractTagContent(html: String, tag: String): String? {
        val pattern = Pattern.compile(
            """<$tag[^>]*>(.*?)</$tag>""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractRegex(html: String, regex: String): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(" | ВКонтакте", "")
            .replace(" | VK Видео", "")
            .replace(" | VK", "")
            .replace(" - Смотреть видео онлайн", "")
            .trim()
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&#160;", " ")
            .replace("&nbsp;", " ")
    }
}
