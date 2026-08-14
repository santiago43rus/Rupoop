package com.santiago43rus.rupoop

import androidx.media3.common.util.UnstableApi
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.util.extractId

// ── Subscription toggle ──
@UnstableApi
fun AppViewModel.toggleSubscription(author: Author) {
    val subs = userRegistry.subscriptions.toMutableList()
    val isSubbed = subs.any { it.name.equals(author.name, ignoreCase = true) }
    if (isSubbed) {
        subs.removeAll { it.name.equals(author.name, ignoreCase = true) }
    } else {
        subs.add(author)
    }
    registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
    userRegistry = registryManager.registry
    pushToGitHub()
}

@UnstableApi
fun AppViewModel.unsubscribeAuthor(author: Author, deleteFromGist: Boolean = false) {
    val subs = userRegistry.subscriptions.toMutableList()
    subs.removeAll { it.name.equals(author.name, ignoreCase = true) }
    registryManager.updateRegistry(userRegistry.copy(subscriptions = subs))
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Вы отписались от ${author.name} (удалено из Gist)")
    } else {
        showSnackbar("Вы отписались от ${author.name}")
    }
}

// ── Like ──
@UnstableApi
fun AppViewModel.toggleLike(video: SearchResult) {
    val added = registryManager.toggleLike(video)
    userRegistry = registryManager.registry
    pushToGitHub()
    showSnackbar(if (added) "Добавлено в Понравившиеся" else "Удалено из Понравившихся")
}

@UnstableApi
fun AppViewModel.removeFromLiked(video: SearchResult, deleteFromGist: Boolean = false) {
    val liked = userRegistry.likedVideos.toMutableList()
    liked.removeAll { it.videoUrl == video.videoUrl }
    registryManager.updateRegistry(userRegistry.copy(likedVideos = liked))
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Удалено из понравившихся и GitHub Gist")
    } else {
        showSnackbar("Удалено из понравившихся")
    }
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
fun AppViewModel.removeFromWatchLater(video: SearchResult, deleteFromGist: Boolean = false) {
    val later = userRegistry.watchLater.toMutableList()
    later.removeAll { it.videoUrl == video.videoUrl }
    registryManager.updateRegistry(userRegistry.copy(watchLater = later))
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Удалено из «Смотреть позже» и GitHub Gist")
    } else {
        showSnackbar("Удалено из «Смотреть позже»")
    }
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
fun AppViewModel.removeFromHistory(videoId: String, deleteFromGist: Boolean = false) {
    registryManager.removeFromHistory(videoId)
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Удалено из истории и GitHub Gist")
    }
}

@UnstableApi
fun AppViewModel.clearWatchHistory(deleteFromGist: Boolean = false) {
    registryManager.clearWatchHistory()
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("История просмотров очищена везде")
    } else {
        showSnackbar("История просмотров очищена на устройстве")
    }
}

@UnstableApi
fun AppViewModel.removeSearchQuery(query: String, deleteFromGist: Boolean = false) {
    registryManager.removeSearchQuery(query)
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Запрос удален из истории и GitHub Gist")
    }
}

@UnstableApi
fun AppViewModel.clearSearchHistory(deleteFromGist: Boolean = false) {
    registryManager.clearSearchHistory()
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("История поиска очищена везде")
    } else {
        showSnackbar("История поиска очищена на устройстве")
    }
}

@UnstableApi
fun AppViewModel.deletePlaylist(id: String, deleteFromGist: Boolean = false) {
    registryManager.deletePlaylist(id)
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Плейлист удален локально и из GitHub Gist")
    } else {
        showSnackbar("Плейлист удален")
    }
}

@UnstableApi
fun AppViewModel.removeFromPlaylist(playlistId: String, videoUrl: String, deleteFromGist: Boolean = false) {
    registryManager.removeFromPlaylist(playlistId, videoUrl)
    userRegistry = registryManager.registry
    selectedPlaylist = userRegistry.playlists.find { it.id == playlistId }
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Удалено из плейлиста и GitHub Gist")
    } else {
        showSnackbar("Удалено из плейлиста")
    }
}

@UnstableApi
fun AppViewModel.restoreHiddenVideo(videoId: String, title: String, deleteFromGist: Boolean = false) {
    registryManager.restoreVideo(videoId, title)
    userRegistry = registryManager.registry
    if (deleteFromGist) {
        pushToGitHub(forceDirect = true)
        showSnackbar("Видео удалено из скрытых и GitHub Gist")
    } else {
        showSnackbar("Видео удалено из скрытых")
    }
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
