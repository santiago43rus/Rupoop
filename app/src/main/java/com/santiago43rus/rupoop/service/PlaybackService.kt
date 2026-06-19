package com.santiago43rus.rupoop.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.santiago43rus.rupoop.AppViewModel

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "default_channel_id",
                "Воспроизведение",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление управления воспроизведением"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val basePlayer = AppViewModel.sharedPlayer ?: return

        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands().buildUpon()
                val vm = AppViewModel.instance
                if (vm != null) {
                    if (vm.currentVideoIndex < vm.currentVideoList.size - 1 || (!vm.isPlaylistMode && vm.relatedVideos.isNotEmpty())) {
                        commands.add(Player.COMMAND_SEEK_TO_NEXT)
                        commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    }
                    if (vm.currentVideoIndex > 0) {
                        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    }
                }
                commands.add(Player.COMMAND_SEEK_BACK)
                commands.add(Player.COMMAND_SEEK_FORWARD)
                return commands.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                val vm = AppViewModel.instance
                if (vm != null) {
                    if (command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                        return vm.currentVideoIndex < vm.currentVideoList.size - 1 || (!vm.isPlaylistMode && vm.relatedVideos.isNotEmpty())
                    }
                    if (command == Player.COMMAND_SEEK_TO_PREVIOUS || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                        return vm.currentVideoIndex > 0
                    }
                }
                if (command == Player.COMMAND_SEEK_BACK || command == Player.COMMAND_SEEK_FORWARD) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            override fun seekToNext() {
                seekToNextMediaItem()
            }

            override fun seekToNextMediaItem() {
                AppViewModel.instance?.let { vm ->
                    Handler(Looper.getMainLooper()).post {
                        vm.playNext()
                    }
                }
            }

            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }

            override fun seekToPreviousMediaItem() {
                AppViewModel.instance?.let { vm ->
                    Handler(Looper.getMainLooper()).post {
                        vm.playPrevious()
                    }
                }
            }

            override fun seekForward() {
                val incrementMs = (AppViewModel.instance?.settingsManager?.doubleTapSeekDuration ?: 10) * 1000L
                seekTo(currentPosition + incrementMs)
            }

            override fun seekBack() {
                val incrementMs = (AppViewModel.instance?.settingsManager?.doubleTapSeekDuration ?: 10) * 1000L
                seekTo((currentPosition - incrementMs).coerceAtLeast(0))
            }
        }

        val seekBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .build()
        val seekForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .build()

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setMediaButtonPreferences(listOf(seekBackButton, seekForwardButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}
