package com.santiago43rus.rupoop

import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.RelatedVideoRecommendationStrategy
import com.santiago43rus.rupoop.data.SearchResult
import org.junit.Assert.*
import org.junit.Test

class SequelTests {

    private val strategy = RelatedVideoRecommendationStrategy()

    @Test
    fun testParseSequenceInfoCases() {
        assertEquals(1, strategy.parseSequenceInfo("Звёздные войны: Эпизод I").episode)
        assertEquals(6, strategy.parseSequenceInfo("Звёздные войны: Эпизод VI").episode)
        assertEquals(4, strategy.parseSequenceInfo("Рокки IV").part)
        assertEquals(2, strategy.parseSequenceInfo("Путь II часть").part)

        val pluribus = strategy.parseSequenceInfo("Одна из многих / Pluribus 1 сезон 4 серия LE-Production")
        assertEquals(1, pluribus.season)
        assertEquals(4, pluribus.episode)

        val boys = strategy.parseSequenceInfo("Пацаны 4 сезон 2 серия / The Boys")
        assertEquals(4, boys.season)
        assertEquals(2, boys.episode)

        val breakingBad = strategy.parseSequenceInfo("Во все тяжкие s05e14")
        assertEquals(5, breakingBad.season)
        assertEquals(14, breakingBad.episode)
    }

    @Test
    fun testRutubeNextEpisodeSequel() {
        val current = SearchResult(
            videoUrl = "https://rutube.ru/video/111/",
            title = "Универ. 13 лет спустя 1 сезон 5 серия",
            author = Author(name = "ТНТ")
        )
        val candidates = listOf(
            SearchResult(videoUrl = "https://rutube.ru/video/222/", title = "Универ. 13 лет спустя 1 сезон 7 серия", author = Author(name = "ТНТ")),
            SearchResult(videoUrl = "https://rutube.ru/video/333/", title = "Универ. 13 лет спустя 1 сезон 6 серия", author = Author(name = "ТНТ")),
            SearchResult(videoUrl = "https://rutube.ru/video/444/", title = "Интерны 2 сезон 1 серия", author = Author(name = "ТНТ"))
        )

        val recommended = strategy.recommendRelated(current, candidates)
        assertFalse(recommended.isEmpty())
        // Next episode (6) MUST be at index 0
        assertEquals("https://rutube.ru/video/333/", recommended.first().videoUrl)
        assertEquals("Универ. 13 лет спустя 1 сезон 6 серия", recommended.first().title)
    }

    @Test
    fun testVkNextEpisodeWithDifferentUploaders() {
        val current = SearchResult(
            videoUrl = "https://vk.com/video-123_456",
            title = "Пацаны 4 сезон 1 серия [Кубик в Кубе]",
            author = Author(name = "SerialHub")
        )
        val candidates = listOf(
            SearchResult(videoUrl = "https://vk.com/video-999_100", title = "Случайный ролик про кино", author = Author(name = "MovieReview")),
            SearchResult(videoUrl = "https://vk.com/video-888_200", title = "Пацаны 4 сезон 2 серия [Кубик в Кубе]", author = Author(name = "AnotherUploader")),
            SearchResult(videoUrl = "https://vk.com/video-777_300", title = "Пацаны 4 сезон 4 серия", author = Author(name = "CinemaWorld"))
        )

        val recommended = strategy.recommendRelated(current, candidates)
        assertFalse(recommended.isEmpty())
        // Next episode (2) MUST be at index 0 even if uploaded by different author on VK!
        assertEquals("https://vk.com/video-888_200", recommended.first().videoUrl)
        assertEquals("Пацаны 4 сезон 2 серия [Кубик в Кубе]", recommended.first().title)
    }

    @Test
    fun testOkNextEpisodeSeries() {
        val current = SearchResult(
            videoUrl = "https://ok.ru/video/12345678",
            title = "Сваты 7 сезон 3 серия",
            author = Author(name = "Кино ОК")
        )
        val candidates = listOf(
            SearchResult(videoUrl = "https://ok.ru/video/888888", title = "Сваты 7 сезон 5 серия", author = Author(name = "Кино ОК")),
            SearchResult(videoUrl = "https://ok.ru/video/999999", title = "Сваты 7 сезон 4 серия", author = Author(name = "Пользователь 123")),
            SearchResult(videoUrl = "https://ok.ru/video/777777", title = "Кухня 1 сезон 1 серия", author = Author(name = "Сериалы"))
        )

        val recommended = strategy.recommendRelated(current, candidates)
        assertFalse(recommended.isEmpty())
        // Next episode (4) MUST be first
        assertEquals("https://ok.ru/video/999999", recommended.first().videoUrl)
        assertEquals("Сваты 7 сезон 4 серия", recommended.first().title)
    }

    @Test
    fun testLordfilmMovieSequel() {
        val current = SearchResult(
            videoUrl = "https://lordfilm.top/123-dune-2021.html",
            title = "Дюна (2021)",
            author = Author(name = "Lordfilm")
        )
        val candidates = listOf(
            SearchResult(videoUrl = "https://lordfilm.top/456-other-film.html", title = "Бегущий по лезвию 2049", author = Author(name = "Lordfilm")),
            SearchResult(videoUrl = "https://lordfilm.top/789-dune-part-2-2024.html", title = "Дюна: Часть вторая (2024)", author = Author(name = "Lordfilm")),
            SearchResult(videoUrl = "https://lordfilm.top/111-dune-1984.html", title = "Дюна (1984)", author = Author(name = "Lordfilm"))
        )

        val recommended = strategy.recommendRelated(current, candidates)
        assertFalse(recommended.isEmpty())
        // Sequel (Part 2) MUST be first
        assertEquals("https://lordfilm.top/789-dune-part-2-2024.html", recommended.first().videoUrl)
        assertEquals("Дюна: Часть вторая (2024)", recommended.first().title)
    }

    @Test
    fun testNextSeasonFirstEpisode() {
        val current = SearchResult(
            videoUrl = "https://rutube.ru/video/s1e8/",
            title = "Рик и Морти 1 сезон 8 серия",
            author = null
        )
        val candidates = listOf(
            SearchResult(videoUrl = "https://rutube.ru/video/s2e1/", title = "Рик и Морти 2 сезон 1 серия", author = null),
            SearchResult(videoUrl = "https://rutube.ru/video/s3e5/", title = "Рик и Морти 3 сезон 5 серия", author = null)
        )

        val recommended = strategy.recommendRelated(current, candidates)
        assertFalse(recommended.isEmpty())
        assertEquals("https://rutube.ru/video/s2e1/", recommended.first().videoUrl)
    }
}
