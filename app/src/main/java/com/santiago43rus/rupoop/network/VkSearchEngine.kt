package com.santiago43rus.rupoop.network

import android.util.Log
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VkSearchEngine {

    private const val TAG = "VkSearchEngine"
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
            val ajaxResults = searchViaAlVideo(trimmed)
            if (ajaxResults.isNotEmpty()) {
                results.addAll(ajaxResults)
            }
        } catch (e: Exception) {
            Log.w(TAG, "VK searchViaAlVideo error", e)
        }

        return@withContext results.distinctBy { it.videoUrl }
    }

    private fun searchViaAlVideo(query: String): List<SearchResult> {
        val formBody = FormBody.Builder()
            .add("act", "search_video")
            .add("al", "1")
            .add("q", query)
            .add("offset", "0")
            .build()

        val request = Request.Builder()
            .url("https://vk.com/al_video.php")
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://vk.com/video")
            .post(formBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body.string()

        val list = mutableListOf<SearchResult>()

        val jsonStart = body.indexOf('{')
        val jsonEnd = body.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                val jsonStr = body.substring(jsonStart, jsonEnd + 1)
                val element = RetrofitClient.json.parseToJsonElement(jsonStr).jsonObject
                val payload = element["payload"]?.jsonArray
                val subArray = payload?.getOrNull(1)?.jsonArray
                val dict = subArray?.getOrNull(2)?.jsonObject
                val itemsArray = dict?.get("list")?.jsonArray

                if (itemsArray != null) {
                    for (itemElem in itemsArray) {
                        val item = itemElem.jsonArray
                        if (item.size < 4) continue

                        val ownerId = item[0].jsonPrimitive.longOrNull ?: continue
                        val videoId = item[1].jsonPrimitive.longOrNull ?: continue
                        val rawThumb = item[2].jsonPrimitive.contentOrNull ?: ""
                        val rawTitle = item[3].jsonPrimitive.contentOrNull ?: ""
                        val durationText = item.getOrNull(5)?.jsonPrimitive?.contentOrNull ?: ""
                        val rawOwner = item.getOrNull(8)?.jsonPrimitive?.contentOrNull ?: ""

                        val title = unescapeHtml(rawTitle).trim()
                        if (title.isBlank()) continue

                        val videoUrl = "https://vk.com/video${ownerId}_$videoId"
                        val thumb = rawThumb.replace("\\/", "/").takeIf { it.startsWith("http") }
                        val authorName = extractRegex(rawOwner, """<a[^>]*>(.*?)</a>""")
                            ?.let { unescapeHtml(it).trim() } ?: "VK Видео"

                        val durationSec = parseDurationSeconds(durationText)

                        list.add(
                            SearchResult(
                                videoUrl = videoUrl,
                                title = title,
                                thumbnailUrl = thumb,
                                author = Author(name = authorName),
                                duration = durationSec
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing VK JSON", e)
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
            .replace("&#33;", "!")
            .trim()
    }
}
