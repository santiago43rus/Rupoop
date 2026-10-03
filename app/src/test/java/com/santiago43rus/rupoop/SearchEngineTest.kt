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
    fun testLiveLordfilmSearchMovies() = runBlocking {
        val results = LordfilmSearchEngine.search("интерстеллар")
        println("Lordfilm movies results: ${results.size}")
        results.take(3).forEach { println("Lordfilm Movie: ${it.title} -> ${it.videoUrl}") }
        assertTrue("Lordfilm movie search should return items", results.isNotEmpty())
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
}
