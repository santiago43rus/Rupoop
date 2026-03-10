package com.example.rupoop

class RecommendationEngine(private val registryManager: UserRegistryManager) {

    private val PERSONALIZATION_THRESHOLD = 3

    fun recommend(results: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val historySize = registry.watchHistory.size

        return results.filter { !it.title.contains("мультик", ignoreCase = true) }
            .filter { (it.duration ?: 0) >= 900 } // Minimum 15 minutes (900 seconds)
            .shuffled().sortedWith(compareByDescending<SearchResult> {
            calculateScore(it, registry)
        }.thenByDescending {
            if (historySize < PERSONALIZATION_THRESHOLD) isMovie(it) else false
        })
    }

    fun recommendRelated(video: SearchResult, pool: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val originalTitleParsed = parseTitle(video.title)

        return pool.filter { it.videoUrl != video.videoUrl }
            .filter { !it.title.contains("мультик", ignoreCase = true) }
            .filter { (it.duration ?: 0) >= 900 } // Minimum 15 minutes
            .filter { candidate ->
                val status = getSequelStatus(candidate, video)
                if (status == SequelStatus.NONE) {
                    val candidateParsed = parseTitle(candidate.title)
                    !isTooSimilar(candidateParsed.base, originalTitleParsed.base)
                } else {
                    true
                }
            }
            .sortedWith(compareByDescending<SearchResult> { candidate ->
                var score = 0f
                val sequelStatus = getSequelStatus(candidate, video)
                when (sequelStatus) {
                    SequelStatus.NEXT_EPISODE -> score += 50000.0f
                    SequelStatus.NEXT_SEASON_FIRST_EP -> score += 40000.0f
                    SequelStatus.SAME_SEASON_FUTURE -> score += 5000.0f
                    SequelStatus.SAME_SERIES_OTHER -> score += 1000.0f
                    SequelStatus.PAST_EPISODE -> score -= 50000.0f
                    SequelStatus.NONE -> {}
                }

                score += calculateScore(candidate, registry)
                if (candidate.author?.name == video.author?.name) score += 50.0f
                score
            })
    }

    private fun isTooSimilar(s1: String, s2: String): Boolean {
        if (s1.isEmpty() || s2.isEmpty()) return false
        val s1l = s1.lowercase()
        val s2l = s2.lowercase()
        if (s1l == s2l) return true
        if (s1l.contains(s2l) || s2l.contains(s1l)) {
             val ratio = s1.length.toFloat() / s2.length.toFloat()
             if (ratio > 0.7f && ratio < 1.4f) return true
        }
        return false
    }

    fun getSearchQueries(video: SearchResult): List<String> {
        val parsed = parseTitle(video.title)
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

    private enum class SequelStatus { NEXT_EPISODE, NEXT_SEASON_FIRST_EP, SAME_SEASON_FUTURE, SAME_SERIES_OTHER, PAST_EPISODE, NONE }

    data class VideoParsed(val base: String, val season: Int?, val episode: Int?, val part: Int?)

    private fun parseTitle(title: String): VideoParsed {
        val sRegex = Regex("(?:сезон|season|s)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val eRegex = Regex("(?:серия|эпизод|выпуск|episode|ep|e|сер|вып)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val pRegex = Regex("(?:часть|part|ч|p|фильм)\\s*(\\d+)", RegexOption.IGNORE_CASE)

        val sMatch = sRegex.find(title)
        val eMatch = eRegex.find(title)
        val pMatch = pRegex.find(title)

        val season = sMatch?.groupValues?.get(1)?.toIntOrNull()
        val episode = eMatch?.groupValues?.get(1)?.toIntOrNull()
        val part = pMatch?.groupValues?.get(1)?.toIntOrNull()

        var base = title
        listOfNotNull(sMatch, eMatch, pMatch).forEach { match ->
            base = base.replace(match.value, "", ignoreCase = true)
        }

        base = base.replace(Regex("[^a-zA-Zа-яА-Я0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

        return VideoParsed(base, season, episode, part)
    }

    private fun getSequelStatus(candidate: SearchResult, original: SearchResult): SequelStatus {
        val c = parseTitle(candidate.title)
        val o = parseTitle(original.title)

        if (c.base.length < 3 || o.base.length < 3 ||
            (!c.base.contains(o.base, true) && !o.base.contains(c.base, true))) {
            return SequelStatus.NONE
        }

        val origSeason = o.season ?: 1
        val candSeason = c.season ?: 1

        if (candSeason < origSeason) return SequelStatus.PAST_EPISODE

        if (candSeason == origSeason) {
            if (o.episode != null && c.episode != null) {
                if (c.episode == o.episode + 1) return SequelStatus.NEXT_EPISODE
                if (c.episode < o.episode) return SequelStatus.PAST_EPISODE
                if (c.episode > o.episode) return SequelStatus.SAME_SEASON_FUTURE
            }
            if (o.part != null && c.part != null) {
                if (c.part == o.part + 1) return SequelStatus.NEXT_EPISODE
                if (c.part < o.part) return SequelStatus.PAST_EPISODE
                if (c.part > o.part) return SequelStatus.SAME_SEASON_FUTURE
            }
            return SequelStatus.SAME_SERIES_OTHER
        } else if (candSeason == origSeason + 1) {
            if (c.episode == null || c.episode == 1) return SequelStatus.NEXT_SEASON_FIRST_EP
            return SequelStatus.SAME_SEASON_FUTURE
        }

        return SequelStatus.NONE
    }

    private fun isMovie(video: SearchResult): Boolean {
        return (video.duration ?: 0) > 3600 || video.title.contains("фильм", ignoreCase = true)
    }

    private fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        var score = 0f
        video.tags?.forEach { tag -> score += registry.tagWeights[tag] ?: 0f }
        if (registry.subscriptions.any { it.name == video.author?.name }) score += 15.0f

        val historyId = extractId(video.videoUrl)
        val historyItem = registry.watchHistory.find { historyId == it.videoId }
        if (historyItem != null) {
            val progressPercent = if (historyItem.totalDuration > 0) historyItem.progress.toFloat() / historyItem.totalDuration else 1f
            if (progressPercent > 0.9f) score -= 40.0f
            else if (progressPercent > 0.05f) score += 30.0f
        }
        return score
    }

    private fun extractId(url: String): String = url.split("/").lastOrNull { it.isNotEmpty() } ?: ""
}
