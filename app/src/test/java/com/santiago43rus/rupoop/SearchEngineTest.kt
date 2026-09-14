package com.santiago43rus.rupoop

import com.santiago43rus.rupoop.data.SearchSource
import com.santiago43rus.rupoop.network.LordfilmSearchEngine
import com.santiago43rus.rupoop.network.OkSearchEngine
import com.santiago43rus.rupoop.network.VkSearchEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

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
    fun testLiveLordfilmSearchSeries() = runBlocking {
        val results = LordfilmSearchEngine.search("ведьмак")
        println("Lordfilm series results: ${results.size}")
        results.take(3).forEach { println("Lordfilm Series: ${it.title} -> ${it.videoUrl}") }
        assertTrue("Lordfilm series search should return items", results.isNotEmpty())
    }
}
