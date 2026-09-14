package com.santiago43rus.rupoop.network

import android.util.Log
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object OkSearchEngine {

    private const val TAG = "OkSearchEngine"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResult>()

        try {
            val searchResults = searchViaOkVideoSearch(trimmed)
            if (searchResults.isNotEmpty()) {
                results.addAll(searchResults)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OK search failed for query: $trimmed", e)
        }

        return@withContext results.distinctBy { it.videoUrl }
    }

    private fun searchViaOkVideoSearch(query: String): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://ok.ru/video/search?st.v.sq=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val html = response.body.string()

        val list = mutableListOf<SearchResult>()

        // 1. Extract and parse data-props from <video-search-result data-props="...">
        val tagMarker = "<video-search-result"
        val tagStart = html.indexOf(tagMarker)
        if (tagStart != -1) {
            try {
                val propsMarker = "data-props=\""
                val propsStart = html.indexOf(propsMarker, tagStart)
                if (propsStart != -1) {
                    val jsonStart = propsStart + propsMarker.length
                    val endIdx = html.indexOf("\"", jsonStart)
                    if (endIdx > jsonStart) {
                        val rawJson = html.substring(jsonStart, endIdx)
                        val cleanJson = rawJson.replace("&quot;", "\"")
                            .replace("&amp;", "&")
                            .replace("&#39;", "'")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")

                        val root = RetrofitClient.json.parseToJsonElement(cleanJson).jsonObject
                        val videosObj = root["videos"]?.jsonObject
                        val items = videosObj?.get("list")?.jsonArray

                        if (items != null) {
                            for (itemElem in items) {
                                val item = itemElem.jsonObject
                                val movie = item["movie"]?.jsonObject
                                val id = movie?.get("id")?.jsonPrimitive?.contentOrNull
                                    ?: item["id"]?.jsonPrimitive?.contentOrNull ?: continue

                                val rawTitle = movie?.get("title")?.jsonPrimitive?.contentOrNull
                                    ?: item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                val title = try {
                                    URLDecoder.decode(rawTitle, "UTF-8")
                                } catch (_: Exception) {
                                    rawTitle
                                }

                                if (title.isBlank()) continue

                                val thumb = movie?.get("thumbnail")?.jsonObject?.get("big")?.jsonPrimitive?.contentOrNull
                                    ?: movie?.get("thumbnail")?.jsonObject?.get("small")?.jsonPrimitive?.contentOrNull
                                    ?: item["imageUrl"]?.jsonPrimitive?.contentOrNull

                                val durMs = movie?.get("duration")?.jsonPrimitive?.longOrNull
                                val durSec = durMs?.div(1000)?.toInt()

                                val ownerObj = item["owner"]?.jsonObject
                                val userObj = ownerObj?.get("user")?.jsonObject
                                val groupObj = ownerObj?.get("group")?.jsonObject
                                val authorName = userObj?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: groupObj?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: "Одноклассники"

                                val videoUrl = "https://ok.ru/video/$id"

                                list.add(
                                    SearchResult(
                                        videoUrl = videoUrl,
                                        title = title,
                                        thumbnailUrl = thumb,
                                        author = Author(name = authorName),
                                        duration = durSec
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing OK data-props JSON", e)
            }
        }

        // 2. Fallback regex if data-props layout was changed
        if (list.isEmpty()) {
            val cardPattern = Pattern.compile(
                """data-movie-id=["'](\d+)["'](.*?)(?=<div[^>]+data-movie-id=|</body>|</html>)""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val matcher = cardPattern.matcher(html)
            val seenIds = mutableSetOf<String>()

            while (matcher.find()) {
                val movieId = matcher.group(1) ?: continue
                if (!seenIds.add(movieId)) continue

                val block = matcher.group(2) ?: ""
                val poster = extractRegex(block, """data-poster-src=["'](https?://[^"']+)["']""")
                    ?: extractRegex(block, """src=["'](https?://[^"']+)["']""")
                    ?.replace("&amp;", "&")

                val durationText = extractRegex(block, """class=["'][^"']*video-card_duration[^"']*["'][^>]*>(.*?)<""")
                val durationSec = parseDurationSeconds(durationText)

                var rawTitle = extractRegex(block, """title=["']([^"']+)["']""")
                    ?: extractRegex(block, """class=["'][^"']*video_search_result_link[^"']*["'][^>]*>(.*?)<""")
                    ?: extractRegex(block, """alt=["']([^"']+)["']""")
                    ?: "Видео Одноклассники"

                if (rawTitle.contains("%")) {
                    try {
                        rawTitle = URLDecoder.decode(rawTitle, "UTF-8")
                    } catch (_: Exception) {}
                }

                val title = unescapeHtml(rawTitle).trim()
                if (title.isBlank()) continue

                val videoUrl = "https://ok.ru/video/$movieId"

                list.add(
                    SearchResult(
                        videoUrl = videoUrl,
                        title = title,
                        thumbnailUrl = poster,
                        author = Author(name = "Одноклассники"),
                        duration = durationSec
                    )
                )
            }
        }

        return list
    }

    private fun parseDurationSeconds(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val clean = text.trim()
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRegex(input: String, regex: String): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun unescapeHtml(input: String): String {
        return input.replace("<[^>]*>".toRegex(), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .trim()
    }
}
