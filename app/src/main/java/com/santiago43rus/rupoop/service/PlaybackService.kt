package com.santiago43rus.rupoop.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.santiago43rus.rupoop.AppViewModel
import com.santiago43rus.rupoop.*

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateNotification()
        }
    }

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

        // ALWAYS start foreground service immediately to comply with Android 8.0 - 15 systems
        val initialNotif = buildFallbackNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2026, initialNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(2026, initialNotif)
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error starting initial foreground", e)
        }

        val basePlayer = AppViewModel.sharedPlayer
        if (basePlayer != null) {
            basePlayer.addListener(playerListener)
            setupMediaSession(basePlayer)
        } else {
            Log.w("PlaybackService", "sharedPlayer was null in onCreate")
        }
    }

    private fun setupMediaSession(basePlayer: Player) {
        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands().buildUpon()
                val vm = AppViewModel.instance ?: return commands.build()
                if (vm.currentVideoIndex < vm.currentVideoList.size - 1 || (!vm.isPlaylistMode && vm.relatedVideos.isNotEmpty())) {
                    commands.add(Player.COMMAND_SEEK_TO_NEXT)
                    commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (vm.currentVideoIndex > 0) {
                    commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                commands.add(Player.COMMAND_SEEK_BACK)
                commands.add(Player.COMMAND_SEEK_FORWARD)
                return commands.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                val vm = AppViewModel.instance ?: return super.isCommandAvailable(command)
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                        vm.currentVideoIndex < vm.currentVideoList.size - 1 || (!vm.isPlaylistMode && vm.relatedVideos.isNotEmpty())
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                        vm.currentVideoIndex > 0
                    Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() = seekToNextMediaItem()
            override fun seekToNextMediaItem() {
                AppViewModel.instance?.let { vm ->
                    Handler(Looper.getMainLooper()).post { vm.playNext() }
                }
            }

            override fun seekToPrevious() = seekToPreviousMediaItem()
            override fun seekToPreviousMediaItem() {
                AppViewModel.instance?.let { vm ->
                    Handler(Looper.getMainLooper()).post { vm.playPrevious() }
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

        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val vm = AppViewModel.instance
        val player = AppViewModel.sharedPlayer
        val baseSeek = (vm?.settingsManager?.doubleTapSeekDuration ?: 10) * 1000L
        when (action) {
            "PLAY" -> player?.play()
            "PAUSE" -> player?.pause()
            "NEXT" -> vm?.playNext()
            "PREVIOUS" -> vm?.playPrevious()
            "REWIND" -> player?.let { it.seekTo((it.currentPosition - baseSeek).coerceAtLeast(0L)) }
            "FAST_FORWARD" -> player?.let { it.seekTo((it.currentPosition + baseSeek).coerceAtMost(it.duration)) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): android.app.Notification {
        val basePlayer = AppViewModel.sharedPlayer ?: return buildFallbackNotification()
        val currentVideo = AppViewModel.instance?.currentVideo
        val title = currentVideo?.title ?: "Воспроизведение"
        val author = currentVideo?.author?.name ?: "Rupoop"
        val isPlaying = basePlayer.isPlaying

        val vm = AppViewModel.instance
        val hasPrevious = vm != null && vm.currentVideoIndex > 0
        val hasNext = vm != null && (vm.currentVideoIndex < vm.currentVideoList.size - 1 || (!vm.isPlaylistMode && vm.relatedVideos.isNotEmpty()))

        // Standard system drawables are fully safe and crash-free on all API levels
        val prevIcon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, android.R.drawable.ic_media_previous)
        val nextIcon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, android.R.drawable.ic_media_next)

        val prevPendingIntent = if (hasPrevious) {
            PendingIntent.getService(this, 10, Intent(this, PlaybackService::class.java).apply { action = "PREVIOUS" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }

        val rewindIntent = Intent(this, PlaybackService::class.java).apply { action = "REWIND" }
        val rewindPendingIntent = PendingIntent.getService(this, 11, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, PlaybackService::class.java).apply { action = if (isPlaying) "PAUSE" else "PLAY" }
        val playPausePendingIntent = PendingIntent.getService(this, 12, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val ffIntent = Intent(this, PlaybackService::class.java).apply { action = "FAST_FORWARD" }
        val ffPendingIntent = PendingIntent.getService(this, 13, ffIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextPendingIntent = if (hasNext) {
            PendingIntent.getService(this, 14, Intent(this, PlaybackService::class.java).apply { action = "NEXT" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }

        val prevAction = androidx.core.app.NotificationCompat.Action.Builder(prevIcon, "Previous", prevPendingIntent).build()
        val rewindAction = androidx.core.app.NotificationCompat.Action(android.R.drawable.ic_media_rew, "Rewind", rewindPendingIntent)
        val playPauseAction = androidx.core.app.NotificationCompat.Action(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
        val ffAction = androidx.core.app.NotificationCompat.Action(android.R.drawable.ic_media_ff, "Fast Forward", ffPendingIntent)
        val nextAction = androidx.core.app.NotificationCompat.Action(nextIcon, "Next", nextPendingIntent)

        val session = mediaSession ?: return buildFallbackNotification()

        val contentIntent = PendingIntent.getActivity(
            this,
            2026,
            Intent(this, com.santiago43rus.rupoop.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.core.app.NotificationCompat.Builder(this, "default_channel_id")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(author)
            .setContentIntent(contentIntent)
            .addAction(prevAction)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(ffAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 2, 4)
            )
            .setOngoing(isPlaying)
            .setSilent(true)
            .build()
    }

    private fun buildFallbackNotification(): android.app.Notification {
        return androidx.core.app.NotificationCompat.Builder(this, "default_channel_id")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Воспроизведение")
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = createNotification()
        manager?.notify(2026, notification)

        val basePlayer = AppViewModel.sharedPlayer
        val isPlaying = basePlayer?.isPlaying == true

        // Dynamically transition between foreground and background service state based on active playback
        // This avoids Android 14/15 background restrictions while maintaining the media control notification.
        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(2026, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(2026, notification)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error starting foreground in updateNotification", e)
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error stopping foreground in updateNotification", e)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        AppViewModel.sharedPlayer?.removeListener(playerListener)
        mediaSession?.release()
        super.onDestroy()
    }
}
