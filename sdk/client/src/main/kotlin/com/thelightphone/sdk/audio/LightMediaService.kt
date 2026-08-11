package com.thelightphone.sdk.audio

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * A standard system service that hosts a MediaSession for background audio playback.
 * This is automatically added to a tool's manifest when `enableBackgroundAudio = true`
 * is set in `lighttool.toml`.
 */
@UnstableApi
class LightMediaService : MediaSessionService() {
    
    companion object {
        private var activeSession: MediaSession? = null

        /**
         * Sets the active media session for the background service.
         * Called by LightAudioPlayer when it wants to support background playback.
         */
        fun setActiveSession(session: MediaSession?) {
            activeSession = session
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
