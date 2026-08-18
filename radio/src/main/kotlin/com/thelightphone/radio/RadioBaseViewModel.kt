package com.thelightphone.radio

import android.view.KeyEvent
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.audio.LightVolume
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Base view model for every Radio screen — owns the volume panel.
 *
 * The LP3's volume rocker arrives here first (LightKeyHandler → the top
 * screen's VM): show the in-app volume panel instantly using [LightVolume]'s
 * last-known level, then let the key fall through to LightOS, which adjusts
 * the actual media stream (the VOLUME_CHANGED_ACTION receiver in LightVolume
 * keeps the displayed level honest).
 */
abstract class RadioBaseViewModel<T> : LightViewModel<T>() {

    /** The volume panel's state (null = hidden). Hosted by every screen's root. */
    val volumePanel = MutableStateFlow<VolumePanelState?>(null)

    fun dismissVolumePanel() {
        volumePanel.value = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        ) {
            val current = LightVolume.state.value
            val newLevel = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> (current.level + 1).coerceAtMost(current.max.coerceAtLeast(1))
                else -> (current.level - 1).coerceAtLeast(0)
            }
            volumePanel.value = VolumePanelState.Media(newLevel, current.max)
            return false // let the platform adjust the media stream
        }
        return super.onKeyDown(keyCode, event)
    }
}
