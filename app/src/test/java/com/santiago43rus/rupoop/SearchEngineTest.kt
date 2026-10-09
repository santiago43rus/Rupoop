package com.santiago43rus.rupoop

import com.santiago43rus.rupoop.data.SearchSource
import com.santiago43rus.rupoop.network.LordfilmSearchEngine
import com.santiago43rus.rupoop.network.OkSearchEngine
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.network.VkSearchEngine
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder
import java.util.regex.Pattern

class SearchEngineTest {

    @Test
    fun testSearchSourcesEnum() {
        assertEquals(5, SearchSource.entries.size)
        assertEquals("Все", SearchSource.ALL.displayName)
        assertEquals("Rutube", SearchSource.RUTUBE.displayName)
        assertEquals("ВКонтакте", SearchSource.VK.displayName)
        assertEquals("Одноклассники", SearchSource.OK.displayName)
        assertEquals("Lordfilm", SearchSource.LORDFILM.displayName)
    }

    @Test
    fun testEmptyQueryHandling() = runBlocking {
        assertTrue(VkSearchEngine.search("").isEmpty())
        assertTrue(OkSearchEngine.search("").isEmpty())
        assertTrue(LordfilmSearchEngine.search("").isEmpty())
    }

    @Test
    fun testLiveVkSearch() = runBlocking {
        val results = VkSearchEngine.search("майнкрафт")
        println("VK results: ${results.size}")
        results.take(3).forEach { println("VK: ${it.title} -> ${it.videoUrl}") }
        assertTrue("VK search should return items", results.isNotEmpty())
    }

    @Test
    fun testLiveOkSearch() = runBlocking {
        val queries = listOf("ведьмак", "майнкрафт", "фильмы")
        for (q in queries) {
            val results = OkSearchEngine.search(q)
            println("OK results for '$q': ${results.size}")
            results.take(2).forEach {
                println("OK: ${it.title} (${it.duration}s) -> ${it.videoUrl}")
            }
            assertTrue("OK search for '$q' should return items", results.isNotEmpty())
        }
    }

    @Test
    fun testMockLordfilmSearchParsing() {
        val mockHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="item expand-link">
                    <img src="https://example.com/poster.jpg" alt="Интерстеллар (2014)">
                    <a class="item__title" href="/469-interstellar-interstellar-2014.html">Интерстеллар</a>
                    <span class="news-cat">Фильмы</span>
                </div>
                <div class="th-item">
                    <a class="th-in" href="/zarubezhnye-serialy/618-vedmak.html">
                        <img src="/uploads/posts/witcher.jpg" alt="Ведьмак 1 сезон">
                    </a>
                </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = LordfilmSearchEngine.parseCardsFromHtml(mockHtml, "https://lordfilm.top")
        assertEquals(2, parsed.size)
        assertEquals("Интерстеллар (2014)", parsed[0].title)
        assertEquals("https://lordfilm.top/469-interstellar-interstellar-2014.html", parsed[0].videoUrl)
        assertEquals("Lordfilm", parsed[0].author?.name)
        assertEquals("LORDFILM", parsed[0].author?.platform)

        assertEquals("Ведьмак 1 сезон", parsed[1].title)
        assertEquals("https://lordfilm.top/zarubezhnye-serialy/618-vedmak.html", parsed[1].videoUrl)
    }

    @Test
    fun testInspectLordfilmNotFound() = runBlocking {
        val results = LordfilmSearchEngine.search("qwertyuiopasdfghjklzxcvbnm123456789")
        assertTrue("Lordfilm must return empty list for nonexistent query", results.isEmpty())
    }

    @Test
    fun testVkMetadataParsed() = runBlocking {
        val results = VkSearchEngine.search("майнкрафт")
        assertTrue(results.isNotEmpty())
        val first = results.first()
        println("VK first: title=${first.title}, hits=${first.hits}, date=${first.publicationTs}, avatar=${first.author?.avatarUrl}")
        assertNotNull(first.author?.avatarUrl)
        assertTrue(first.author?.avatarUrl?.startsWith("http") == true)
        assertNotNull(first.hits)
        assertTrue((first.hits ?: 0) > 0)
        assertNotNull(first.publicationTs)
    }

    @Test
    fun testOkMetadataParsed() = runBlocking {
        val results = OkSearchEngine.search("майнкрафт")
        assertTrue(results.isNotEmpty())
        val first = results.first()
        println("OK first: title=${first.title}, hits=${first.hits}, date=${first.publicationTs}, avatar=${first.author?.avatarUrl}")
        assertNotNull(first.hits)
        assertNotNull(first.publicationTs)
        assertNotNull(first.author?.avatarUrl)
    }

    @Test
    fun testAuthorVideosFilteringVk() = runBlocking {
        val testAuthor = "Kuplinov"
        val videos = VkSearchEngine.getAuthorVideos(testAuthor)
        println("VK Author '$testAuthor' videos: ${videos.size}")
        assertTrue("VK Author search should return results", videos.isNotEmpty())
        videos.take(5).forEach {
            println("VK item: [${it.author?.name}] ${it.title}")
            val match = it.author?.name?.contains(testAuthor, ignoreCase = true) == true ||
                    testAuthor.contains(it.author?.name ?: "", ignoreCase = true)
            assertTrue("Video should match requested author", match)
        }
    }

    @Test
    fun testAuthorVideosFilteringOk() = runBlocking {
        val testAuthor = "Фильмы"
        val videos = OkSearchEngine.getAuthorVideos(testAuthor)
        println("OK Author '$testAuthor' videos: ${videos.size}")
        assertTrue("OK Author search should return results", videos.isNotEmpty())
        videos.take(5).forEach {
            println("OK item: [${it.author?.name}] ${it.title} | Avatar: ${it.author?.avatarUrl}")
            val match = it.author?.name?.contains(testAuthor, ignoreCase = true) == true ||
                    testAuthor.contains(it.author?.name ?: "", ignoreCase = true)
            assertTrue("Video should match requested author", match)
        }
    }
}
