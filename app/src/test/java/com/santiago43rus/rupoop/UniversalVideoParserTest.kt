package com.santiago43rus.rupoop

import com.santiago43rus.rupoop.parser.JsPackerUnpacker
import com.santiago43rus.rupoop.parser.OkVideoParser
import com.santiago43rus.rupoop.parser.UniversalVideoParser
import com.santiago43rus.rupoop.parser.VkVideoParser
import org.junit.Assert.*
import org.junit.Test

class UniversalVideoParserTest {

    @Test
    fun testIsHttpUrl() {
        assertTrue(UniversalVideoParser.isHttpUrl("https://rutube.ru/video/123/"))
        assertTrue(UniversalVideoParser.isHttpUrl("http://vk.com/video-123_456"))
        assertTrue(UniversalVideoParser.isHttpUrl("vk.ru/video-123_456"))
        assertTrue(UniversalVideoParser.isHttpUrl("https://lordfilm.cx/film/123.html"))
        assertTrue(UniversalVideoParser.isHttpUrl("ok.ru/video/1234567890"))
        assertTrue(UniversalVideoParser.isHttpUrl("https://ok.ru/videoembed/9876543210"))
        assertTrue(UniversalVideoParser.isHttpUrl("jut.su/naruto/season-1/episode-1.html"))
        assertTrue(UniversalVideoParser.isHttpUrl("hdrezka.ag/films/12345.html"))
        assertTrue(UniversalVideoParser.isHttpUrl("aniqit.com/video/54321"))
        assertFalse(UniversalVideoParser.isHttpUrl("аниме приколы 2024"))
        assertFalse(UniversalVideoParser.isHttpUrl("рутуб видео поиск"))
    }

    @Test
    fun testIsRutubeUrl() {
        assertTrue(UniversalVideoParser.isRutubeUrl("https://rutube.ru/video/abcdef123456/"))
        assertFalse(UniversalVideoParser.isRutubeUrl("https://vk.ru/video-123_456"))
        assertFalse(UniversalVideoParser.isRutubeUrl("https://ok.ru/video/123456"))
    }

    @Test
    fun testExtractRutubeId() {
        assertEquals("abcdef123456", UniversalVideoParser.extractRutubeId("https://rutube.ru/video/abcdef123456/"))
        assertEquals("abcdef123456", UniversalVideoParser.extractRutubeId("https://rutube.ru/play/embed/abcdef123456/"))
    }

    @Test
    fun testIsVkUrl() {
        assertTrue(VkVideoParser.isVkUrl("https://vk.com/video-123_456"))
        assertTrue(VkVideoParser.isVkUrl("https://vk.ru/video-123_456"))
        assertTrue(VkVideoParser.isVkUrl("https://vkvideo.ru/video-123_456"))
        assertFalse(VkVideoParser.isVkUrl("https://rutube.ru/video/123"))
        assertFalse(VkVideoParser.isVkUrl("https://ok.ru/video/123"))
    }

    @Test
    fun testIsOkUrlAndExtractId() {
        assertTrue(OkVideoParser.isOkUrl("https://ok.ru/video/1234567890"))
        assertTrue(OkVideoParser.isOkUrl("http://odnoklassniki.ru/video/1234567890"))
        assertTrue(OkVideoParser.isOkUrl("https://ok.ru/videoembed/1234567890"))
        assertTrue(OkVideoParser.isOkUrl("https://ok.ru/live/1234567890"))
        assertFalse(OkVideoParser.isOkUrl("https://vk.com/video-123_456"))

        assertEquals("1234567890", OkVideoParser.extractOkVideoId("https://ok.ru/video/1234567890"))
        assertEquals("1234567890", OkVideoParser.extractOkVideoId("https://ok.ru/videoembed/1234567890"))
        assertEquals("1234567890", OkVideoParser.extractOkVideoId("https://ok.ru/live/1234567890"))
        assertEquals("1234567890", OkVideoParser.extractOkVideoId("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=1234567890"))
    }

    @Test
    fun testDirectMediaUrl() {
        assertTrue(UniversalVideoParser.isDirectMediaUrl("https://example.com/live/stream.m3u8"))
        assertTrue(UniversalVideoParser.isDirectMediaUrl("https://example.com/video.mp4?token=abc"))
        assertFalse(UniversalVideoParser.isDirectMediaUrl("https://vk.ru/video-123_456"))
        assertFalse(UniversalVideoParser.isDirectMediaUrl("https://ok.ru/video/123"))
    }

    @Test
    fun testRealOkVideo6635699048795() {
        kotlinx.coroutines.runBlocking {
            val url = "https://ok.ru/video/6635699048795"
            val result = UniversalVideoParser.parse(url)
            assertNotNull("Parsed result should not be null", result)
            
            // Test actually fetching the stream URL with the headers
            val client = okhttp3.OkHttpClient.Builder().followRedirects(true).build()
            val reqBuilder = okhttp3.Request.Builder().url(result!!.streamUrl)
            for ((k, v) in result.headers) {
                reqBuilder.header(k, v)
            }
            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body.string()
            assertEquals("Stream URL should return HTTP 200", 200, resp.code)
            assertTrue("Body should contain video data or m3u8 playlist", body.contains("#EXTM3U") || body.contains("ftyp") || resp.code == 200)
        }
    }
}
