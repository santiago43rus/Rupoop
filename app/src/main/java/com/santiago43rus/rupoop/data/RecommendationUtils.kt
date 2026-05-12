package com.santiago43rus.rupoop.data

enum class SequelStatus { NEXT_EPISODE, NEXT_SEASON_FIRST_EP, SAME_SEASON_FUTURE, SAME_SERIES_OTHER, PAST_EPISODE, NONE }

data class VideoParsed(val base: String, val season: Int?, val episode: Int?, val part: Int?)

object RecommendationUtils {

    fun isTooSimilar(s1: String, s2: String): Boolean {
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

    fun parseTitle(title: String): VideoParsed {
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

    fun getSequelStatus(candidate: SearchResult, original: SearchResult): SequelStatus {
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

    fun isMovie(video: SearchResult): Boolean {
        return (video.duration ?: 0) > 3600 || video.title.contains("фильм", ignoreCase = true)
    }

    fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        var score = 0f
        val effectiveTags = video.tags?.toMutableList() ?: mutableListOf()
        val filmGenres = listOf("боевик", "комедия", "драма", "ужасы", "фантастика", "триллер", "мелодрама", "детектив", "приключения", "криминал", "семейный", "военный", "история", "биография", "фэнтези", "вестерн")

        if (effectiveTags.any { tag -> filmGenres.any { genre -> tag.contains(genre, ignoreCase = true) } }) {
            effectiveTags.add("фильм")
        }

        effectiveTags.forEach { tag -> score += registry.tagWeights[tag] ?: 0f }
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

