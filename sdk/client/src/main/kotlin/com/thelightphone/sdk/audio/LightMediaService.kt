package com.thelightphone.sdk.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper

/**
 * A standard system service that hosts a MediaSession for background audio playback.
 * This is automatically added to a tool's manifest when `enableBackgroundAudio = true`
 * is set in `lighttool.toml`.
 */
@UnstableApi
class LightMediaService : MediaSessionService() {
    
    companion object {
        private const val CHANNEL_ID = "light_audio_channel"
        private const val NOTIFICATION_ID = 1001
        private var activeSession: MediaSession? = null

        /**
         * Sets the active media session for the background service.
         * Called by LightAudioPlayer when it wants to support background playback.
         */
        fun setActiveSession(session: MediaSession?) {
            activeSession = session
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Light Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background audio playback"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = activeSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeSession = null
        super.onDestroy()
    }
}
