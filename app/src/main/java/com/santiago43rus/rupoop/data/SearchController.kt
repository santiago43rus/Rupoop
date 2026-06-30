package com.santiago43rus.rupoop.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.OverlayState
import com.santiago43rus.rupoop.util.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchController(
    private val scope: CoroutineScope,
    private val registryManager: UserRegistryManager,
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
    private val setIsSearchVisible: (Boolean) -> Unit
) {

    var searchQuery by mutableStateOf("")
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var isSearchExpanded by mutableStateOf(false)
    var searchSortOrder by mutableStateOf<String?>(null) // null = default, "-created_ts" = newest

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isBlank()) {
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

    fun performSearch(query: String, ordering: String? = searchSortOrder) {
        searchQuery = query
        isSearchExpanded = false

        setIsSearchVisible(true)
        setOverlayOrder(getOverlayOrder().filter { it != OverlayState.SEARCH } + OverlayState.SEARCH)
        if (getPlayerState() == PlayerState.FULL) setPlayerState(PlayerState.MINI)

        registryManager.addSearchQuery(query)
        pushToGitHub()
        val requestNav = getCurrentNav()
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.searchVideos(query, ordering = ordering) }
                val filteredResults = filterHiddenAndDisliked(resp.results)
                val currentStack = getSearchStacks()[requestNav] ?: mutableListOf()
                currentStack.add(NavigationController.SearchState(query, filteredResults, ordering))
                getSearchStacks()[requestNav] = currentStack

                if (getCurrentNav() == requestNav) {
                    updateSearchStates(query, filteredResults, ordering, false, true)
                }
            } catch (e: Exception) {
                Log.e("Rupoop", "Search error", e)
            }
        }
    }
}
