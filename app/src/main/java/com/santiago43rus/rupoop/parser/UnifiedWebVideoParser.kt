package com.santiago43rus.rupoop.parser

import android.util.Base64
import android.util.Log
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object UnifiedWebVideoParser {

    private const val TAG = "UnifiedWebVideoParser"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun parse(url: String): ParsedVideo? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(url)

            // Direct check for specific dedicated platforms
            if (UniversalVideoParser.isRutubeUrl(normalizedUrl)) {
                val rutubeId = UniversalVideoParser.extractRutubeId(normalizedUrl)
                if (!rutubeId.isNullOrEmpty()) {
                    try {
                        val resp = RetrofitClient.api.getVideoOptions(rutubeId)
                        val m3u8 = resp.videoBalancer?.m3u8
                        if (!m3u8.isNullOrEmpty()) {
                            return@withContext ParsedVideo(
                                videoUrl = normalizedUrl,
                                streamUrl = m3u8,
                                title = "Rutube Видео",
                                thumbnailUrl = resp.thumbnailUrl,
                                authorName = "Rutube",
                                durationSeconds = resp.duration?.toLong(),
                                sourceName = "Rutube"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Rutube parse in UnifiedWeb failed", e)
                    }
                }
            }

            if (VkVideoParser.isVkUrl(normalizedUrl)) {
                val vkResult = VkVideoParser.parse(normalizedUrl)
                if (vkResult != null) return@withContext vkResult
            }

            if (OkVideoParser.isOkUrl(normalizedUrl)) {
                val okResult = OkVideoParser.parse(normalizedUrl)
                if (okResult != null) return@withContext okResult
            }

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

            val rawHtml = response.body.string()
            val html = JsPackerUnpacker.unpack(rawHtml)
            val hostName = getHost(normalizedUrl) ?: "Фильм / Видео"

            // 1. Extract metadata
            var title = extractMetaContent(html, "og:title")
                ?: extractTagContent(html, "h1")
                ?: extractTagContent(html, "title")
                ?: "Онлайн видео"
            title = cleanTitle(title)

            var thumbnail = extractMetaContent(html, "og:image")
                ?: extractMetaContent(html, "og:thumbnail")
                ?: extractRegex(html, """<img[^>]+(?:class|id)=["'][^"']*(?:poster|thumb|cover)[^"']*["'][^>]+src=["']([^"']+)["']""")
                ?: extractRegex(html, """poster=["']([^"']+)["']""")
            if (thumbnail != null) {
                thumbnail = resolveUrl(normalizedUrl, thumbnail)
            }

            // 2. Direct stream in current HTML
            val directStream = extractDirectStream(html, normalizedUrl)
            if (directStream != null) {
                val resolvedStream = resolveUrl(normalizedUrl, directStream)
                return@withContext ParsedVideo(
                    videoUrl = url,
                    streamUrl = resolvedStream,
                    title = title,
                    thumbnailUrl = thumbnail,
                    authorName = hostName,
                    headers = buildHeaders(normalizedUrl),
                    sourceName = hostName
                )
            }

            // 3. Special handling for Kodik / Aniboom embeds directly on page
            val aniboomStream = extractAniboomStream(html, normalizedUrl)
            if (aniboomStream != null) {
                return@withContext ParsedVideo(
                    videoUrl = url,
                    streamUrl = aniboomStream,
                    title = title,
                    thumbnailUrl = thumbnail,
                    authorName = hostName,
                    headers = buildHeaders(normalizedUrl),
                    sourceName = hostName
                )
            }

            // 4. Find iframes and balancer links on page
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

                // If iframe is OK.ru / Odnoklassniki
                if (OkVideoParser.isOkUrl(iframeUrl)) {
                    val okResult = OkVideoParser.parse(iframeUrl)
                    if (okResult != null) {
                        return@withContext okResult.copy(
                            videoUrl = url,
                            title = if (title.isNotBlank() && title != "Онлайн видео") title else okResult.title,
                            thumbnailUrl = thumbnail ?: okResult.thumbnailUrl
                        )
                    }
                }

                // Generic web player iframe (Collaps, Lumex, VideoCDN, Kodik, Alloha, Voidboost, Aniboom, Ashdi, Bazon, Vibix, etc.)
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
        if (depth > 3) return@withContext null
        if (isTrailerOrAdFrame(iframeUrl)) return@withContext null

        try {
            // Kodik specific handling
            if (isKodikDomain(iframeUrl)) {
                val kodikResult = parseKodikIframe(iframeUrl, referrerUrl, fallbackTitle, fallbackThumbnail, sourceName)
                if (kodikResult != null) return@withContext kodikResult
            }

            // Aniboom specific handling
            if (iframeUrl.contains("aniboom.one")) {
                val aniboomResult = parseAniboomIframe(iframeUrl, referrerUrl, fallbackTitle, fallbackThumbnail, sourceName)
                if (aniboomResult != null) return@withContext aniboomResult
            }

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
                    headers = buildHeaders(iframeUrl),
                    sourceName = sourceName
                )
            }

            // 2. Nested iframe in player (e.g. wrapper player -> balancer)
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
        // Priority 1: .m3u8 links in JS variables (file, hls, src, playlist, url, stream, source)
        val m3u8Regexes = listOf(
            """(?:["']file["']|file)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']hls["']|hls)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']src["']|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']source["']|source)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']url["']|url)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']stream["']|stream)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(?:["']playlist["']|playlist)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
            """(https?:\/\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)""",
            """(https?:\\\/\\\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)"""
        )

        for (regex in m3u8Regexes) {
            val match = extractRegex(html, regex)
            if (!match.isNullOrBlank()) {
                return resolveUrl(baseUrl, match)
            }
        }

        // Priority 2: Base64 / Encoded strings (Common in pirate balancers like Collaps / Alloha / CDNMovies / Voidboost)
        val encodedPatterns = listOf(
            Pattern.compile("""(?:["']file["']|file)\s*:\s*["'](#2[a-zA-Z0-9+/=]+|[a-zA-Z0-9+/=]{40,})["']"""),
            Pattern.compile("""(?:["']file["']|file)\s*:\s*["'](#3[a-zA-Z0-9+/=]+|#0[a-zA-Z0-9+/=]+)["']"""),
            Pattern.compile("""window\.atob\(["']([a-zA-Z0-9+/=]+)["']\)""")
        )

        for (encodedPattern in encodedPatterns) {
            val encodedMatcher = encodedPattern.matcher(html)
            if (encodedMatcher.find()) {
                var encodedStr = encodedMatcher.group(1) ?: ""
                if (encodedStr.startsWith("#2") || encodedStr.startsWith("#3") || encodedStr.startsWith("#0")) {
                    encodedStr = encodedStr.substring(2)
                }
                try {
                    val decoded = String(Base64.decode(encodedStr, Base64.DEFAULT))
                    val decodedM3u8 = extractRegex(decoded, """(https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*)""")
                        ?: extractRegex(decoded, """(https?:\/\/[^"'\s<>]+\.mp4[^"'\s<>]*)""")
                        ?: extractRegex(decoded, """(//[^"'\s<>]+\.m3u8[^"'\s<>]*)""")
                    if (decodedM3u8 != null) {
                        return resolveUrl(baseUrl, decodedM3u8)
                    }
                } catch (_: Exception) {}
            }
        }

        // Priority 3: MP4 quality patterns like [1080p]https://... or [720p]https://... or direct MP4
        val mp4QualityRegex = """\[(?:1080p|720p|480p|360p)\](https?:\/\/[^,\s"']+)"""
        val mp4QualityMatch = extractRegex(html, mp4QualityRegex)
        if (!mp4QualityMatch.isNullOrBlank()) {
            return resolveUrl(baseUrl, mp4QualityMatch)
        }

        // Priority 4: Bracketed quality template like [480p,720p,1080p]https://domain.com/path/,480,720,1080,.mp4.m3u8
        val bracketTemplatePattern = Pattern.compile("""\[([0-9p,\s]+)](https?:\/\/[^"'\s]+)""")
        val bracketMatcher = bracketTemplatePattern.matcher(html)
        if (bracketMatcher.find()) {
            val qualities = bracketMatcher.group(1)?.split(",")?.map { it.trim() } ?: emptyList()
            val rawTemplate = bracketMatcher.group(2) ?: ""
            val bestQuality = listOf("1080p", "1080", "720p", "720", "480p", "480", "360p", "360")
                .firstOrNull { q -> qualities.any { it.equals(q, ignoreCase = true) } }
            if (bestQuality != null && rawTemplate.contains(",${bestQuality.removeSuffix("p")},")) {
                val clean = rawTemplate.replace(Regex(""",[0-9,\s]+,"""), bestQuality.removeSuffix("p"))
                return resolveUrl(baseUrl, clean)
            }
        }

        // Priority 5: Direct MP4 URLs
        val directMp4 = extractRegex(html, """(https?:\/\/[^"'\s<>\\]+?\.mp4(?:\?[^"'\s<>\\]*)?)""")
        if (directMp4 != null) {
            return resolveUrl(baseUrl, directMp4)
        }

        return null
    }

    private fun extractDirectStream(html: String, baseUrl: String): String? {
        val unpacked = JsPackerUnpacker.unpack(html)

        // 1. Direct <video> or <source> tags with highest resolution
        val sourcePattern = Pattern.compile(
            """<source[^>]+src=["']([^"']+)["'][^>]*(?:res=["'](\d+)["']|label=["'](\d+p?)["'])?""",
            Pattern.CASE_INSENSITIVE
        )
        val sourceMatcher = sourcePattern.matcher(unpacked)
        var bestSourceUrl: String? = null
        var bestSourceRes = -1

        while (sourceMatcher.find()) {
            val src = sourceMatcher.group(1) ?: continue
            if (!src.contains(".mp4") && !src.contains(".m3u8")) continue

            val resStr = sourceMatcher.group(2) ?: sourceMatcher.group(3)?.removeSuffix("p")
            val res = resStr?.toIntOrNull() ?: if (src.contains("1080")) 1080 else if (src.contains("720")) 720 else 480

            if (res > bestSourceRes) {
                bestSourceRes = res
                bestSourceUrl = src
            }
        }
        if (bestSourceUrl != null) {
            return resolveUrl(baseUrl, bestSourceUrl)
        }

        val directVideoTag = extractRegex(unpacked, """<video[^>]+src=["']([^"']+)["']""")
        if (directVideoTag != null && (directVideoTag.contains(".m3u8") || directVideoTag.contains(".mp4"))) {
            return resolveUrl(baseUrl, directVideoTag)
        }

        val m3u8InScript = extractRegex(unpacked, """["'](https?:\/\/[^"'\s<>\\]+?\.m3u8(?:\?[^"'\s<>\\]*)?)["']""")
        if (m3u8InScript != null) {
            return resolveUrl(baseUrl, m3u8InScript)
        }

        return null
    }

    private fun extractAniboomStream(html: String, baseUrl: String): String? {
        val dataParamsPattern = Pattern.compile("""data-parameters=["'](.*?)["']""", Pattern.DOTALL)
        val matcher = dataParamsPattern.matcher(html)
        if (matcher.find()) {
            val raw = unescapeHtml(matcher.group(1)) ?: ""
            val hlsSrc = extractRegex(raw, """(?:hls|src|file)["']?\s*:\s*["']?(\{.*?\})""")
                ?: extractRegex(raw, """["'](https?:\\\/\\\/[^"'\s<>]+\.m3u8[^"'\s<>]*)["']""")
                ?: extractRegex(raw, """["'](https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*)""")
            if (hlsSrc != null) {
                val clean = hlsSrc.replace("\\/", "/").replace("\\\"", "\"")
                val streamUrl = extractRegex(clean, """(https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*)""") ?: clean
                return resolveUrl(baseUrl, streamUrl)
            }
        }
        return null
    }

    private fun parseAniboomIframe(
        iframeUrl: String,
        referrerUrl: String,
        fallbackTitle: String,
        fallbackThumbnail: String?,
        sourceName: String
    ): ParsedVideo? {
        return try {
            val request = Request.Builder()
                .url(iframeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referrerUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body.string()

            val stream = extractAniboomStream(html, iframeUrl)
                ?: extractStreamFromPlayerHtml(html, iframeUrl)
            if (stream != null) {
                ParsedVideo(
                    videoUrl = referrerUrl,
                    streamUrl = stream,
                    title = fallbackTitle,
                    thumbnailUrl = fallbackThumbnail,
                    authorName = sourceName,
                    headers = buildHeaders(iframeUrl),
                    sourceName = sourceName
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isKodikDomain(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("kodik.") || lower.contains("aniqit.") ||
                lower.contains("anivod.") || lower.contains("playkodik.")
    }

    private fun parseKodikIframe(
        iframeUrl: String,
        referrerUrl: String,
        fallbackTitle: String,
        fallbackThumbnail: String?,
        sourceName: String
    ): ParsedVideo? {
        return try {
            val request = Request.Builder()
                .url(iframeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referrerUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body.string()
            val unpacked = JsPackerUnpacker.unpack(html)

            // Try direct extraction from Kodik HTML
            var streamUrl = extractStreamFromPlayerHtml(unpacked, iframeUrl)

            // Kodik /gvi API request if available
            if (streamUrl == null) {
                val domain = URI(iframeUrl).host ?: "kodik.info"
                val urlParamsMatch = extractRegex(unpacked, """urlParams\s*=\s*['"](\{.*?\})['"]""")
                    ?: extractRegex(unpacked, """videoInfo\s*=\s*(\{.*?\})""")
                if (urlParamsMatch != null) {
                    val paramsJson = JSONObject(urlParamsMatch.replace("\\\"", "\""))
                    val formBuilder = FormBody.Builder()
                        .add("bad_user", "true")
                        .add("info", "{}")

                    val keys = paramsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        formBuilder.add(key, paramsJson.optString(key))
                    }

                    val gviReq = Request.Builder()
                        .url("https://$domain/gvi")
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", iframeUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .post(formBuilder.build())
                        .build()

                    val gviResp = httpClient.newCall(gviReq).execute()
                    if (gviResp.isSuccessful) {
                        val gviBody = gviResp.body.string()
                        val gviJson = JSONObject(gviBody)
                        val links = gviJson.optJSONObject("links")
                        if (links != null) {
                            val qualityPriority = listOf("1080", "720", "480", "360", "240")
                            for (q in qualityPriority) {
                                val arr = links.optJSONArray(q)
                                if (arr != null && arr.length() > 0) {
                                    val linkObj = arr.optJSONObject(0)
                                    val src = linkObj?.optString("src")
                                    if (!src.isNullOrBlank()) {
                                        streamUrl = resolveUrl(iframeUrl, src)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (streamUrl != null) {
                ParsedVideo(
                    videoUrl = referrerUrl,
                    streamUrl = streamUrl,
                    title = fallbackTitle,
                    thumbnailUrl = fallbackThumbnail,
                    authorName = if (sourceName.isNotBlank() && sourceName != "Фильм / Видео") sourceName else "Kodik",
                    headers = buildHeaders(iframeUrl),
                    sourceName = if (sourceName.isNotBlank() && sourceName != "Фильм / Видео") sourceName else "Kodik"
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPotentialPlayerUrls(html: String, baseUrl: String): List<String> {
        val results = mutableListOf<String>()

        // 1. Iframes (src, data-src, data-player, data-frame, data-url)
        val iframePattern = Pattern.compile(
            """<iframe[^>]+(?:src|data-src|data-player|data-frame|data-url)=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val iframeMatcher = iframePattern.matcher(html)
        while (iframeMatcher.find()) {
            val rawSrc = iframeMatcher.group(1) ?: continue
            if (isTrailerOrAdFrame(rawSrc)) continue
            val resolved = resolveUrl(baseUrl, rawSrc)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                results.add(resolved)
            }
        }

        // 2. Player container elements with data-src, data-player, data-url
        val playerContainerPattern = Pattern.compile(
            """<(?:div|section|span|a)[^>]+(?:data-src|data-player|data-url|data-balancer)=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val containerMatcher = playerContainerPattern.matcher(html)
        while (containerMatcher.find()) {
            val rawSrc = containerMatcher.group(1) ?: continue
            if (isTrailerOrAdFrame(rawSrc)) continue
            val resolved = resolveUrl(baseUrl, rawSrc)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                results.add(resolved)
            }
        }

        // 3. Balancer embeds in script variables (e.g. data-player-url, iframe src in JS, voidboost, collaps, alloha, bazon, vibix, lumex)
        val jsEmbedPattern = Pattern.compile(
            """(?:src|url|link|player|balancer|iframe|embed)\s*[:=]\s*["'](https?:\/\/[^"'\s]+(?:embed|player|video|movie|collaps|lumex|alloha|kodik|voidboost|bazon|vibix|ashdi|aniboom|lordfilm|kinogo|rezka|seasonvar|filmix|zetflix)[^"'\s]*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val jsEmbedMatcher = jsEmbedPattern.matcher(html)
        while (jsEmbedMatcher.find()) {
            val rawSrc = jsEmbedMatcher.group(1) ?: continue
            if (isTrailerOrAdFrame(rawSrc)) continue
            val resolved = resolveUrl(baseUrl, rawSrc)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                results.add(resolved)
            }
        }

        // Sort: High priority balancers first
        return results.distinct().sortedByDescending { url ->
            val lower = url.lowercase()
            when {
                lower.contains("collaps") || lower.contains("lumex") || lower.contains("alloha") ||
                        lower.contains("kodik") || lower.contains("aniqit") || lower.contains("voidboost") ||
                        lower.contains("aniboom") || lower.contains("ashdi") || lower.contains("bazon") ||
                        lower.contains("vibix") || lower.contains("ok.ru") || lower.contains("vk.com") ||
                        lower.contains("vkvideo.ru") -> 4
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
                lower.contains("top100") || lower.contains("analytics") || lower.contains("partner") ||
                lower.contains("reklama") || lower.contains("adv")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val cleanRel = cleanStreamUrl(relativeUrl)
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

    private fun cleanStreamUrl(url: String): String {
        return url
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()
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

    fun buildHeaders(targetUrl: String): Map<String, String> {
        val host = getHost(targetUrl) ?: ""
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to targetUrl,
            "Origin" to if (host.isNotEmpty()) "https://$host" else targetUrl
        )
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*property=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        if (matcher.find()) return unescapeHtml(matcher.group(1))

        val namePattern = Pattern.compile(
            """<meta\s+[^>]*name=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val nameMatcher = namePattern.matcher(html)
        if (nameMatcher.find()) return unescapeHtml(nameMatcher.group(1))

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
            .replace(" | Rezka", "")
            .replace(" | Jut.su", "")
            .replace(" | AnimeGo", "")
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
