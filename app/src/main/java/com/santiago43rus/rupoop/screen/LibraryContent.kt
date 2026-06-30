package com.santiago43rus.rupoop.screen

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.components.*
import com.santiago43rus.rupoop.data.*
import com.santiago43rus.rupoop.service.DownloadService
import com.santiago43rus.rupoop.util.LibrarySubScreen
import com.santiago43rus.rupoop.util.extractId
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LibraryContent(vm: AppViewModel, listState: LazyListState) {
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogMessage by remember { mutableStateOf("") }

    var pendingDeleteVideoId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteVideoId?.let { videoId ->
                vm.downloadTracker.removeDownload(videoId)
            }
        }
        pendingDeleteVideoId = null
    }

    when (vm.currentLibSub) {
        LibrarySubScreen.NONE -> {
            LibraryScreen(
                vm.userRegistry,
                onVideoClick = { item ->
                    vm.playVideo(
                        SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl)),
                        null
                    )
                },
                onAuthorClick = { vm.loadAuthorVideos(it, false) },
                onMoreClick = { item, action ->
                    val video = SearchResult(videoUrl = item.videoUrl, title = item.title ?: "", thumbnailUrl = item.thumbnailUrl, author = Author(id = item.authorId, name = item.authorName ?: "", avatarUrl = item.authorAvatarUrl))
                    when (action) {
                        "remove" -> {
                            deleteDialogTitle = "Удалить из истории"
                            deleteDialogMessage = "Вы уверены, что хотите удалить это видео из истории просмотров?"
                            pendingDeleteAction = { vm.removeFromHistory(item.videoId) }
                        }
                        "share" -> vm.shareVideo(video)
                        "download" -> vm.showDownloadDialog = video
                        "later" -> vm.toggleWatchLater(video)
                        "playlist" -> vm.showPlaylistDialog = video
                    }
                },
                onActionClick = { action ->
                    when (action) {
                        "liked" -> vm.currentLibSub = LibrarySubScreen.LIKED
                        "later" -> vm.currentLibSub = LibrarySubScreen.WATCH_LATER
                        "playlists" -> vm.currentLibSub = LibrarySubScreen.PLAYLISTS
                        "history" -> vm.currentLibSub = LibrarySubScreen.HISTORY
                        "downloads" -> vm.currentLibSub = LibrarySubScreen.DOWNLOADS
                    }
                },
                listState = listState
            )
        }
        LibrarySubScreen.HISTORY -> {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.userRegistry.watchHistory) { item ->
                    val video = SearchResult(
                        videoUrl = item.videoUrl,
                        title = item.title ?: "",
                        thumbnailUrl = item.thumbnailUrl,
                        author = Author(
                            id = item.authorId,
                            name = item.authorName ?: "",
                            avatarUrl = item.authorAvatarUrl
                        ),
                        duration = (item.totalDuration / 1000).toInt()
                    )
                    VideoCardItem(
                        video = video, history = item,
                        onClick = { vm.playVideo(video, null, false) },
                        onAuthorClick = { vm.loadAuthorVideos(it, false) },
                        onMoreClick = { action ->
                            when (action) {
                                "remove" -> {
                                    deleteDialogTitle = "Удалить из истории"
                                    deleteDialogMessage = "Вы уверены, что хотите удалить это видео из истории просмотров?"
                                    pendingDeleteAction = { vm.removeFromHistory(item.videoId) }
                                }
                                "share" -> vm.shareVideo(video)
                                "download" -> vm.showDownloadDialog = video
                            }
                        },
                        isEditMode = true
                    )
                }
            }
        }
        LibrarySubScreen.LIKED -> VideoListScreen(vm.userRegistry.likedVideos, { v -> vm.playVideo(v, vm.userRegistry.likedVideos, true) }, { vm.loadAuthorVideos(it, false) }, { vm.shareVideo(it) }, { video ->
            deleteDialogTitle = "Удалить из понравившихся"
            deleteDialogMessage = "Вы уверены, что хотите удалить это видео из списка понравившихся?"
            pendingDeleteAction = { vm.toggleLike(video) }
        }, { vm.showDownloadDialog = it })
        LibrarySubScreen.WATCH_LATER -> VideoListScreen(vm.userRegistry.watchLater, { v -> vm.playVideo(v, vm.userRegistry.watchLater, true) }, { vm.loadAuthorVideos(it, false) }, { vm.shareVideo(it) }, { video ->
            deleteDialogTitle = "Удалить из \"Смотреть позже\""
            deleteDialogMessage = "Вы уверены, что хотите удалить это видео из списка \"Смотреть позже\"?"
            pendingDeleteAction = { vm.toggleWatchLater(video) }
        }, { vm.showDownloadDialog = it })
        LibrarySubScreen.PLAYLISTS -> {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.userRegistry.playlists) { playlist ->
                    LibraryRow(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        playlist.name,
                        playlist.videos.size.toString(),
                        onAction = {
                            deleteDialogTitle = "Удалить плейлист"
                            deleteDialogMessage = "Вы уверены, что хотите удалить плейлист \"${playlist.name}\"?"
                            pendingDeleteAction = { vm.deletePlaylist(playlist.id) }
                        }) {
                        vm.selectedPlaylist = playlist
                        vm.currentLibSub = LibrarySubScreen.PLAYLIST_DETAIL
                    }
                }
            }
        }
        LibrarySubScreen.PLAYLIST_DETAIL -> {
            VideoListScreen(
                vm.selectedPlaylist?.videos ?: emptyList(),
                { v -> vm.playVideo(v, vm.selectedPlaylist?.videos, true) },
                { vm.loadAuthorVideos(it, false) },
                { vm.shareVideo(it) },
                { video ->
                    deleteDialogTitle = "Удалить из плейлиста"
                    deleteDialogMessage = "Вы уверены, что хотите удалить это видео из плейлиста?"
                    pendingDeleteAction = { vm.selectedPlaylist?.let { vm.removeFromPlaylist(it.id, video.videoUrl) } }
                },
                { vm.showDownloadDialog = it }
            )
        }
        LibrarySubScreen.DOWNLOADS -> {
            val downloads by vm.downloadTracker.downloads.collectAsState()
            LaunchedEffect(Unit) {
                vm.downloadTracker.indexSavedFiles()
                while (true) {
                    delay(1000)
                    vm.downloadTracker.refresh()
                }
            }
            DownloadsScreen(
                downloads = downloads,
                onPause = { videoId ->
                    val intent = Intent(vm.getApplication(), DownloadService::class.java).apply {
                        action = "PAUSE"
                        putExtra("VIDEO_ID", videoId)
                    }
                    val showNotif = vm.settingsManager.showDownloadNotifications
                    if (showNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vm.getApplication<Application>().startForegroundService(intent)
                    } else {
                        vm.getApplication<Application>().startService(intent)
                    }
                },
                onResume = { videoId ->
                    val intent = Intent(vm.getApplication(), DownloadService::class.java).apply {
                        action = "RESUME"
                        putExtra("VIDEO_ID", videoId)
                    }
                    val showNotif = vm.settingsManager.showDownloadNotifications
                    if (showNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vm.getApplication<Application>().startForegroundService(intent)
                    } else {
                        vm.getApplication<Application>().startService(intent)
                    }
                },
                onCancel = { videoId ->
                    val intent = Intent(vm.getApplication(), DownloadService::class.java).apply {
                        action = "CANCEL"
                        putExtra("VIDEO_ID", videoId)
                    }
                    val showNotif = vm.settingsManager.showDownloadNotifications
                    if (showNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vm.getApplication<Application>().startForegroundService(intent)
                    } else {
                        vm.getApplication<Application>().startService(intent)
                    }
                },
                onDelete = { videoId ->
                    deleteDialogTitle = "Удалить загрузку"
                    deleteDialogMessage = "Вы уверены, что хотите удалить этот файл с устройства?"
                    pendingDeleteAction = {
                        val item = downloads.find { it.videoId == videoId }
                        item?.filePath?.let { path ->
                            val file = File(path)
                            var deleted = false
                            try {
                                if (file.exists() && file.delete()) {
                                    deleted = true
                                }
                            } catch (_: Exception) {}

                            if (deleted) {
                                vm.downloadTracker.removeDownload(videoId)
                            } else {
                                val uri = vm.downloadTracker.getUriFromFilePath(context, path)
                                if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                                    pendingDeleteVideoId = videoId
                                    deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
                                } else {
                                    vm.downloadTracker.removeDownload(videoId)
                                }
                            }
                        }
                    }
                },
                onRetry = { videoId ->
                    val item = downloads.find { it.videoId == videoId }
                    if (item != null) {
                        val video = SearchResult(videoUrl = "https://rutube.ru/video/$videoId/", title = item.title, thumbnailUrl = item.thumbnailUrl)
                        val isAudio = item.filePath?.endsWith(".mp3") == true || item.filePath?.endsWith(".m4a") == true
                        vm.startDownload(video, isAudio)
                    }
                },
                onPlay = { item ->
                    item.filePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val list = downloads.map { SearchResult(videoUrl = it.filePath ?: "", title = it.title, thumbnailUrl = it.thumbnailUrl) }
                            vm.playLocalFile(path, item.title, list, true)
                        }
                    }
                },
                onPermissionGranted = {
                    vm.downloadTracker.indexSavedFiles()
                }
            )
        }
    }

    if (pendingDeleteAction != null) {
        DeleteConfirmationDialog(
            title = deleteDialogTitle,
            message = deleteDialogMessage,
            onConfirm = {
                pendingDeleteAction?.invoke()
                pendingDeleteAction = null
            },
            onDismiss = {
                pendingDeleteAction = null
            }
        )
    }
}
