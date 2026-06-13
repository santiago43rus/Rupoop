package com.santiago43rus.rupoop.data

import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistryManager
import com.santiago43rus.rupoop.data.RecommendationUtils
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
    val season: Int?,
    val episode: Int?,
    val episodeStart: Int?,
    val episodeEnd: Int?,
    val part: Int?,
    val year: Int?
)

class RelatedVideoRecommendationStrategy(private val registryManager: UserRegistryManager) {

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

        // Part
        var part: Int? = null
        val pRegex1 = Regex("(?:часть|part|ч|p|фильм)\\s*($numPattern)\\b", RegexOption.IGNORE_CASE)
        val pRegex2 = Regex("($numPattern)\\s*-?я?\\s*(?:часть|part)", RegexOption.IGNORE_CASE)
        // Just number at the end
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

    fun recommendRelated(video: SearchResult, pool: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val currentInfo = parseSequenceInfo(video.title)
        
        var strictSequel: SearchResult? = null
        val others = mutableListOf<SearchResult>()
        
        for (candidate in pool) {
            if (candidate.videoUrl == video.videoUrl) continue
            if (candidate.title.contains("мультик", ignoreCase = true)) continue
            
            // Filter out verified channels (original Rutube content)
            if (candidate.author?.isVerifiedChannel == true) continue

            // Filter CAPS lock, banned words and genre plurals
            if (RecommendationUtils.hasCapsWord(candidate.title)) continue
            if (RecommendationUtils.isBannedTitle(candidate.title)) continue
            
            val candInfo = parseSequenceInfo(candidate.title)
            
            // Strict Sequel Logic
            if (candidate.author?.name == video.author?.name &&
                isStrictSequel(currentInfo, candInfo)) {
                // If we rely on year, pick the closest future year
                if (currentInfo.part == null && currentInfo.episodeStart == null && candInfo.part == null && candInfo.episodeStart == null && currentInfo.year != null && candInfo.year != null) {
                    if (strictSequel == null) {
                        strictSequel = candidate
                    } else {
                        val currentStrictInfo = parseSequenceInfo(strictSequel!!.title)
                        if (currentStrictInfo.year != null && candInfo.year < currentStrictInfo.year) {
                            others.add(strictSequel!!)
                            strictSequel = candidate
                        } else {
                            others.add(candidate)
                        }
                    }
                    continue
                } else if (strictSequel == null) {
                    strictSequel = candidate
                    continue
                }
            }
            
            others.add(candidate)
        }
        
        others.sortByDescending { candidate ->
            var score = RecommendationUtils.calculateScore(candidate, registry)
            if (candidate.author?.name == video.author?.name) score += 50.0f
            
            val cInfo = parseSequenceInfo(candidate.title)
            if (cInfo.baseName == currentInfo.baseName) {
                score += 200.0f // Give similar named ones a boost
            }
            
            score
        }
        
        val result = mutableListOf<SearchResult>()
        if (strictSequel != null) {
            result.add(strictSequel)
        }
        result.addAll(others)
        
        return result
    }

    fun getSearchQueries(video: SearchResult): List<String> {
        val parsed = RecommendationUtils.parseTitle(video.title)
        val queries = mutableListOf<String>()
        val base = parsed.base.take(50).trim()

        if (parsed.season != null && parsed.episode != null) {
            queries.add("$base сезон ${parsed.season} серия ${parsed.episode + 1}")
            queries.add("$base сезон ${parsed.season + 1} серия 1")
        } else if (parsed.episode != null) {
            queries.add("$base серия ${parsed.episode + 1}")
        } else if (parsed.part != null) {
            queries.add("$base часть ${parsed.part + 1}")
        }
        queries.add(base)
        return queries
    }
}
