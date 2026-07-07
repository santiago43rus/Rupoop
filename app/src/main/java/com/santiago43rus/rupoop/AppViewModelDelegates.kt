@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.santiago43rus.rupoop

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.santiago43rus.rupoop.data.GitHubUser
import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.Playlist
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.service.DownloadService
import com.santiago43rus.rupoop.player.playLocalFile
import com.santiago43rus.rupoop.player.*
import com.santiago43rus.rupoop.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Navigation Property Delegation ──
var AppViewModel.currentNav: NavItem
    get() = navigationController.currentNav
    set(value) { navigationController.currentNav = value }

var AppViewModel.currentLibSub: LibrarySubScreen
    get() = navigationController.currentLibSub
    set(value) { navigationController.currentLibSub = value }

var AppViewModel.selectedPlaylist: Playlist?
    get() = navigationController.selectedPlaylist
    set(value) { navigationController.selectedPlaylist = value }

var AppViewModel.selectedAuthor: Author?
    get() = navigationController.selectedAuthor
    set(value) { navigationController.selectedAuthor = value }

var AppViewModel.isAuthorVisible: Boolean
    get() = navigationController.isAuthorVisible
    set(value) { navigationController.isAuthorVisible = value }

var AppViewModel.isSettingsVisible: Boolean
    get() = navigationController.isSettingsVisible
    set(value) { navigationController.isSettingsVisible = value }

var AppViewModel.isHiddenVideosVisible: Boolean
    get() = navigationController.isHiddenVideosVisible
    set(value) { navigationController.isHiddenVideosVisible = value }

var AppViewModel.isNotificationSettingsVisible: Boolean
    get() = navigationController.isNotificationSettingsVisible
    set(value) { navigationController.isNotificationSettingsVisible = value }

var AppViewModel.isSearchVisible: Boolean
    get() = navigationController.isSearchVisible
    set(value) { navigationController.isSearchVisible = value }

var AppViewModel.overlayOrder: List<OverlayState>
    get() = navigationController.overlayOrder
    set(value) { navigationController.overlayOrder = value }

fun AppViewModel.restoreSearchStateForTab(tab: NavItem) = navigationController.restoreSearchStateForTab(tab)
fun AppViewModel.clearCurrentSearchStack() = navigationController.clearCurrentSearchStack()
fun AppViewModel.handleBack(): Boolean = navigationController.handleBack()

// ── Playback Property Delegation ──
val AppViewModel.exoPlayer: ExoPlayer
    get() = playbackController.exoPlayer

@get:UnstableApi
@set:UnstableApi
var AppViewModel.currentVideoList: List<SearchResult>
    get() = playbackController.currentVideoList
    set(value) { playbackController.currentVideoList = value }

var AppViewModel.currentVideoIndex: Int
    get() = playbackController.currentVideoIndex
    set(value) { playbackController.currentVideoIndex = value }

var AppViewModel.isPlaylistMode: Boolean
    get() = playbackController.isPlaylistMode
    set(value) { playbackController.isPlaylistMode = value }

var AppViewModel.playerState: PlayerState
    get() = playbackController.playerState
    set(value) { playbackController.playerState = value }

var AppViewModel.playerTransitionProgress: Float
    get() = playbackController.playerTransitionProgress
    set(value) { playbackController.playerTransitionProgress = value }

@get:UnstableApi
@set:UnstableApi
var AppViewModel.currentVideo: SearchResult?
    get() = playbackController.currentVideo
    set(value) { playbackController.currentVideo = value }

var AppViewModel.isFullscreenVideo: Boolean
    get() = playbackController.isFullscreenVideo
    set(value) { playbackController.isFullscreenVideo = value }

var AppViewModel.isPlaying: Boolean
    get() = playbackController.isPlaying
    set(value) { playbackController.isPlaying = value }

var AppViewModel.isBuffering: Boolean
    get() = playbackController.isBuffering
    set(value) { playbackController.isBuffering = value }

var AppViewModel.isBackgroundPlaybackEnabled: Boolean
    get() = playbackController.isBackgroundPlaybackEnabled
    set(value) { playbackController.isBackgroundPlaybackEnabled = value }

@get:UnstableApi
@set:UnstableApi
var AppViewModel.relatedVideos: List<SearchResult>
    get() = playbackController.relatedVideos
    set(value) { playbackController.relatedVideos = value }

@UnstableApi
fun AppViewModel.playVideo(video: SearchResult, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
    playbackController.playVideo(video, list, isPlaylist)
}
fun AppViewModel.playNext() = playbackController.playNext()
fun AppViewModel.playPrevious() = playbackController.playPrevious()
@UnstableApi
fun AppViewModel.playLocalFile(filePath: String, title: String, list: List<SearchResult>? = null, isPlaylist: Boolean = false) {
    playbackController.playLocalFile(filePath, title, list, isPlaylist)
}
fun AppViewModel.closePlayer() = playbackController.closePlayer()
fun AppViewModel.startProgressSaving(): Unit = playbackController.startProgressSaving()
fun AppViewModel.stopProgressSaving(): Unit = playbackController.stopProgressSaving()
fun AppViewModel.toggleBackgroundPlayback(): Unit = playbackController.toggleBackgroundPlayback()

// ── Auth Property Delegation ──
var AppViewModel.isAuthenticating: Boolean
    get() = authController.isAuthenticating
    set(value) { authController.isAuthenticating = value }

var AppViewModel.isAuthenticated: Boolean
    get() = authController.isAuthenticated
    set(value) { authController.isAuthenticated = value }

var AppViewModel.githubUser: GitHubUser?
    get() = authController.githubUser
    set(value) { authController.githubUser = value }

var AppViewModel.isAccountMenuExpanded: Boolean
    get() = authController.isAccountMenuExpanded
    set(value) { authController.isAccountMenuExpanded = value }

fun AppViewModel.initializeApp() = authController.initializeApp()
fun AppViewModel.processAuthResponse(response: net.openid.appauth.AuthorizationResponse) = authController.processAuthResponse(response)
fun AppViewModel.onAuthSuccess(token: String) = authController.onAuthSuccess(token)
fun AppViewModel.logout() = authController.logout()

// ── Search Property Delegation ──
var AppViewModel.searchQuery: String
    get() = searchController.searchQuery
    set(value) { searchController.searchQuery = value }

var AppViewModel.searchSuggestions: List<String>
    get() = searchController.searchSuggestions
    set(value) { searchController.searchSuggestions = value }

var AppViewModel.isSearchExpanded: Boolean
    get() = searchController.isSearchExpanded
    set(value) { searchController.isSearchExpanded = value }

var AppViewModel.searchSortOrder: String?
    get() = searchController.searchSortOrder
    set(value) { searchController.searchSortOrder = value }

fun AppViewModel.updateSearchQuery(query: String) = searchController.updateSearchQuery(query)
@UnstableApi
fun AppViewModel.performSearch(query: String, ordering: String? = searchSortOrder) = searchController.performSearch(query, ordering)

// ── Feed Property Delegation ──
@get:UnstableApi
@set:UnstableApi
var AppViewModel.homeVideos: List<SearchResult>
    get() = contentFeedController.homeVideos
    set(value) { contentFeedController.homeVideos = value }

@get:UnstableApi
@set:UnstableApi
var AppViewModel.subscriptionVideos: List<SearchResult>
    get() = contentFeedController.subscriptionVideos
    set(value) { contentFeedController.subscriptionVideos = value }

@get:UnstableApi
@set:UnstableApi
var AppViewModel.authorVideos: List<SearchResult>
    get() = contentFeedController.authorVideos
    set(value) { contentFeedController.authorVideos = value }

var AppViewModel.isHomeLoadingMore: Boolean
    get() = contentFeedController.isHomeLoadingMore
    set(value) { contentFeedController.isHomeLoadingMore = value }

var AppViewModel.isRefreshingHome: Boolean
    get() = contentFeedController.isRefreshingHome
    set(value) { contentFeedController.isRefreshingHome = value }

var AppViewModel.isAuthorLoadingMore: Boolean
    get() = contentFeedController.isAuthorLoadingMore
    set(value) { contentFeedController.isAuthorLoadingMore = value }

var AppViewModel.isRefreshingAuthor: Boolean
    get() = contentFeedController.isRefreshingAuthor
    set(value) { contentFeedController.isRefreshingAuthor = value }

var AppViewModel.authorPage: Int
    get() = contentFeedController.authorPage
    set(value) { contentFeedController.authorPage = value }

var AppViewModel.hasMoreAuthorVideos: Boolean
    get() = contentFeedController.hasMoreAuthorVideos
    set(value) { contentFeedController.hasMoreAuthorVideos = value }

var AppViewModel.isSubsLoadingMore: Boolean
    get() = contentFeedController.isSubsLoadingMore
    set(value) { contentFeedController.isSubsLoadingMore = value }

var AppViewModel.isRefreshingSubs: Boolean
    get() = contentFeedController.isRefreshingSubs
    set(value) { contentFeedController.isRefreshingSubs = value }

var AppViewModel.hasMoreSubsVideos: Boolean
    get() = contentFeedController.hasMoreSubsVideos
    set(value) { contentFeedController.hasMoreSubsVideos = value }

@UnstableApi
fun AppViewModel.loadHome(isLoadMore: Boolean) = contentFeedController.loadHome(isLoadMore)
@UnstableApi
fun AppViewModel.loadSubscriptions(isLoadMore: Boolean) = contentFeedController.loadSubscriptions(isLoadMore)
@UnstableApi
fun AppViewModel.loadAuthorVideos(author: Author, isLoadMore: Boolean) {
    contentFeedController.loadAuthorVideos(author, isLoadMore) { selected ->
        selectedAuthor = selected
    }
}


