package com.santiago43rus.rupoop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiago43rus.rupoop.util.LibrarySubScreen
import com.santiago43rus.rupoop.util.NavItem
import com.santiago43rus.rupoop.util.OverlayState
import com.santiago43rus.rupoop.util.PlayerState

class NavigationController(
    private val getPlayerState: () -> PlayerState,
    private val setPlayerState: (PlayerState) -> Unit,
    private val getIsFullscreenVideo: () -> Boolean,
    private val setIsFullscreenVideo: (Boolean) -> Unit,
    private val stopPlayer: () -> Unit,
    private val pushToGitHub: () -> Unit,
    private val updateSearchStates: (query: String, results: List<SearchResult>, order: String?, expand: Boolean, visible: Boolean) -> Unit
) {

    // ── Navigation states ──
    private val _currentNavState = mutableStateOf(NavItem.HOME)
    var currentNav: NavItem
        get() = _currentNavState.value
        set(value) {
            val oldNav = _currentNavState.value
            if (oldNav != value) {
                // Save current author state before switching
                authorStates[oldNav] = AuthorState(
                    isVisible = isAuthorVisible,
                    author = selectedAuthor,
                    videos = authorVideos,
                    page = authorPage,
                    hasMore = hasMoreAuthorVideos
                )
                // Save current library sub-screen state before switching
                if (oldNav == NavItem.LIBRARY) {
                    librarySubScreenStates[oldNav] = currentLibSub
                }

                _currentNavState.value = value
                restoreSearchStateForTab(value)

                // Restore library sub-screen state for the new tab
                if (value == NavItem.LIBRARY) {
                    currentLibSub = librarySubScreenStates[value] ?: LibrarySubScreen.NONE
                } else {
                    currentLibSub = LibrarySubScreen.NONE
                }

                // Restore author state for the new tab
                val authorState = authorStates[value] ?: AuthorState(false, null, emptyList(), 1, true)
                isAuthorVisible = authorState.isVisible
                selectedAuthor = authorState.author
                authorVideos = authorState.videos
                authorPage = authorState.page
                hasMoreAuthorVideos = authorState.hasMore
            }
        }

    var currentLibSub by mutableStateOf(LibrarySubScreen.NONE)
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
    var selectedAuthor by mutableStateOf<Author?>(null)

    // ── Author State Per Tab ──
    data class AuthorState(
        val isVisible: Boolean,
        val author: Author?,
        val videos: List<SearchResult>,
        val page: Int,
        val hasMore: Boolean
    )

    val authorStates = mutableMapOf<NavItem, AuthorState>(
        NavItem.HOME to AuthorState(false, null, emptyList(), 1, true),
        NavItem.SUBSCRIPTIONS to AuthorState(false, null, emptyList(), 1, true),
        NavItem.LIBRARY to AuthorState(false, null, emptyList(), 1, true)
    )

    // Helper properties synchronized with current active author states
    var isAuthorVisible by mutableStateOf(false)
    var authorVideos by mutableStateOf<List<SearchResult>>(emptyList())
    var authorPage by mutableStateOf(1)
    var hasMoreAuthorVideos by mutableStateOf(true)

    // ── Library Sub-Screen State Per Tab ──
    val librarySubScreenStates = mutableMapOf<NavItem, LibrarySubScreen>(
        NavItem.HOME to LibrarySubScreen.NONE,
        NavItem.SUBSCRIPTIONS to LibrarySubScreen.NONE,
        NavItem.LIBRARY to LibrarySubScreen.NONE
    )

    // ── Search State Per Tab ──
    data class SearchState(
        val query: String,
        val results: List<SearchResult>,
        val ordering: String? = null
    )

    val searchStacks = mutableMapOf<NavItem, MutableList<SearchState>>(
        NavItem.HOME to mutableListOf(),
        NavItem.SUBSCRIPTIONS to mutableListOf(),
        NavItem.LIBRARY to mutableListOf()
    )

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearchVisible by mutableStateOf(false)
    var isSearchExpanded by mutableStateOf(false)
    var searchSortOrder by mutableStateOf<String?>(null)

    // ── Overlays ──
    var isSettingsVisible by mutableStateOf(false)
    var isHiddenVideosVisible by mutableStateOf(false)
    var isNotificationSettingsVisible by mutableStateOf(false)
    var overlayOrder by mutableStateOf(listOf(OverlayState.SEARCH, OverlayState.AUTHOR))

    fun restoreSearchStateForTab(tab: NavItem) {
        val stack = searchStacks[tab] ?: mutableListOf()
        if (stack.isNotEmpty()) {
            val top = stack.last()
            searchQuery = top.query
            searchResults = top.results
            searchSortOrder = top.ordering
            isSearchVisible = true
            isSearchExpanded = false
            updateSearchStates(top.query, top.results, top.ordering, false, true)
            if (!overlayOrder.contains(OverlayState.SEARCH)) {
                overlayOrder = overlayOrder + OverlayState.SEARCH
            }
        } else {
            searchQuery = ""
            searchResults = emptyList()
            isSearchVisible = false
            isSearchExpanded = false
            updateSearchStates("", emptyList(), null, false, false)
            overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
        }
    }

    fun clearCurrentSearchStack() {
        searchStacks[currentNav]?.clear()
        searchQuery = ""
        searchResults = emptyList()
        isSearchVisible = false
        isSearchExpanded = false
        updateSearchStates("", emptyList(), null, false, false)
        overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
    }

    fun handleBack(): Boolean {
        if (getIsFullscreenVideo()) { setIsFullscreenVideo(false); return true }
        if (getPlayerState() == PlayerState.FULL) { setPlayerState(PlayerState.MINI); return true }
        if (isNotificationSettingsVisible) { isNotificationSettingsVisible = false; return true }
        if (isHiddenVideosVisible) { isHiddenVideosVisible = false; return true }
        if (isSettingsVisible) { isSettingsVisible = false; return true }
        if (isSearchExpanded) {
            val currentStack = searchStacks[currentNav] ?: mutableListOf()
            if (currentStack.isNotEmpty()) {
                isSearchExpanded = false
                return true
            }
        }

        val topVisible = overlayOrder.lastOrNull {
            (it == OverlayState.SEARCH && isSearchVisible) ||
            (it == OverlayState.AUTHOR && isAuthorVisible)
        }

        if (topVisible == OverlayState.SEARCH) {
            val currentStack = searchStacks[currentNav] ?: mutableListOf()
            if (currentStack.isNotEmpty()) {
                currentStack.removeAt(currentStack.size - 1)
            }
            if (currentStack.isNotEmpty()) {
                val previous = currentStack.last()
                searchQuery = previous.query
                searchResults = previous.results
                searchSortOrder = previous.ordering
                isSearchExpanded = false
                isSearchVisible = true
                updateSearchStates(previous.query, previous.results, previous.ordering, false, true)
            } else {
                isSearchVisible = false
                searchQuery = ""
                searchResults = emptyList()
                updateSearchStates("", emptyList(), null, false, false)
                overlayOrder = overlayOrder.filter { it != OverlayState.SEARCH }
            }
            return true
        }
        
        if (isSearchExpanded) {
            isSearchExpanded = false
            return true
        }

        if (topVisible == OverlayState.AUTHOR) { isAuthorVisible = false; return true }
        if (currentLibSub != LibrarySubScreen.NONE) { currentLibSub = LibrarySubScreen.NONE; return true }
        if (currentNav != NavItem.HOME) { currentNav = NavItem.HOME; searchQuery = ""; return true }

        setPlayerState(PlayerState.CLOSED)
        stopPlayer()
        pushToGitHub()
        return true
    }
}
