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
    fun testJsPackerUnpacker() {
        val packed = """eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('0 2="1";',3,3,'var|hello|world'.split('|'),0,{}))"""
        val unpacked = JsPackerUnpacker.unpack(packed)
        assertTrue("Unpacked code should contain variables", unpacked.contains("var world=\"hello\""))
    }

    @Test
    fun testMockLordfilmExtraction() {
        val mockHtmlWithIframe = """
            <!DOCTYPE html>
            <html>
            <head><title>Интерстеллар (2014) смотреть онлайн</title></head>
            <body>
                <meta property="og:title" content="Интерстеллар (2014)">
                <meta property="og:image" content="https://example.com/poster.jpg">
                <div class="player">
                    <iframe src="https://cdnvideohub.org/embed/12345" width="100%" height="100%"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mockPlayerHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Player</title></head>
            <body>
                <script>
                    var file = "https://cdn.example.com/hls/interstellar/index.m3u8";
                    var player = new Playerjs({file: file});
                </script>
            </body>
            </html>
        """.trimIndent()

        val iframeMatcher = java.util.regex.Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(mockHtmlWithIframe)
        assertTrue(iframeMatcher.find())
        val iframeUrl = iframeMatcher.group(1)
        assertEquals("https://cdnvideohub.org/embed/12345", iframeUrl)

        val streamMatcher = java.util.regex.Pattern.compile("""(?:file|hls|src|url)\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']""", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(mockPlayerHtml)
        assertTrue(streamMatcher.find())
        val streamUrl = streamMatcher.group(1)
        assertEquals("https://cdn.example.com/hls/interstellar/index.m3u8", streamUrl)
    }

    @Test
    fun testMockSerialEpisodesExtraction() {
        val mockPlayerHtmlWithEpisodes = """
            <script>
            var playlist = [
                {"title": "1 серия (1 сезон)", "file": "https://cdn.example.com/s1e1.m3u8"},
                {"title": "2 серия (1 сезон)", "file": "https://cdn.example.com/s1e2.m3u8"},
                {"title": "3 серия (1 сезон)", "file": "https://cdn.example.com/s1e3.m3u8"}
            ];
            </script>
        """.trimIndent()

        val pattern = java.util.regex.Pattern.compile(
            """\{[^}]*?["']title["']\s*:\s*["']([^"']+)["'][^}]*?["'](?:file|hls|url|stream|src)["']\s*:\s*["']([^"']+\.m3u8[^"']*)["'][^}]*\}""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(mockPlayerHtmlWithEpisodes)
        val episodes = mutableListOf<Pair<String, String>>()
        while (matcher.find()) {
            val title = matcher.group(1) ?: continue
            val file = matcher.group(2) ?: continue
            episodes.add(title to file)
        }

        assertEquals(3, episodes.size)
        assertEquals("1 серия (1 сезон)", episodes[0].first)
        assertEquals("https://cdn.example.com/s1e1.m3u8", episodes[0].second)
        assertEquals("2 серия (1 сезон)", episodes[1].first)
        assertEquals("https://cdn.example.com/s1e2.m3u8", episodes[1].second)
    }
}