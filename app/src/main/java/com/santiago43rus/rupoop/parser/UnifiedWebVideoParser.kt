package com.santiago43rus.rupoop.parser

import android.util.Base64
import android.util.Log
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object UnifiedWebVideoParser {

    private const val TAG = "UnifiedWebVideoParser"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun parse(url: String): ParsedVideo? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(url)
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Web page request failed: HTTP ${response.code}")
                return@withContext null
            }

            val html = response.body.string()
            val hostName = getHost(normalizedUrl) ?: "Фильм / Видео"

            // 1. Extract metadata
            var title = extractMetaContent(html, "og:title")
                ?: extractTagContent(html, "h1")
                ?: extractTagContent(html, "title")
                ?: "Онлайн видео"
            title = cleanTitle(title)

            var thumbnail = extractMetaContent(html, "og:image")
                ?: extractRegex(html, """<img[^>]+(?:class|id)=["'][^"']*(?:poster|thumb|cover)[^"']*["'][^>]+src=["']([^"']+)["']""")
                ?: extractRegex(html, """poster=["']([^"']+)["']""")
            if (thumbnail != null) {
                thumbnail = resolveUrl(normalizedUrl, thumbnail)
            }

            // 2. Direct stream in current HTML
            val directStream = extractDirectStream(html)
            if (directStream != null) {
                val resolvedStream = resolveUrl(normalizedUrl, directStream)
                return@withContext ParsedVideo(
                    videoUrl = url,
                    streamUrl = resolvedStream,
                    title = title,
                    thumbnailUrl = thumbnail,
                    authorName = hostName,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to normalizedUrl
                    ),
                    sourceName = hostName
                )
            }

            // 3. Find iframes and balancer links on page
            val iframes = extractPotentialPlayerUrls(html, normalizedUrl)
            for (iframeUrl in iframes) {
                // If iframe is Rutube
                if (iframeUrl.contains("rutube.ru")) {
                    val rutubeId = UniversalVideoParser.extractRutubeId(iframeUrl)
                    if (!rutubeId.isNullOrEmpty()) {
                        try {
                            val rutubeResp = RetrofitClient.api.getVideoOptions(rutubeId)
                            val m3u8 = rutubeResp.videoBalancer?.m3u8
                            if (!m3u8.isNullOrEmpty()) {
                                return@withContext ParsedVideo(
                                    videoUrl = url,
                                    streamUrl = m3u8,
                                    title = title,
                                    thumbnailUrl = thumbnail ?: rutubeResp.thumbnailUrl,
                                    authorName = "Rutube",
                                    sourceName = "Rutube"
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Rutube embed parse error: $iframeUrl", e)
                        }
                    }
                }

                // If iframe is VK
                if (VkVideoParser.isVkUrl(iframeUrl)) {
                    val vkResult = VkVideoParser.parse(iframeUrl)
                    if (vkResult != null) {
                        return@withContext vkResult.copy(
                            videoUrl = url,
                            title = if (title.isNotBlank() && title != "Онлайн видео") title else vkResult.title,
                            thumbnailUrl = thumbnail ?: vkResult.thumbnailUrl
                        )
                    }
                }

                // Generic web player iframe (Collaps, Lumex, VideoCDN, Kodik, Alloha, Voidboost, etc.)
                val playerVideo = parsePlayerIframe(iframeUrl, normalizedUrl, title, thumbnail, hostName)
                if (playerVideo != null) {
                    return@withContext playerVideo.copy(videoUrl = url)
                }
            }

            Log.w(TAG, "No video stream found for web URL: $url")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing web video URL: $url", e)
            null
        }
    }

    private suspend fun parsePlayerIframe(
        iframeUrl: String,
        referrerUrl: String,
        fallbackTitle: String,
        fallbackThumbnail: String?,
        sourceName: String,
        depth: Int = 0
    ): ParsedVideo? = withContext(Dispatchers.IO) {
        if (depth > 2) return@withContext null
        if (isTrailerOrAdFrame(iframeUrl)) return@withContext null

        try {
            val request = Request.Builder()
                .url(iframeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referrerUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val rawHtml = response.body.string()
            val html = JsPackerUnpacker.unpack(rawHtml)

            // 1. Direct stream in player HTML
            val streamUrl = extractStreamFromPlayerHtml(html, iframeUrl)
            if (streamUrl != null) {
                val resolvedStream = resolveUrl(iframeUrl, streamUrl)
                return@withContext ParsedVideo(
                    videoUrl = referrerUrl,
                    streamUrl = resolvedStream,
                    title = fallbackTitle,
                    thumbnailUrl = fallbackThumbnail,
                    authorName = sourceName,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to iframeUrl
                    ),
                    sourceName = sourceName
                )
            }

            // 2. Nested iframe in player (e.g. wrapper player)
            val nestedIframes = extractPotentialPlayerUrls(html, iframeUrl)
            for (nested in nestedIframes) {
                if (nested != iframeUrl) {
                    val nestedResult = parsePlayerIframe(nested, iframeUrl, fallbackTitle, fallbackThumbnail, sourceName, depth + 1)
                    if (nestedResult != null) return@withContext nestedResult
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing player iframe: $iframeUrl", e)
            null
        }
    }

    private fun extractStreamFromPlayerHtml(html: String, baseUrl: String): String? {
        // Priority 1: .m3u8 links in JS variables (file, hls, src, playlist)
        val m3u8Regexes = listOf(
            """(?:["']file["']|file)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']hls["']|hls)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']src["']|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']playlist["']|playlist)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(https?:\/\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)""",
            """(https?:\\\/\\\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)"""
        )

        for (regex in m3u8Regexes) {
            val match = extractRegex(html, regex)
            if (!match.isNullOrBlank()) {
                val clean = match.replace("\\/", "/").replace("\\u0026", "&")
                return resolveUrl(baseUrl, clean)
            }
        }

        // Priority 2: Base64 / Encoded strings (Common in pirate balancers like Collaps / Alloha / CDNMovies)
        val encodedPattern = Pattern.compile("""(?:["']file["']|file)\s*:\s*["'](#2[a-zA-Z0-9+/=]+|[a-zA-Z0-9+/=]{40,})["']""")
        val encodedMatcher = encodedPattern.matcher(html)
        if (encodedMatcher.find()) {
            var encodedStr = encodedMatcher.group(1) ?: ""
            if (encodedStr.startsWith("#2")) {
                encodedStr = encodedStr.substring(2)
            }
            try {
                val decoded = String(Base64.decode(encodedStr, Base64.DEFAULT))
                val decodedM3u8 = extractRegex(decoded, """(https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*)""")
                    ?: extractRegex(decoded, """(https?:\/\/[^"'\s<>]+\.mp4[^"'\s<>]*)""")
                if (decodedM3u8 != null) {
                    return resolveUrl(baseUrl, decodedM3u8)
                }
            } catch (_: Exception) {}
        }

        // Priority 3: MP4 quality patterns like [1080p]https://... or [720p]https://... or direct MP4
        val mp4QualityRegex = """\[(?:1080p|720p|480p|360p)\](https?:\/\/[^,\s"']+)"""
        val mp4QualityMatch = extractRegex(html, mp4QualityRegex)
        if (!mp4QualityMatch.isNullOrBlank()) {
            return resolveUrl(baseUrl, mp4QualityMatch)
        }

        val directMp4 = extractRegex(html, """(https?:\/\/[^"'\s<>\\]+?\.mp4(?:\?[^"'\s<>\\]*)?)""")
        if (directMp4 != null) {
            return resolveUrl(baseUrl, directMp4.replace("\\/", "/"))
        }

        return null
    }

    private fun extractDirectStream(html: String): String? {
        val unpacked = JsPackerUnpacker.unpack(html)

        val directVideoTag = extractRegex(unpacked, """<video[^>]+src=["']([^"']+)["']""")
            ?: extractRegex(unpacked, """<source[^>]+src=["']([^"']+)["']""")
        if (directVideoTag != null && (directVideoTag.contains(".m3u8") || directVideoTag.contains(".mp4"))) {
            return directVideoTag
        }

        val m3u8InScript = extractRegex(unpacked, """["'](https?:\/\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)["']""")
        if (m3u8InScript != null) {
            return m3u8InScript.replace("\\/", "/")
        }

        return null
    }

    private fun extractPotentialPlayerUrls(html: String, baseUrl: String): List<String> {
        val results = mutableListOf<String>()

        // 1. Iframes (src, data-src, data-player)
        val iframePattern = Pattern.compile("""<iframe[^>]+(?:src|data-src|data-player|data-frame)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val iframeMatcher = iframePattern.matcher(html)
        while (iframeMatcher.find()) {
            val rawSrc = iframeMatcher.group(1) ?: continue
            if (isTrailerOrAdFrame(rawSrc)) continue
            val resolved = resolveUrl(baseUrl, rawSrc)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                results.add(resolved)
            }
        }

        // 2. Balancer embeds in script variables (e.g. data-player-url, iframe src in JS, voidboost, collaps, alloha)
        val jsEmbedPattern = Pattern.compile("""(?:src|url|link|player|balancer|iframe)\s*:\s*["'](https?:\/\/[^"'\s]+(?:embed|player|video|movie|collaps|lumex|alloha|kodik|voidboost|bazon|vibix|lordfilm)[^"'\s]*)["']""", Pattern.CASE_INSENSITIVE)
        val jsEmbedMatcher = jsEmbedPattern.matcher(html)
        while (jsEmbedMatcher.find()) {
            val rawSrc = jsEmbedMatcher.group(1) ?: continue
            if (isTrailerOrAdFrame(rawSrc)) continue
            val resolved = resolveUrl(baseUrl, rawSrc)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                results.add(resolved)
            }
        }

        // Sort: Balancer URLs first (collaps, lumex, alloha, kodik, voidboost, videocdn, cdnmovies, etc.)
        return results.distinct().sortedByDescending { url ->
            val lower = url.lowercase()
            when {
                lower.contains("collaps") || lower.contains("lumex") || lower.contains("alloha") || lower.contains("kodik") || lower.contains("voidboost") -> 3
                lower.contains("embed") || lower.contains("player") || lower.contains("video") -> 2
                else -> 1
            }
        }
    }

    private fun isTrailerOrAdFrame(src: String): Boolean {
        val lower = src.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("vimeo.com") ||
                lower.contains("google") || lower.contains("yandex") || lower.contains("banner") ||
                lower.contains("counter") || lower.contains("adriver") || lower.contains("tbn") ||
                lower.contains("top100") || lower.contains("analytics") || lower.contains("partner")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val cleanRel = relativeUrl.trim()
        if (cleanRel.startsWith("//")) {
            return "https:$cleanRel"
        }
        if (cleanRel.startsWith("http://") || cleanRel.startsWith("https://")) {
            return cleanRel
        }
        return try {
            val base = URI(baseUrl)
            base.resolve(cleanRel).toString()
        } catch (_: Exception) {
            cleanRel
        }
    }

    private fun normalizeUrl(url: String): String {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        return cleanUrl
    }

    private fun getHost(url: String): String? {
        return try {
            val host = URI(url).host ?: return null
            host.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*property=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = pattern.matcher(html)
        if (matcher.find()) return unescapeHtml(matcher.group(1))

        val namePattern = Pattern.compile(
            """<meta\s+[^>]*name=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        matcher = namePattern.matcher(html)
        if (matcher.find()) return unescapeHtml(matcher.group(1))

        return null
    }

    private fun extractTagContent(html: String, tag: String): String? {
        val pattern = Pattern.compile("""<$tag[^>]*>(.*?)</$tag>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) unescapeHtml(matcher.group(1)?.trim()) else null
    }

    private fun extractRegex(html: String, regex: String): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\s*(?:смотреть онлайн|в хорошем качестве|hd\s*\d+|1080p|720p|бесплатно|все серии).*"""), "")
            .replace(" - Смотреть онлайн", "")
            .replace(" | Lordfilm", "")
            .replace(" | Лордфильм", "")
            .replace(" | Киного", "")
            .replace(" | HDRezka", "")
            .trim()
    }

    private fun unescapeHtml(text: String?): String? {
        if (text == null) return null
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
