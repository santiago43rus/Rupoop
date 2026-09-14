package com.santiago43rus.rupoop.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiago43rus.rupoop.network.LordfilmSearchEngine
import com.santiago43rus.rupoop.network.OkSearchEngine
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.network.VkSearchEngine
import com.santiago43rus.rupoop.parser.UniversalVideoParser
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.OverlayState
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchSource(val displayName: String) {
    ALL("Все"),
    RUTUBE("Rutube"),
    VK("ВКонтакте"),
    OK("Одноклассники"),
    LORDFILM("Lordfilm")
}

class SearchController(
    private val scope: CoroutineScope,
    private val registryManager: UserRegistryManager,
    private val onRegistryUpdate: (UserRegistry) -> Unit,
    private val pushToGitHub: () -> Unit,
    private val filterHiddenAndDisliked: (List<SearchResult>) -> List<SearchResult>,
    private val getPlayerState: () -> PlayerState,
    private val setPlayerState: (PlayerState) -> Unit,
    private val getCurrentNav: () -> NavItem,
    private val getSearchStacks: () -> MutableMap<NavItem, MutableList<NavigationController.SearchState>>,
    private val updateSearchStates: (query: String, results: List<SearchResult>, order: String?, expand: Boolean, visible: Boolean) -> Unit,
    private val getOverlayOrder: () -> List<OverlayState>,
    private val setOverlayOrder: (List<OverlayState>) -> Unit,
    private val getIsSearchVisible: () -> Boolean,
    private val setIsSearchVisible: (Boolean) -> Unit,
    private val onPlayUrl: ((String) -> Unit)? = null
) {

    var searchQuery by mutableStateOf("")
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var isSearchExpanded by mutableStateOf(false)
    var searchSortOrder by mutableStateOf<String?>(null) // null = default, "-created_ts" = newest
    var selectedSearchSource by mutableStateOf(SearchSource.ALL)

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchSuggestions = emptyList()
            return
        }
        if (UniversalVideoParser.isHttpUrl(query)) {
            searchSuggestions = emptyList()
            return
        }
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.suggestApi.getSuggestions(query) }
                val jsonString = response.body()?.string()
                if (jsonString != null) {
                    val jsonArray = RetrofitClient.json.parseToJsonElement(jsonString) as kotlinx.serialization.json.JsonArray
                    if (jsonArray.size > 1 && jsonArray[1] is kotlinx.serialization.json.JsonArray) {
                        val suggestionsArray = jsonArray[1] as kotlinx.serialization.json.JsonArray
                        searchSuggestions = suggestionsArray.map { it.toString().removeSurrounding("\"") }
                    }
                }
            } catch (e: Exception) {
                Log.e("Rupoop", "Search suggest auto-complete error", e)
            }
        }
    }

    fun selectSearchSource(source: SearchSource) {
        selectedSearchSource = source
        // Force clear current search results immediately
        val requestNav = getCurrentNav()
        updateSearchStates(searchQuery, emptyList(), searchSortOrder, false, true)

        if (searchQuery.isNotBlank()) {
            scope.launch {
                try {
                    val newResults = executeMultiSourceSearch(searchQuery, searchSortOrder, source)
                    val filteredResults = filterHiddenAndDisliked(newResults)

                    val currentStack = getSearchStacks()[requestNav] ?: mutableListOf()
                    if (currentStack.isNotEmpty()) {
                        currentStack[currentStack.size - 1] = NavigationController.SearchState(searchQuery, filteredResults, searchSortOrder)
                    } else {
                        currentStack.add(NavigationController.SearchState(searchQuery, filteredResults, searchSortOrder))
                    }
                    getSearchStacks()[requestNav] = currentStack

                    if (getCurrentNav() == requestNav) {
                        updateSearchStates(searchQuery, filteredResults, searchSortOrder, false, true)
                    }
                } catch (e: Exception) {
                    Log.e("Rupoop", "Error selecting search source: $source", e)
                }
            }
        }
    }

    fun performSearch(query: String, ordering: String? = searchSortOrder) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        searchQuery = trimmed
        isSearchExpanded = false

        if (UniversalVideoParser.isHttpUrl(trimmed)) {
            registryManager.addSearchQuery(trimmed)
            onRegistryUpdate(registryManager.registry)
            pushToGitHub()
            setIsSearchVisible(false)
            onPlayUrl?.invoke(trimmed)
            return
        }

        setIsSearchVisible(true)
        setOverlayOrder(getOverlayOrder().filter { it != OverlayState.SEARCH } + OverlayState.SEARCH)
        if (getPlayerState() == PlayerState.FULL) setPlayerState(PlayerState.MINI)

        // Clear existing results prior to new search
        val requestNav = getCurrentNav()
        updateSearchStates(trimmed, emptyList(), ordering, false, true)

        registryManager.addSearchQuery(trimmed)
        onRegistryUpdate(registryManager.registry)
        pushToGitHub()

        scope.launch {
            try {
                val searchResults = executeMultiSourceSearch(trimmed, ordering, selectedSearchSource)
                val filteredResults = filterHiddenAndDisliked(searchResults)

                val currentStack = getSearchStacks()[requestNav] ?: mutableListOf()
                currentStack.add(NavigationController.SearchState(trimmed, filteredResults, ordering))
                getSearchStacks()[requestNav] = currentStack

                if (getCurrentNav() == requestNav) {
                    updateSearchStates(trimmed, filteredResults, ordering, false, true)
                }
            } catch (e: Exception) {
                Log.e("Rupoop", "Search error", e)
            }
        }
    }

    private suspend fun executeMultiSourceSearch(
        query: String,
        ordering: String?,
        source: SearchSource
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        when (source) {
            SearchSource.RUTUBE -> {
                try {
                    RetrofitClient.api.searchVideos(query, ordering = ordering).results
                } catch (e: Exception) {
                    Log.e("Rupoop", "Rutube search failed", e)
                    emptyList()
                }
            }
            SearchSource.VK -> {
                VkSearchEngine.search(query)
            }
            SearchSource.OK -> {
                OkSearchEngine.search(query)
            }
            SearchSource.LORDFILM -> {
                LordfilmSearchEngine.search(query)
            }
            SearchSource.ALL -> {
                val rutubeDeferred = scope.async(Dispatchers.IO) {
                    try {
                        RetrofitClient.api.searchVideos(query, ordering = ordering).results
                    } catch (e: Exception) {
                        Log.e("Rupoop", "Rutube search error in ALL mode", e)
                        emptyList<SearchResult>()
                    }
                }
                val vkDeferred = scope.async(Dispatchers.IO) {
                    try {
                        VkSearchEngine.search(query)
                    } catch (e: Exception) {
                        Log.e("Rupoop", "VK search error in ALL mode", e)
                        emptyList<SearchResult>()
                    }
                }
                val okDeferred = scope.async(Dispatchers.IO) {
                    try {
                        OkSearchEngine.search(query)
                    } catch (e: Exception) {
                        Log.e("Rupoop", "OK search error in ALL mode", e)
                        emptyList<SearchResult>()
                    }
                }
                val lordfilmDeferred = scope.async(Dispatchers.IO) {
                    try {
                        LordfilmSearchEngine.search(query)
                    } catch (e: Exception) {
                        Log.e("Rupoop", "Lordfilm search error in ALL mode", e)
                        emptyList<SearchResult>()
                    }
                }

                val rutubeRes = rutubeDeferred.await()
                val vkRes = vkDeferred.await()
                val okRes = okDeferred.await()
                val lordfilmRes = lordfilmDeferred.await()

                interleaveResults(listOf(rutubeRes, vkRes, okRes, lordfilmRes))
            }
        }
    }

    private fun interleaveResults(lists: List<List<SearchResult>>): List<SearchResult> {
        val result = mutableListOf<SearchResult>()
        var index = 0
        var added = true
        while (added) {
            added = false
            for (list in lists) {
                if (index < list.size) {
                    result.add(list[index])
                    added = true
                }
            }
            index++
        }
        return result.distinctBy { it.videoUrl }
    }
}
