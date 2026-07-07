package com.santiago43rus.rupoop

import androidx.media3.common.util.UnstableApi
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.extractId

// ── Subscription toggle ──
@UnstableApi
fun AppViewModel.toggleSubscription(author: Author) {
    val subs = userRegistry.subscriptions.toMutableList()
    if (subs.any { it.name.equals(author.name, ignoreCase = true) }) {
        subs.removeAll { it.name.equals(author.name, ignoreCase = true) }
    } else {
        subs.add(author)
    }
    registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
    userRegistry = registryManager.registry
    pushToGitHub()
}

// ── Like ──
@UnstableApi
fun AppViewModel.toggleLike(video: SearchResult) {
    val added = registryManager.toggleLike(video)
    userRegistry = registryManager.registry
    pushToGitHub()
    showSnackbar(if (added) "Добавлено в Понравившиеся" else "Удалено из Понравившихся")
}

// ── Dislike ──
@UnstableApi
fun AppViewModel.toggleDislike(video: SearchResult) {
    val added = registryManager.toggleDislike(video)
    userRegistry = registryManager.registry
    if (added) {
        removeVideoFromUiLists(video)
    }
    pushToGitHub()
    showSnackbar(if (added) "Добавлено в Не нравится" else "Удалено из Не нравится")
}

// ── Watch Later ──
@UnstableApi
fun AppViewModel.toggleWatchLater(video: SearchResult) {
    val added = registryManager.toggleWatchLater(video)
    userRegistry = registryManager.registry
    pushToGitHub()
    showSnackbar(if (added) "Добавлено в Смотреть позже" else "Удалено из Смотреть позже")
}

@UnstableApi
fun AppViewModel.addToWatchLaterViaMenu(video: SearchResult) {
    val exists = userRegistry.watchLater.any { extractId(it.videoUrl) == extractId(video.videoUrl) }
    if (exists) {
        showSnackbar("Видео уже находится в разделе \"Смотреть позже\"")
    } else {
        registryManager.toggleWatchLater(video)
        userRegistry = registryManager.registry
        pushToGitHub()
        showSnackbar("Добавлено в Смотреть позже")
    }
}

// ── Playlist ──
@UnstableApi
fun AppViewModel.addToPlaylist(name: String, video: SearchResult) {
    val added = registryManager.addToPlaylist(name, video)
    userRegistry = registryManager.registry
    showPlaylistDialog = null
    pushToGitHub()
    if (added) {
        showSnackbar("Добавлено в $name")
    } else {
        showSnackbar("Видео уже добавлено в плейлист \"$name\"")
    }
}

@UnstableApi
fun AppViewModel.createPlaylistAndAdd(name: String, video: SearchResult) {
    registryManager.addToPlaylist(name, video)
    userRegistry = registryManager.registry
    showPlaylistDialog = null
    pushToGitHub()
    showSnackbar("Плейлист $name создан")
}

// ── History ──
@UnstableApi
fun AppViewModel.removeFromHistory(videoId: String) {
    registryManager.removeFromHistory(videoId)
    userRegistry = registryManager.registry
    pushToGitHub()
}

@UnstableApi
fun AppViewModel.removeSearchQuery(query: String) {
    registryManager.removeSearchQuery(query)
    userRegistry = registryManager.registry
    pushToGitHub()
}

@UnstableApi
fun AppViewModel.deletePlaylist(id: String) {
    registryManager.deletePlaylist(id)
    userRegistry = registryManager.registry
    pushToGitHub()
}

@UnstableApi
fun AppViewModel.removeFromPlaylist(playlistId: String, videoUrl: String) {
    registryManager.removeFromPlaylist(playlistId, videoUrl)
    userRegistry = registryManager.registry
    selectedPlaylist = userRegistry.playlists.find { it.id == playlistId }
    pushToGitHub()
}

@UnstableApi
fun AppViewModel.handleVideoMoreAction(video: SearchResult, action: String) {
    when (action) {
        "later" -> addToWatchLaterViaMenu(video)
        "playlist" -> showPlaylistDialog = video
        "share" -> shareVideo(video)
        "download" -> showDownloadDialog = video
        "dislike" -> {
            registryManager.toggleDislike(video)
            userRegistry = registryManager.registry
            removeVideoFromUiLists(video)
            pushToGitHub()
            showSnackbar("Видео отмечено как \"Не нравится\"")
        }
        "not_interested" -> {
            registryManager.hideVideo(video)
            registryManager.hideTitle(video.title)
            val videoId = extractId(video.videoUrl)
            if (videoId != null && !userRegistry.dislikedVideos.contains(videoId)) {
                registryManager.toggleDislike(video)
            }
            userRegistry = registryManager.registry
            removeVideoFromUiLists(video)
            pushToGitHub()
            showSnackbar("Видео и его аналоги скрыты из ленты")
        }
    }
}

@UnstableApi
fun AppViewModel.removeVideoFromUiLists(video: SearchResult) {
    val videoId = extractId(video.videoUrl)
    val title = video.title
    val filterPredicate: (SearchResult) -> Boolean = { item ->
        val itemId = extractId(item.videoUrl)
        itemId != videoId && !item.title.contains(title, ignoreCase = true)
    }
    homeVideos = homeVideos.filter(filterPredicate)
    searchResults = searchResults.filter(filterPredicate)
    subscriptionVideos = subscriptionVideos.filter(filterPredicate)
    authorVideos = authorVideos.filter(filterPredicate)
    relatedVideos = relatedVideos.filter(filterPredicate)
}

@UnstableApi
fun AppViewModel.filterHiddenAndDisliked(videos: List<SearchResult>): List<SearchResult> {
    val hiddenIds = userRegistry.hiddenVideos.toSet()
    val dislikedIds = userRegistry.dislikedVideos.toSet()
    val hiddenTitles = userRegistry.hiddenTitles.toSet()
    return videos.filter { video ->
        val id = extractId(video.videoUrl)
        if (id in hiddenIds || id in dislikedIds) false
        else if (hiddenTitles.any { video.title.contains(it, ignoreCase = true) }) false
        else true
    }
}

@UnstableApi
fun AppViewModel.isPreviousVideoDislikedOrHidden(): Boolean {
    if (currentVideoIndex <= 0 || currentVideoIndex >= currentVideoList.size) return false
    val prevVideo = currentVideoList[currentVideoIndex - 1]
    val prevId = extractId(prevVideo.videoUrl)
    val hiddenIds = userRegistry.hiddenVideos.toSet()
    val dislikedIds = userRegistry.dislikedVideos.toSet()
    val hiddenTitles = userRegistry.hiddenTitles.toSet()
    return prevId in hiddenIds || prevId in dislikedIds || hiddenTitles.any { prevVideo.title.contains(it, ignoreCase = true) }
}
