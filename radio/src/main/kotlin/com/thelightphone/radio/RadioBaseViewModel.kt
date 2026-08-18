package com.thelightphone.radio

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.audio.LightVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Base view model for every Radio screen — owns the volume panel.
 *
 * The LP3's rocker adjusts the media stream itself and broadcasts
 * VOLUME_CHANGED_ACTION (the SDK never forwards volume keys to the screen —
 * LightActivity routes them to super). So the panel is driven by
 * [LightVolume]'s receiver: every real volume change is surfaced here.
 * [onKeyDown] remains as a fallback for devices that do deliver volume keys.
 */
abstract class RadioBaseViewModel<T> : LightViewModel<T>() {

    /** The volume panel's state (null = hidden). Hosted by every screen's root. */
    val volumePanel = MutableStateFlow<VolumePanelState?>(null)

    init {
        viewModelScope.launch {
            var last: LightVolume.State? = null
            LightVolume.state.collect { current ->
                // The first emission is the initial read (a seed, not a change).
                val lastLevel = last?.level
                if (lastLevel != null && current.level != lastLevel && current.max > 0) {
                    volumePanel.value = VolumePanelState.Media(current.level, current.max)
                }
                last = current
            }
        }
    }

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
