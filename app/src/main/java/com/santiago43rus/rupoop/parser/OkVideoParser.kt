package com.santiago43rus.rupoop.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object OkVideoParser {

    private const val TAG = "OkVideoParser"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isOkUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("ok.ru") || lower.contains("odnoklassniki.ru")
    }

    fun extractOkVideoId(url: String): String? {
        val clean = url.trim()
        val pattern = Pattern.compile("""(?:video|videoembed|live|movie)(?:/c|/)?(\d+)""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(clean)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val midPattern = Pattern.compile("""[?&](?:mid|st\.id|id)=(\d+)""")
        val midMatcher = midPattern.matcher(clean)
        if (midMatcher.find()) {
            return midMatcher.group(1)
        }
        return null
    }

    suspend fun parse(url: String): ParsedVideo? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(url)
            val videoId = extractOkVideoId(normalizedUrl)

            // Method 1: Fetch via videoembed page (contains data-options with HLS and MP4 links)
            if (!videoId.isNullOrEmpty()) {
                val embedUrl = "https://ok.ru/videoembed/$videoId"
                val embedResult = fetchFromEmbedHtml(embedUrl, normalizedUrl)
                if (embedResult != null) return@withContext embedResult

                // Method 2: Fetch via OK metadata Web API
                val apiResult = fetchFromMetadataApi(videoId, normalizedUrl)
                if (apiResult != null) return@withContext apiResult
            }

            // Method 3: Load original page HTML
            val pageResult = fetchFromEmbedHtml(normalizedUrl, normalizedUrl)
            if (pageResult != null) return@withContext pageResult

            Log.w(TAG, "All OK.ru parsing methods failed for $url")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OK.ru video: $url", e)
            null
        }
    }

    private suspend fun fetchFromEmbedHtml(targetUrl: String, originalUrl: String): ParsedVideo? {
        return try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", "https://ok.ru/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body.string()

            // 1. Direct search for hlsManifestUrl in unescaped HTML
            val unescapedHtml = unescapeAll(html)
            val hlsDirectMatch = extractRegex(unescapedHtml, """["']hlsManifestUrl["']\s*:\s*["'](https?:\/\/[^"'\s<>\\]+?\.m3u8[^"'\s<>]*)["']""")
                ?: extractRegex(unescapedHtml, """(https?:\/\/[^"'\s<>\\]+?\.m3u8\?cmd=videoPlayerCdn[^"'\s<>]*)""")
                ?: extractRegex(unescapedHtml, """(https?:\/\/[^"'\s<>\\]+?\.m3u8[^"'\s<>]*)""")

            if (hlsDirectMatch != null) {
                val cleanHls = cleanUrl(hlsDirectMatch)
                var title = extractMetaContent(html, "og:title")
                    ?: extractTagContent(html, "title")
                    ?: "Видео Одноклассники"
                title = cleanTitle(title)

                val thumbnail = extractMetaContent(html, "og:image")
                val author = extractMetaContent(html, "og:site_name") ?: "Одноклассники"

                return ParsedVideo(
                    videoUrl = originalUrl,
                    streamUrl = cleanHls,
                    title = title,
                    thumbnailUrl = thumbnail,
                    authorName = author,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://ok.ru/"
                    ),
                    sourceName = "Одноклассники"
                )
            }

            // 2. Try to extract data-options attribute
            val dataOptionsPatterns = listOf(
                Pattern.compile("""data-(?:options|movie-options|player-options|video-options)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""data-options=["'](\{.*?\})["']""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
                Pattern.compile("""(?:data-options|videoOptions|okVideoOptions)\s*[:=]\s*(\{.*?\})(?:;|\n|</script>)""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            )

            for (p in dataOptionsPatterns) {
                val matcher = p.matcher(html)
                while (matcher.find()) {
                    val rawAttr = matcher.group(1) ?: continue
                    val unescaped = unescapeAll(rawAttr)
                    val parsed = parseOkDataOptionsJson(unescaped, originalUrl)
                    if (parsed != null) return parsed
                }
            }

            // 3. Check for embedded VK iframe or other balancer url
            val embeddedVkUrl = extractRegex(unescapedHtml, """["']url["']\s*:\s*["'](https?:\/\/(?:vk\.com|vkvideo\.ru)\/video_ext\.php[^"']+)["']""")
                ?: extractRegex(unescapedHtml, """(https?:\/\/(?:vk\.com|vkvideo\.ru)\/video_ext\.php[^"'\s<>]+)""")
            if (embeddedVkUrl != null) {
                val vkParsed = VkVideoParser.parse(cleanUrl(embeddedVkUrl))
                if (vkParsed != null) {
                    return vkParsed.copy(videoUrl = originalUrl)
                }
            }

            // 4. Fallback: direct mp4 in HTML
            val streamUrl = extractStreamUrl(unescapedHtml)
            if (streamUrl != null) {
                val cleanMp4 = cleanUrl(streamUrl)
                var title = extractMetaContent(html, "og:title")
                    ?: extractTagContent(html, "title")
                    ?: "Видео Одноклассники"
                title = cleanTitle(title)

                val thumbnail = extractMetaContent(html, "og:image")
                val author = extractMetaContent(html, "og:site_name") ?: "Одноклассники"

                return ParsedVideo(
                    videoUrl = originalUrl,
                    streamUrl = cleanMp4,
                    title = title,
                    thumbnailUrl = thumbnail,
                    authorName = author,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://ok.ru/"
                    ),
                    sourceName = "Одноклассники"
                )
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "fetchFromEmbedHtml failed for $targetUrl", e)
            null
        }
    }

    private suspend fun fetchFromMetadataApi(videoId: String, originalUrl: String): ParsedVideo? {
        return try {
            val formBody = FormBody.Builder()
                .add("cmd", "videoPlayerMetadata")
                .add("mid", videoId)
                .build()

            val request = Request.Builder()
                .url("https://ok.ru/dk?cmd=videoPlayerMetadata")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://ok.ru/videoembed/$videoId")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body.string()

            parseOkDataOptionsJson(unescapeAll(body), originalUrl)
        } catch (e: Exception) {
            Log.w(TAG, "fetchFromMetadataApi failed for $videoId", e)
            null
        }
    }

    private suspend fun parseOkDataOptionsJson(jsonStr: String, originalUrl: String): ParsedVideo? {
        return try {
            val json = JSONObject(jsonStr)

            // If options contains external player URL (e.g. VK video_ext.php)
            val externalUrl = json.optString("url").takeIf { it.isNotBlank() }
            if (externalUrl != null && VkVideoParser.isVkUrl(externalUrl)) {
                val vkParsed = VkVideoParser.parse(cleanUrl(externalUrl))
                if (vkParsed != null) return vkParsed.copy(videoUrl = originalUrl)
            }

            // Stream URL: Priority to HLS Master Playlist
            var streamUrl: String? = json.optString("hlsManifestUrl").takeIf { it.isNotBlank() }
                ?: json.optString("hlsMasterPlaylistUrl").takeIf { it.isNotBlank() }

            // If no HLS, select best MP4 from videos array
            if (streamUrl.isNullOrBlank() && json.has("videos")) {
                val videosArray = json.optJSONArray("videos")
                if (videosArray != null && videosArray.length() > 0) {
                    streamUrl = selectBestMp4Quality(videosArray)
                }
            }

            if (streamUrl.isNullOrBlank()) return null

            // Clean stream URL
            streamUrl = cleanUrl(streamUrl)

            // Metadata extraction
            val movie = json.optJSONObject("movie")
            val authorObj = json.optJSONObject("author")

            val title = cleanTitle(
                movie?.optString("title")?.takeIf { it.isNotBlank() }
                    ?: json.optString("title").takeIf { it.isNotBlank() }
                    ?: "Видео Одноклассники"
            )

            val thumbnail = movie?.optString("poster")?.takeIf { it.isNotBlank() }
                ?: movie?.optString("thumbnail")?.takeIf { it.isNotBlank() }
                ?: json.optString("poster").takeIf { it.isNotBlank() }

            val authorName = authorObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: json.optString("author").takeIf { it.isNotBlank() }
                ?: "Одноклассники"

            val authorAvatar = authorObj?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
                ?: authorObj?.optString("profileImg")?.takeIf { it.isNotBlank() }

            val durationSeconds = movie?.optLong("duration")?.takeIf { it > 0 }
                ?: json.optLong("duration").takeIf { it > 0 }

            ParsedVideo(
                videoUrl = originalUrl,
                streamUrl = streamUrl,
                title = title,
                thumbnailUrl = thumbnail,
                authorName = authorName,
                authorAvatarUrl = authorAvatar,
                durationSeconds = durationSeconds,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://ok.ru/"
                ),
                sourceName = "Одноклассники"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing OK JSON options", e)
            null
        }
    }

    private fun selectBestMp4Quality(videos: JSONArray): String? {
        val qualityRank = mapOf(
            "ultra" to 8,
            "quad" to 7,
            "full" to 6,
            "hd" to 5,
            "sd" to 4,
            "low" to 3,
            "lowest" to 2,
            "mobile" to 1
        )

        var bestUrl: String? = null
        var bestScore = -1

        for (i in 0 until videos.length()) {
            val video = videos.optJSONObject(i) ?: continue
            val name = video.optString("name").lowercase()
            val url = video.optString("url")
            if (url.isBlank()) continue

            val score = qualityRank[name] ?: 0
            if (score > bestScore) {
                bestScore = score
                bestUrl = cleanUrl(url)
            }
        }

        return bestUrl
    }

    private fun extractStreamUrl(html: String): String? {
        val hlsRegex = """(https?:\/\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)"""
        val hlsMatch = extractRegex(html, hlsRegex)
        if (!hlsMatch.isNullOrBlank()) {
            return cleanUrl(hlsMatch)
        }

        val mp4Regex = """(https?:\/\/[^"'\s<>\\]+?\.mp4(?:\?[^"'\s<>\\]*)?)"""
        val mp4Match = extractRegex(html, mp4Regex)
        if (!mp4Match.isNullOrBlank()) {
            return cleanUrl(mp4Match)
        }

        return null
    }

    private fun cleanUrl(raw: String): String {
        return raw
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "")
            .replace("\"", "")
            .replace("'", "")
            .replace("\\", "")
            .trim()
    }

    private fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(" - Смотреть видео онлайн в Моем Мире", "")
            .replace(" | Одноклассники", "")
            .replace(" - Одноклассники", "")
            .trim()
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*property=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        if (matcher.find()) return unescapeAll(matcher.group(1))
        return null
    }

    private fun extractTagContent(html: String, tag: String): String? {
        val pattern = Pattern.compile(
            """<$tag[^>]*>(.*?)</$tag>""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = pattern.matcher(html)
        return if (matcher.find()) unescapeAll(matcher.group(1)?.trim()) else null
    }

    private fun extractRegex(html: String, regex: String): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun unescapeAll(text: String?): String {
        if (text == null) return ""
        return text
            .replace("\\&quot;", "\"")
            .replace("&quot;", "\"")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#160;", " ")
            .replace("&nbsp;", " ")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
