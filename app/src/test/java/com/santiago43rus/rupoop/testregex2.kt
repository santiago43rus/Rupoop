import java.util.regex.Pattern

data class VideoParsed(val base: String, val season: Int?, val episode: Int?, val part: Int?, val year: Int?)

fun parseTitle(title: String): VideoParsed {
    val sRegex = Regex("(?:сезон|season|s)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val eRegex = Regex("(?:серия|эпизод|выпуск|episode|ep|e|сер|вып)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val pRegex = Regex("(?:часть|part|ч|p|фильм)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val yRegex = Regex("(?:^|\\s|\\()(19\\d{2}|20\\d{2})(?:\\s|$|\\))")

    val sMatch = sRegex.find(title)
    val eMatch = eRegex.find(title)
    val pMatch = pRegex.find(title)
    val yMatch = yRegex.find(title)

    val season = sMatch?.groupValues?.get(1)?.toIntOrNull()
    val episode = eMatch?.groupValues?.get(1)?.toIntOrNull()
    var part = pMatch?.groupValues?.get(1)?.toIntOrNull()
    val year = yMatch?.groupValues?.get(1)?.toIntOrNull()

    var base = title.replace(Regex("(?ui)(?s)(?:(?:^|\\s)(?:3[dд]|4[kк]|2[dд]|1080p?|720p?|480p?|2160p?)(?:\\s|$))"), " ")
    listOfNotNull(sMatch, eMatch, pMatch, yMatch).forEach { match ->
        base = base.replace(match.value, " ", ignoreCase = true)
    }

    base = base.replace(Regex("[^a-zA-Zа-яА-Я0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    if (part == null && season == null && episode == null) {
        val numRegex = Regex("(?:^|\\s)(\\d{1,2})(?:\\s|$)")
        val numMatch = numRegex.findAll(base).lastOrNull()
        if (numMatch != null) {
            part = numMatch.groupValues[1].toIntOrNull()
            base = base.replaceRange(numMatch.range, " ").replace(Regex("\\s+"), " ").trim()
        }
    }

    return VideoParsed(base, season, episode, part, year)
}

fun main() {
    val t1 = "История игрушек 2"
    val t2 = "История игрушек 3"
    val t3 = "История игрушек 4"
    val t4 = "Гадкий я (2010)"
    val t5 = "Гадкий я 2"
    val t6 = "Гадкий я 3"
    val t7 = "Гадкий я 4"

    println("$t1 -> ${parseTitle(t1)}")
    println("$t2 -> ${parseTitle(t2)}")
    println("$t3 -> ${parseTitle(t3)}")
    println("$t4 -> ${parseTitle(t4)}")
    println("$t5 -> ${parseTitle(t5)}")
    println("$t6 -> ${parseTitle(t6)}")
    println("$t7 -> ${parseTitle(t7)}")
}

