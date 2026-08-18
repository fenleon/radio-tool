package com.thelightphone.sdk.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks the media-stream volume (level of max) with no permissions: a
 * dynamic [AudioManager.VOLUME_CHANGED_ACTION] receiver plus direct
 * AudioManager reads. Started by [LightMediaService] so the tool never needs
 * Context access (the tool plugin bans it). Tools read [state] directly from
 * Compose to drive the volume panel.
 */
object LightVolume {
    /** Media-stream volume; [max] is 0 until the first read. */
    data class State(val level: Int, val max: Int)

    private val _state = MutableStateFlow(State(0, 0))

    /** Current media-stream volume, level of max. */
    val state: StateFlow<State> = _state

    @Volatile
    private var observing = false
    private var audioManager: AudioManager? = null
    private var receiver: BroadcastReceiver? = null

    /** Idempotent; safe to call from any process start (e.g. service onCreate). */
    fun observe(context: Context) {
        if (observing) return
        observing = true
        val audio = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = audio
        update(audio)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Constant not present in the compile SDK's android.jar — use
                // the literal, like audiobooks' VolumeChangeMonitor.
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    audioManager?.let(::update)
                }
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver!!,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun update(audio: AudioManager) {
        _state.value = State(
            level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        )
    }
}
