package com.santiago43rus.rupoop.data

enum class SequelStatus { NEXT_EPISODE, NEXT_SEASON_FIRST_EP, SAME_SEASON_FUTURE, SAME_SERIES_OTHER, PAST_EPISODE, NONE }

data class VideoParsed(val base: String, val season: Int?, val episode: Int?, val part: Int?)

class SequelPredictor {

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
}
