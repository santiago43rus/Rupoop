package com.santiago43rus.rupoop

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
        assertFalse(UniversalVideoParser.isHttpUrl("аниме приколы 2024"))
    }

    @Test
    fun testIsRutubeUrl() {
        assertTrue(UniversalVideoParser.isRutubeUrl("https://rutube.ru/video/abcdef123456/"))
        assertFalse(UniversalVideoParser.isRutubeUrl("https://vk.ru/video-123_456"))
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
    }

    @Test
    fun testDirectMediaUrl() {
        assertTrue(UniversalVideoParser.isDirectMediaUrl("https://example.com/live/stream.m3u8"))
        assertTrue(UniversalVideoParser.isDirectMediaUrl("https://example.com/video.mp4?token=abc"))
        assertFalse(UniversalVideoParser.isDirectMediaUrl("https://vk.ru/video-123_456"))
    }
}
