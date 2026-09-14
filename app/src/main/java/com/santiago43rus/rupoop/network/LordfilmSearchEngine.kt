package com.santiago43rus.rupoop.network

import android.util.Log
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object LordfilmSearchEngine {

    private const val TAG = "LordfilmSearchEngine"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SEARCH_MIRRORS = listOf(
        "https://lordfilm.top/index.php?do=search",
        "https://lordserials.fan/index.php?do=search"
    )

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResult>()

        for (endpoint in SEARCH_MIRRORS) {
            try {
                val mirrorResults = searchDleMirror(endpoint, trimmed)
                if (mirrorResults.isNotEmpty()) {
                    results.addAll(mirrorResults)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lordfilm search mirror $endpoint failed", e)
            }
        }

        return@withContext results.distinctBy { it.videoUrl }
    }

    private fun searchDleMirror(endpoint: String, query: String): List<SearchResult> {
        val formBody = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("search_start", "0")
            .add("full_search", "0")
            .add("result_from", "1")
            .add("story", query)
            .build()

        val hostDomain = endpoint.substringBefore("/index.php")
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$hostDomain/")
            .post(formBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val html = response.body.string()

        val list = mutableListOf<SearchResult>()
        val seenUrls = mutableSetOf<String>()

        // 1. Lordfilm.top card format (<div class="item expand-link">...<a class="item__title" href="...">...</a>)
        val topPattern = Pattern.compile(
            """<div class=["']item expand-link[^"']*["'].*?<img[^>]+src=["']([^"']+)["'][^>]*alt=["']([^"']+)["'].*?<a[^>]+href=["']([^"']+\.html)["']""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val mTop = topPattern.matcher(html)
        while (mTop.find()) {
            var poster = mTop.group(1) ?: ""
            val rawTitle = mTop.group(2) ?: ""
            val rawUrl = mTop.group(3) ?: continue

            val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "$hostDomain$rawUrl"
            if (!seenUrls.add(fullUrl)) continue

            val title = unescapeHtml(rawTitle).trim()
            if (title.isBlank()) continue

            if (!poster.startsWith("http")) {
                poster = "$hostDomain$poster"
            }

            list.add(
                SearchResult(
                    videoUrl = fullUrl,
                    title = title,
                    thumbnailUrl = poster,
                    author = Author(name = "Lordfilm"),
                    duration = null
                )
            )
        }

        // 2. Lordserials.fan th-item card format (<div class="th-item">...<a class="th-in" href="...">...<img src="..." alt="...">...)
        val thPattern = Pattern.compile(
            """<div class=["']th-item["'][^>]*>.*?<a class=["'][^"']*th-in[^"']*["']\s+href=["']([^"']+)["'][^>]*>.*?<img\s+src=["']([^"']+)["']\s+alt=["']([^"']+)["'].*?</div>\s*</div>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val mTh = thPattern.matcher(html)
        while (mTh.find()) {
            val rawUrl = mTh.group(1) ?: continue
            var poster = mTh.group(2) ?: ""
            val rawTitle = mTh.group(3) ?: ""

            val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "$hostDomain$rawUrl"
            if (!seenUrls.add(fullUrl)) continue

            val title = unescapeHtml(rawTitle).trim()
            if (title.isBlank()) continue

            if (!poster.startsWith("http")) {
                poster = "$hostDomain$poster"
            }

            list.add(
                SearchResult(
                    videoUrl = fullUrl,
                    title = title,
                    thumbnailUrl = poster,
                    author = Author(name = "Lordfilm"),
                    duration = null
                )
            )
        }

        // 3. Fallback generic DLE html card format
        if (list.isEmpty()) {
            val fbPattern = Pattern.compile(
                """href=["'](https?://[^"']+\.html)["'][^>]*>.*?<img[^>]+src=["']([^"']+)["'][^>]*alt=["']([^"']+)["']""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val fbMatcher = fbPattern.matcher(html)
            while (fbMatcher.find()) {
                val filmUrl = fbMatcher.group(1) ?: continue
                if (!seenUrls.add(filmUrl)) continue
                val poster = fbMatcher.group(2)
                val title = unescapeHtml(fbMatcher.group(3) ?: "").trim()
                if (title.length < 2) continue

                list.add(
                    SearchResult(
                        videoUrl = filmUrl,
                        title = title,
                        thumbnailUrl = poster,
                        author = Author(name = "Lordfilm"),
                        duration = null
                    )
                )
            }
        }

        return list
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
