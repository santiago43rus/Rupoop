package com.santiago43rus.rupoop.lan

import com.santiago43rus.rupoop.data.Playlist
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.WatchHistoryItem

/** Ключи элементов реестра. Один ключ = один элемент списка, по нему считаются добавление и удаление. */
object RegistryKeys {
    fun idFromUrl(url: String): String =
        url.split("/").lastOrNull { it.isNotEmpty() }?.substringBefore("?") ?: ""

    fun historyId(h: WatchHistoryItem): String =
        if (h.videoId.isNotEmpty()) h.videoId else idFromUrl(h.videoUrl)

    /** Все ключи, кроме истории просмотров (у неё версия — timestamp). */
    fun listKeys(r: UserRegistry): Set<String> {
        val s = HashSet<String>()
        r.likedVideos.forEach { s += "liked|" + it.videoUrl }
        r.watchLater.forEach { s += "later|" + it.videoUrl }
        r.subscriptions.forEach { s += "sub|" + it.name }
        r.hiddenVideos.forEach { s += "hid|$it" }
        r.hiddenTitles.forEach { s += "htl|$it" }
        r.dislikedVideos.forEach { s += "dis|$it" }
        r.hiddenVideosList.forEach { s += "hvl|" + it.videoUrl }
        r.dislikedVideosList.forEach { s += "dvl|" + it.videoUrl }
        r.searchHistory.forEach { s += "q|$it" }
        r.playlists.forEach { p ->
            s += "pl|" + p.name
            p.videos.forEach { s += "plv|" + p.name + "|" + it.videoUrl }
        }
        return s
    }

    fun historyKeys(r: UserRegistry): Set<String> = r.watchHistory.map { "hist|" + historyId(it) }.toSet()

    /**
     * Локальное изменение: сравнить старый и новый реестр, проставить время добавления
     * новым ключам и надгробия удалённым. Вызывается из UserRegistryManager.updateRegistry.
     */
    fun stampLocalChange(old: UserRegistry, new: UserRegistry, now: Long): UserRegistry {
        val oldKeys = listKeys(old)
        val newKeys = listKeys(new)
        val addedKeys = newKeys - oldKeys
        val removed = (oldKeys - newKeys) + (historyKeys(old) - historyKeys(new))
        if (addedKeys.isEmpty() && removed.isEmpty()) return new
        val added = HashMap(new.addedAt)
        val tomb = HashMap(new.tombstones)
        addedKeys.forEach { added[it] = now }
        removed.forEach { tomb[it] = now; added.remove(it) }
        tomb.entries.removeAll { now - it.value > RegistryMerge.PRUNE_MS }
        return new.copy(addedAt = added, tombstones = tomb)
    }

    /** Разовая миграция: у старых данных нет времени добавления. Даём маленькие числа в порядке списка. */
    fun migrateAddedAt(r: UserRegistry): UserRegistry {
        val added = HashMap(r.addedAt)
        var changed = false
        fun <T> stamp(items: List<T>, key: (T) -> String) {
            items.forEachIndexed { i, x ->
                val k = key(x)
                if (k !in added) { added[k] = (items.size - i).toLong(); changed = true }
            }
        }
        stamp(r.likedVideos) { "liked|" + it.videoUrl }
        stamp(r.watchLater) { "later|" + it.videoUrl }
        stamp(r.subscriptions) { "sub|" + it.name }
        stamp(r.hiddenVideos) { "hid|$it" }
        stamp(r.hiddenTitles) { "htl|$it" }
        stamp(r.dislikedVideos) { "dis|$it" }
        stamp(r.hiddenVideosList) { "hvl|" + it.videoUrl }
        stamp(r.dislikedVideosList) { "dvl|" + it.videoUrl }
        stamp(r.searchHistory) { "q|$it" }
        stamp(r.playlists) { "pl|" + it.name }
        r.playlists.forEach { p -> stamp(p.videos) { "plv|" + p.name + "|" + it.videoUrl } }
        return if (changed) r.copy(addedAt = added) else r
    }
}

/**
 * Слияние двух реестров. Чистая функция: merge(a, b) и merge(b, a) дают одинаковый результат
 * (кроме appSettings, они остаются локальными и берутся из a), merge(a, a) = a.
 */
object RegistryMerge {
    const val PRUNE_MS = 90L * 24 * 3600 * 1000
    private const val HISTORY_LIMIT = 500
    private const val SEARCH_LIMIT = 20

    private fun maxMaps(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> {
        val r = HashMap(a)
        b.forEach { (k, v) -> val o = r[k]; if (o == null || v > o) r[k] = v }
        return r
    }

    private fun <T> pick(items: List<T>): T {
        val d = items.distinct()
        return if (d.size == 1) d[0] else d.maxByOrNull { it.toString() }!!
    }

    private fun <T> union(
        prefix: String,
        a: List<T>,
        b: List<T>,
        key: (T) -> String,
        added: Map<String, Long>,
        alive: (String) -> Boolean
    ): List<T> {
        val by = LinkedHashMap<String, MutableList<T>>()
        for (x in a) by.getOrPut(prefix + key(x)) { ArrayList() }.add(x)
        for (x in b) by.getOrPut(prefix + key(x)) { ArrayList() }.add(x)
        return by.entries
            .filter { alive(it.key) }
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<T>>> { added[it.key] ?: 0L }.thenBy { it.key })
            .map { pick(it.value) }
    }

    fun merge(a: UserRegistry, b: UserRegistry, now: Long = System.currentTimeMillis()): UserRegistry {
        val tomb = maxMaps(a.tombstones, b.tombstones).filterValues { now - it <= PRUNE_MS }
        val added = maxMaps(a.addedAt, b.addedAt)
        fun alive(k: String) = (added[k] ?: 0L) > (tomb[k] ?: -1L)

        val clearedW = maxOf(a.watchHistoryClearedAt, b.watchHistoryClearedAt)
        val clearedS = maxOf(a.searchHistoryClearedAt, b.searchHistoryClearedAt)

        val history = (a.watchHistory + b.watchHistory)
            .filter { it.timestamp > clearedW && it.timestamp > (tomb["hist|" + RegistryKeys.historyId(it)] ?: -1L) }
            .groupBy { RegistryKeys.historyId(it) }
            .map { (_, items) ->
                items.maxWithOrNull(
                    compareBy<WatchHistoryItem>({ it.timestamp }, { it.progress }, { it.toString() })
                )!!
            }
            .sortedWith(compareByDescending<WatchHistoryItem> { it.timestamp }.thenBy { RegistryKeys.historyId(it) })
            .take(HISTORY_LIMIT)

        val search = union("q|", a.searchHistory, b.searchHistory, { it }, added) {
            alive(it) && (added[it] ?: 0L) > clearedS
        }.take(SEARCH_LIMIT)

        val weights = HashMap(a.tagWeights)
        b.tagWeights.forEach { (k, v) -> weights[k] = maxOf(weights[k] ?: 0f, v) }

        val allPlaylists = a.playlists + b.playlists
        val playlists = allPlaylists.map { it.name }.distinct()
            .filter { alive("pl|$it") }
            .map { name ->
                val parts = allPlaylists.filter { it.name == name }
                Playlist(
                    id = parts.minOf { it.id },
                    name = name,
                    videos = union(
                        "plv|$name|",
                        a.playlists.filter { it.name == name }.flatMap { it.videos },
                        b.playlists.filter { it.name == name }.flatMap { it.videos },
                        { it.videoUrl }, added, ::alive
                    )
                )
            }
            .sortedWith(compareBy<Playlist>({ added["pl|" + it.name] ?: 0L }, { it.name }))

        val result = UserRegistry(
            watchHistory = history,
            searchHistory = search,
            tagWeights = weights.toSortedMap().toMap(),
            subscriptions = union("sub|", a.subscriptions, b.subscriptions, { it.name }, added, ::alive),
            likedVideos = union("liked|", a.likedVideos, b.likedVideos, { it.videoUrl }, added, ::alive),
            watchLater = union("later|", a.watchLater, b.watchLater, { it.videoUrl }, added, ::alive),
            playlists = playlists,
            hiddenVideos = union("hid|", a.hiddenVideos, b.hiddenVideos, { it }, added, ::alive),
            hiddenTitles = union("htl|", a.hiddenTitles, b.hiddenTitles, { it }, added, ::alive),
            dislikedVideos = union("dis|", a.dislikedVideos, b.dislikedVideos, { it }, added, ::alive),
            hiddenVideosList = union("hvl|", a.hiddenVideosList, b.hiddenVideosList, { it.videoUrl }, added, ::alive),
            dislikedVideosList = union("dvl|", a.dislikedVideosList, b.dislikedVideosList, { it.videoUrl }, added, ::alive),
            appSettings = a.appSettings,
            lastSynced = maxOf(a.lastSynced, b.lastSynced),
            watchHistoryClearedAt = clearedW,
            searchHistoryClearedAt = clearedS
        )
        val keys = RegistryKeys.listKeys(result)
        return result.copy(
            addedAt = added.filterKeys { it in keys }.toSortedMap().toMap(),
            tombstones = tomb.toSortedMap().toMap()
        )
    }
}
