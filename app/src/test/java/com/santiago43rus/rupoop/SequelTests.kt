package com.santiago43rus.rupoop

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

fun parseRomanOrInt(str: String): Int? {
    str.toIntOrNull()?.let { return it }
    val roman = str.uppercase(Locale.getDefault())
    if (roman.isEmpty() || !roman.matches(Regex("^[IVXLCDM]+$"))) return null
    var result = 0
    var i = 0
    val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    while (i < roman.length) {
        val s1 = map[roman[i]] ?: return null
        if (i + 1 < roman.length) {
            val s2 = map[roman[i + 1]]
            if (s2 != null && s1 < s2) {
                result += s2 - s1
                i += 2
                continue
            }
        }
        result += s1
        i += 1
    }
    return if (result > 0) result else null
}

data class SequenceInfo(
    val baseName: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeStart: Int? = null,
    val episodeEnd: Int? = null,
    val part: Int? = null,
    val year: Int? = null
)

class SequelTests {
    fun parseSequenceInfo(title: String): SequenceInfo {
        var base = title

        // Remove quotes like «...»
        base = base.replace(Regex("«[^»]*»?"), "")

        // Year
        val yearRegex = Regex("\\((\\d{4})\\)")
        var year: Int? = null
        yearRegex.find(base)?.let { match ->
            year = match.groupValues[1].toIntOrNull()
        }

        // Remove stuff in brackets
        val bracketRegex = Regex("\\[.*?\\]|\\(.*?\\)")
        base = base.replace(bracketRegex, "")

        // Season and Episode multiple formats
        var season: Int? = null
        var episode: Int? = null
        var episodeStart: Int? = null
        var episodeEnd: Int? = null

        // Formats:
        val numPattern = "\\d+|[IVXLCDM]+"

        // Format: с01э05
        val seRegex1 = Regex("с(\\d{1,2})э(\\d{1,2})(?:\\s*-\\s*(\\d{1,2}))?", RegexOption.IGNORE_CASE)
        // Format: 1 сезон 5-6 серии
        val seRegex2 = Regex("($numPattern)\\s*-?я?\\s*(?:сезон|season).*?($numPattern)(?:\\s*-\\s*($numPattern))?\\s*-?я?\\s*(?:серия|сериая|серии|эпизод|episode|ep)", RegexOption.IGNORE_CASE)
        // Format: сезон 1 серия 5-6
        val seRegex3 = Regex("(?:сезон|season|s)\\s*($numPattern).*?(?:серия|сериая|серии|эпизод|episode|ep|e)\\s*($numPattern)(?:\\s*-\\s*($numPattern))?", RegexOption.IGNORE_CASE)
        // Format: 5-6 серии (without season)
        val eRegexOnly = Regex("($numPattern)(?:\\s*-\\s*($numPattern))?\\s*-?я?\\s*(?:серия|сериая|серии|эпизод|episode|ep|выпуск)", RegexOption.IGNORE_CASE)
        val eRegexOnly2 = Regex("(?:серия|сериая|серии|эпизод|episode|ep|выпуск|e)\\s*($numPattern)(?:\\s*-\\s*($numPattern))?", RegexOption.IGNORE_CASE)
        // Format: s01e05 or s1e5
        val seRegexUniversal = Regex("s(\\d{1,2})\\s*e(\\d{1,3})(?:\\s*-\\s*(\\d{1,3}))?", RegexOption.IGNORE_CASE)
        // Format: 01x05
        val seRegexXFormat = Regex("(\\d{1,2})x(\\d{1,3})(?:\\s*-\\s*(\\d{1,3}))?", RegexOption.IGNORE_CASE)

        when {
            seRegex1.containsMatchIn(base) -> {
                val match = seRegex1.find(base)!!
                season = match.groupValues[1].toIntOrNull()
                episodeStart = match.groupValues[2].toIntOrNull()
                episodeEnd = match.groupValues[3].toIntOrNull() ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            seRegexUniversal.containsMatchIn(base) -> {
                val match = seRegexUniversal.find(base)!!
                season = match.groupValues[1].toIntOrNull()
                episodeStart = match.groupValues[2].toIntOrNull()
                episodeEnd = match.groupValues[3].toIntOrNull() ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            seRegexXFormat.containsMatchIn(base) -> {
                val match = seRegexXFormat.find(base)!!
                season = match.groupValues[1].toIntOrNull()
                episodeStart = match.groupValues[2].toIntOrNull()
                episodeEnd = match.groupValues[3].toIntOrNull() ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            seRegex2.containsMatchIn(base) -> {
                val match = seRegex2.find(base)!!
                season = parseRomanOrInt(match.groupValues[1])
                episodeStart = parseRomanOrInt(match.groupValues[2])
                episodeEnd = parseRomanOrInt(match.groupValues[3]) ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            seRegex3.containsMatchIn(base) -> {
                val match = seRegex3.find(base)!!
                season = parseRomanOrInt(match.groupValues[1])
                episodeStart = parseRomanOrInt(match.groupValues[2])
                episodeEnd = parseRomanOrInt(match.groupValues[3]) ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            eRegexOnly.containsMatchIn(base) -> {
                val match = eRegexOnly.find(base)!!
                episodeStart = parseRomanOrInt(match.groupValues[1])
                episodeEnd = parseRomanOrInt(match.groupValues[2]) ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
            eRegexOnly2.containsMatchIn(base) -> {
                val match = eRegexOnly2.find(base)!!
                episodeStart = parseRomanOrInt(match.groupValues[1])
                episodeEnd = parseRomanOrInt(match.groupValues[2]) ?: episodeStart
                episode = episodeStart
                base = base.replace(match.value, "")
            }
        }

        var part: Int? = null
        val pRegex1 = Regex("(?:часть|part|ч|p|фильм)\\s*($numPattern)\\b", RegexOption.IGNORE_CASE)
        val pRegex2 = Regex("($numPattern)\\s*-?я?\\s*(?:часть|part)", RegexOption.IGNORE_CASE)
        val pRegex3 = Regex("\\s+(\\d+|(?![iI]\\b)[IVXLCDM]+)\\s*$")

        if (season == null && episode == null) {
            when {
                pRegex1.containsMatchIn(base) -> {
                    val match = pRegex1.find(base)!!
                    part = parseRomanOrInt(match.groupValues[1])
                    base = base.replace(match.value, "")
                }
                pRegex2.containsMatchIn(base) -> {
                    val match = pRegex2.find(base)!!
                    part = parseRomanOrInt(match.groupValues[1])
                    base = base.replace(match.value, "")
                }
                pRegex3.containsMatchIn(base) -> {
                    val match = pRegex3.find(base)!!
                    part = parseRomanOrInt(match.groupValues[1])
                    base = base.replace(match.value, "")
                }
            }
        }

        // Split by slash and keep first part AFTER removing season/episode info
        val splitBySlash = base.split("/")
        if (splitBySlash.size > 1) {
            base = splitBySlash[0]
        }

        // Cut off subtitles to make base names match for series with subtitles (e.g., Star Wars, Minions)
        val colonIndex = base.indexOf(':')
        if (colonIndex != -1) base = base.substring(0, colonIndex)
        val emDashIndex = base.indexOf('—')
        if (emDashIndex != -1) base = base.substring(0, emDashIndex)

        base = base.replace(Regex("сериал|мультфильм|фильм|серия", RegexOption.IGNORE_CASE), "")
        base = base.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "").lowercase(Locale.getDefault())

        return SequenceInfo(base, season, episode, episodeStart, episodeEnd, part, year)
    }

    private fun isStrictSequel(current: SequenceInfo, candidate: SequenceInfo): Boolean {
        if (current.baseName != candidate.baseName) return false

        // Episodic
        if (current.episodeStart != null || candidate.episodeStart != null) {
            val cSeason = current.season ?: 1
            val candSeason = candidate.season ?: 1

            if (cSeason == candSeason) {
                val cEpEnd = current.episodeEnd ?: current.episodeStart ?: 1
                val candEpStart = candidate.episodeStart ?: candidate.episode ?: 1
                return candEpStart == cEpEnd + 1
            } else if (candSeason == cSeason + 1) {
                val candEpStart = candidate.episodeStart ?: candidate.episode ?: 1
                return candEpStart == 1
            }
            return false
        }

        // Parts / Movies - prioritize part if available
        if (current.part != null || candidate.part != null) {
            val cPart = current.part ?: 1
            val candPart = candidate.part ?: 1
            return candPart == cPart + 1
        }

        // Fallback to year if no parts or episodes are specified
        if (current.year != null && candidate.year != null) {
            return candidate.year > current.year
        }

        return false
    }

    @Test
    fun testRegexes() {
        val cases = listOf(
            "Гадкий я (2010)",
            "Гадкий я 2 (2013) / Despicable Me",
            "История игрушек (1995) / Toy Story",
            "История игрушек 2 (1999) / Toy Story 2",
            "С приветом по планетам - 1 сезон 1 серия «Покоритель / Яй...",
            "С приветом по планетам 2 сезон 4 серия «Тут-и-Тамы / Уволен» (му...",
            "Сериал Пацаны - 5 сезон 4 серия / The Boys Dragon Money Studio",
            "Звёздные войны: Эпизод 2 — Атака клонов (2002) / Star Wars: ...",
            "Звёздные войны: Эпизод 4 — Новая надежда (1977) / Star Wars: ...",
            "Миньоны (2015) / Minions",
            "Миньоны: Грювитация (2022) / Minions: The Rise of Gru",
            "Звёздные войны: Эпизод I — Скрытая угроза",
            "Звёздные войны: Эпизод VI",
            "Рокки IV",
            "Путь II часть",
            "часть VII",
            "Одна из многих / Pluribus 1 сезон 4 серия LE-Production"
        )
        for (c in cases) {
            val res = parseSequenceInfo(c)
            println("${c} -> ${res}")
        }

        assertEquals(1, parseSequenceInfo("Звёздные войны: Эпизод I").episode)
        assertEquals(6, parseSequenceInfo("Звёздные войны: Эпизод VI").episode)
        assertEquals(4, parseSequenceInfo("Рокки IV").part)
        assertEquals(2, parseSequenceInfo("Путь II часть").part)

        val pluribus = parseSequenceInfo("Одна из многих / Pluribus 1 сезон 4 серия LE-Production")
        assertEquals(1, pluribus.season)
        assertEquals(4, pluribus.episode)
    }

    @Test
    fun testEpisodeRanges() {
        val current = parseSequenceInfo("Удивительный мир Гамбола 1 сезон 1-2 серии")
        val candidate = parseSequenceInfo("Удивительный мир Гамбола 1 сезон 3-5 серии")
        val candidate2 = parseSequenceInfo("Удивительный мир Гамбола 1 сезон 3 серия")

        assertEquals(1, current.season)
        assertEquals(1, current.episodeStart)
        assertEquals(2, current.episodeEnd)

        assertEquals(1, candidate.season)
        assertEquals(3, candidate.episodeStart)
        assertEquals(5, candidate.episodeEnd)

        // Assert isStrictSequel
        assertTrue(isStrictSequel(current, candidate))
        assertTrue(isStrictSequel(current, candidate2))

        val currentTypo = parseSequenceInfo("Удивительный мир Гамбола 1 сезон сериая 1-2")
        val candidateTypo = parseSequenceInfo("Удивительный мир Гамбола 1 сезон сериая 3-5")
        assertTrue(isStrictSequel(currentTypo, candidateTypo))
    }
}
