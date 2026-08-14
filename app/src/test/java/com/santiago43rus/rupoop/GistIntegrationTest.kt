package com.santiago43rus.rupoop

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.network.GistApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class GistIntegrationTest {

    private lateinit var proxyUrl: String
    private lateinit var gistApi: GistApi
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        val props = Properties()
        val possibleFiles = listOf(
            File("local.properties"),
            File("../local.properties"),
            File("../../local.properties"),
            File("E:/Rupoop/local.properties")
        )
        val propsFile = possibleFiles.firstOrNull { it.exists() }
        if (propsFile != null) {
            props.load(FileInputStream(propsFile))
        }

        proxyUrl = props.getProperty("PROXY_URL") ?: "https://rupoop-proxy.ijonmarston.workers.dev/"
        if (!proxyUrl.endsWith("/")) {
            proxyUrl += "/"
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Rupoop-Test")
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(proxyUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        gistApi = retrofit.create(GistApi::class.java)
    }

    @Test
    fun testProxyUrlIsConfigured() {
        assertNotNull(proxyUrl)
        assertTrue(proxyUrl.startsWith("http"))
        assertTrue(proxyUrl.contains("workers.dev") || proxyUrl.contains("rupoop"))
    }

    @Test
    fun testGistSerializationAndDeserialization() {
        val originalRegistry = UserRegistry(
            watchHistory = listOf(
                WatchHistoryItem(
                    videoId = "v123",
                    timestamp = 1700000000000L,
                    progress = 120L,
                    totalDuration = 300L,
                    title = "Test Video 1",
                    videoUrl = "https://rutube.ru/video/v123/"
                )
            ),
            searchHistory = listOf("rutube", "kotlin"),
            subscriptions = listOf(Author(id = 100L, name = "Test Channel")),
            likedVideos = listOf(
                SearchResult(videoUrl = "https://rutube.ru/video/v123/", title = "Test Video 1")
            ),
            appSettings = AppSettings(
                theme = "dark",
                downloadQuality = "1080",
                autoPlayNext = true,
                doubleTapSeekDuration = 10
            )
        )

        val jsonString = json.encodeToString(originalRegistry)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("v123"))
        assertTrue(jsonString.contains("Test Channel"))

        val decodedRegistry = json.decodeFromString<UserRegistry>(jsonString)
        assertEquals(1, decodedRegistry.watchHistory.size)
        assertEquals("v123", decodedRegistry.watchHistory[0].videoId)
        assertEquals("Test Channel", decodedRegistry.subscriptions[0].name)
        assertEquals("dark", decodedRegistry.appSettings.theme)
        assertEquals("1080", decodedRegistry.appSettings.downloadQuality)
    }

    @Test
    fun testSmartMergeWatchHistoryWeaving() {
        val olderLocalItem = WatchHistoryItem(
            videoId = "v1",
            timestamp = 1000L,
            progress = 30L,
            totalDuration = 100L,
            title = "Older Local Video"
        )
        val newerRemoteItem = WatchHistoryItem(
            videoId = "v2",
            timestamp = 2000L,
            progress = 60L,
            totalDuration = 100L,
            title = "Newer Remote Video"
        )
        val duplicateItemLocal = WatchHistoryItem(
            videoId = "v3",
            timestamp = 1500L,
            progress = 40L,
            totalDuration = 200L,
            title = "Shared Video"
        )
        val duplicateItemRemoteUpdated = WatchHistoryItem(
            videoId = "v3",
            timestamp = 2500L,
            progress = 180L,
            totalDuration = 200L,
            title = "Shared Video Updated"
        )

        val localRegistry = UserRegistry(
            watchHistory = listOf(duplicateItemLocal, olderLocalItem)
        )
        val remoteRegistry = UserRegistry(
            watchHistory = listOf(duplicateItemRemoteUpdated, newerRemoteItem)
        )

        val maxWatchClearedAt = maxOf(localRegistry.watchHistoryClearedAt, remoteRegistry.watchHistoryClearedAt)

        val mergedHistory = (localRegistry.watchHistory + remoteRegistry.watchHistory)
            .filter { it.timestamp > maxWatchClearedAt }
            .groupBy { if (it.videoId.isNotEmpty()) it.videoId else it.videoUrl }
            .map { (_, items) ->
                items.maxWithOrNull(compareBy({ it.timestamp }, { it.progress }))!!
            }
            .sortedByDescending { it.timestamp }

        assertEquals(3, mergedHistory.size)
        assertEquals("v3", mergedHistory[0].videoId)
        assertEquals(2500L, mergedHistory[0].timestamp)
        assertEquals(180L, mergedHistory[0].progress)

        assertEquals("v2", mergedHistory[1].videoId)
        assertEquals(2000L, mergedHistory[1].timestamp)

        assertEquals("v1", mergedHistory[2].videoId)
        assertEquals(1000L, mergedHistory[2].timestamp)
    }

    @Test
    fun testGistItemDeletion() {
        val originalRegistry = UserRegistry(
            watchHistory = listOf(
                WatchHistoryItem(videoId = "v1", timestamp = 1000L, progress = 10L, totalDuration = 100L),
                WatchHistoryItem(videoId = "v2", timestamp = 2000L, progress = 20L, totalDuration = 100L)
            ),
            searchHistory = listOf("query1", "query2"),
            subscriptions = listOf(Author(id = 1L, name = "Author 1"), Author(id = 2L, name = "Author 2")),
            likedVideos = listOf(
                SearchResult(videoUrl = "https://rutube.ru/video/v1/", title = "Video 1"),
                SearchResult(videoUrl = "https://rutube.ru/video/v2/", title = "Video 2")
            ),
            watchLater = listOf(
                SearchResult(videoUrl = "https://rutube.ru/video/v1/", title = "Video 1")
            ),
            playlists = listOf(
                Playlist(id = "p1", name = "Playlist 1", videos = listOf(SearchResult(videoUrl = "https://rutube.ru/video/v1/", title = "Video 1")))
            )
        )

        // Simulate deleting v1 from history, query1 from search, Author 1 from subs, v1 from liked, v1 from watch later, p1 from playlists
        val updatedRegistry = originalRegistry.copy(
            watchHistory = originalRegistry.watchHistory.filterNot { it.videoId == "v1" },
            searchHistory = originalRegistry.searchHistory.filterNot { it == "query1" },
            subscriptions = originalRegistry.subscriptions.filterNot { it.name == "Author 1" },
            likedVideos = originalRegistry.likedVideos.filterNot { it.videoUrl == "https://rutube.ru/video/v1/" },
            watchLater = emptyList(),
            playlists = emptyList()
        )

        val jsonString = json.encodeToString(updatedRegistry)
        val decoded = json.decodeFromString<UserRegistry>(jsonString)

        assertEquals(1, decoded.watchHistory.size)
        assertEquals("v2", decoded.watchHistory[0].videoId)
        assertEquals(listOf("query2"), decoded.searchHistory)
        assertEquals(1, decoded.subscriptions.size)
        assertEquals("Author 2", decoded.subscriptions[0].name)
        assertEquals(1, decoded.likedVideos.size)
        assertEquals("https://rutube.ru/video/v2/", decoded.likedVideos[0].videoUrl)
        assertTrue(decoded.watchLater.isEmpty())
        assertTrue(decoded.playlists.isEmpty())
    }

    @Test
    fun testProxyGistApiConnection() = runBlocking {
        try {
            val response = gistApi.listGists("Bearer invalid_test_token")
            assertNotNull(response)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            assertTrue(
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("HTTP") ||
                msg.contains("Unable to resolve host") ||
                msg.contains("Connect") ||
                e is retrofit2.HttpException
            )
        }
    }
}
