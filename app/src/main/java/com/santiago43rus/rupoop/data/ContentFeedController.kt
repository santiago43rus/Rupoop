package com.santiago43rus.rupoop.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.*

class ContentFeedController(
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val registryManager: UserRegistryManager,
    private val mainFeedRecommender: MainFeedRecommendationStrategy,
    private val filterHiddenAndDisliked: (List<SearchResult>) -> List<SearchResult>,
    private val onAuthorVisibleChanged: (Boolean) -> Unit,
    private val getPlayerState: () -> PlayerState,
    private val setPlayerState: (PlayerState) -> Unit,
    private val getAuthorSortOrder: () -> String
) {

    // ── Feed states ──
    var homeVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var subscriptionVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var authorVideos by mutableStateOf<List<SearchResult>>(emptyList())

    // ── Pagination: Home ──
    var isHomeLoadingMore by mutableStateOf(false)
    var isRefreshingHome by mutableStateOf(false)

    // ── Pagination: Author ──
    var isAuthorLoadingMore by mutableStateOf(false)
    var isRefreshingAuthor by mutableStateOf(false)
    var authorPage by mutableIntStateOf(1)
    var hasMoreAuthorVideos by mutableStateOf(true)

    // ── Pagination: Subscriptions ──
    var subsPage by mutableIntStateOf(1)
    var hasMoreSubsVideos by mutableStateOf(true)
    var isSubsLoadingMore by mutableStateOf(false)
    var isRefreshingSubs by mutableStateOf(false)

    // ── Load home ──
    fun loadHome(isLoadMore: Boolean) {
        scope.launch {
            if (isLoadMore) isHomeLoadingMore = true
            else { isRefreshingHome = true; homeVideos = emptyList() }

            try {
                val enabledGenres = settingsManager.enabledGenres.toList()
                val selectedGenres = if (enabledGenres.size > 4) {
                    enabledGenres.shuffled().take(4)
                } else enabledGenres

                val filmGenres = listOf("боевик", "комедия", "драма", "ужасы", "фантастика", "триллер", "детектив", "мелодрама")
                val queries = selectedGenres.map { genre ->
                    if (filmGenres.any { it.equals(genre, ignoreCase = true) }) {
                        "$genre фильм"
                    } else {
                        genre
                    }
                }.toMutableList()

                if (queries.isEmpty()) queries.add("популярное")

                val deferreds = queries.map { query ->
                    scope.async(Dispatchers.IO) {
                        try {
                            val targetPage = if (isLoadMore) (2..4).random() else 1
                            RetrofitClient.api.searchVideos(query, page = targetPage).results
                        } catch (e: Exception) {
                            Log.e("Rupoop", "Error fetching home genre query: $query", e)
                            emptyList<SearchResult>()
                        }
                    }
                }

                val allResults = deferreds.awaitAll().flatten().distinctBy { it.videoUrl }
                val newVideos = mainFeedRecommender.recommend(allResults)

                val updatedList = if (isLoadMore) (homeVideos + newVideos).distinctBy { it.videoUrl } else newVideos
                homeVideos = updatedList.take(200)
            } catch (e: Exception) {
                Log.e("Rupoop", "Error loading home", e)
            } finally {
                isRefreshingHome = false
                isHomeLoadingMore = false
            }
        }
    }

    // ── Load subscriptions ──
    fun loadSubscriptions(isLoadMore: Boolean) {
        scope.launch {
            if (isLoadMore) {
                isSubsLoadingMore = true
                subsPage++
            } else {
                isRefreshingSubs = true
                subsPage = 1
                hasMoreSubsVideos = true
                subscriptionVideos = emptyList()
            }

            val userRegistry = registryManager.registry
            if (userRegistry.subscriptions.isNotEmpty()) {
                try {
                    val allSubsVideos = mutableListOf<SearchResult>()
                    userRegistry.subscriptions.forEach { author ->
                        val resp = if (author.id != null) {
                            withContext(Dispatchers.IO) { RetrofitClient.api.getAuthorVideos(author.id.toString(), page = subsPage) }
                        } else {
                            withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, page = subsPage) }
                        }
                        allSubsVideos.addAll(resp.results.filter {
                            it.author?.name?.equals(author.name, ignoreCase = true) == true
                        })
                    }

                    if (allSubsVideos.isEmpty()) {
                        hasMoreSubsVideos = false
                    } else {
                        val sortedNewVideos = allSubsVideos.sortedByDescending { it.createdTs ?: "" }
                        val filteredNewVideos = filterHiddenAndDisliked(sortedNewVideos)
                        subscriptionVideos = if (isLoadMore) {
                            (subscriptionVideos + filteredNewVideos).distinctBy { it.videoUrl }
                        } else {
                            filteredNewVideos.distinctBy { it.videoUrl }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Subs load error", e)
                    if (isLoadMore) subsPage--
                }
            } else {
                subscriptionVideos = emptyList()
                hasMoreSubsVideos = false
            }
            isRefreshingSubs = false
            isSubsLoadingMore = false
        }
    }

    // ── Load author videos ──
    fun loadAuthorVideos(author: Author, isLoadMore: Boolean, onAuthorSelected: (Author) -> Unit) {
        if (!isLoadMore || hasMoreAuthorVideos) {
            scope.launch {
                if (isLoadMore) {
                    isAuthorLoadingMore = true
                    authorPage++
                } else {
                    isRefreshingAuthor = true
                    authorPage = 1
                    hasMoreAuthorVideos = true
                    onAuthorSelected(author)
                    authorVideos = emptyList()

                    onAuthorVisibleChanged(true)
                    if (getPlayerState() == PlayerState.FULL) setPlayerState(PlayerState.MINI)
                }
                try {
                    val resp = if (author.id != null) {
                        withContext(Dispatchers.IO) { RetrofitClient.api.getAuthorVideos(author.id.toString(), ordering = getAuthorSortOrder(), page = authorPage) }
                    } else {
                        withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(author.name, page = authorPage) }
                    }

                    if (resp.results.isEmpty()) {
                        hasMoreAuthorVideos = false
                    } else {
                        val filteredNewVideos = filterHiddenAndDisliked(resp.results)
                        val combined = if (isLoadMore) (authorVideos + filteredNewVideos) else filteredNewVideos
                        authorVideos = combined.sortedWith(
                            compareByDescending<SearchResult> { it.publicationTs ?: "" }
                                .thenByDescending { it.createdTs ?: "" }
                        ).distinctBy { it.videoUrl }
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Author videos error", e)
                    if (isLoadMore) authorPage--
                } finally {
                    isRefreshingAuthor = false
                    isAuthorLoadingMore = false
                }
            }
        }
    }
}
