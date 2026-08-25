package com.thelightphone.sdk.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thelightphone.lp3Keyboard.ui.DarkKeyboardColors
import com.thelightphone.lp3Keyboard.ui.LightKeyboardColors
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardCallback
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardTheme
import com.thelightphone.lp3Keyboard.ui.LocalKeyboardColors
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardWrapper
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.sdk.ui.LightHapticFeedback
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LocalHapticsEnabled

/**
 * The LP3 keyboard, matching the real SDK's version: the keys render in a
 * fixed-height block and [additionalBottomHeight] reserves space below them
 * (the library's wrapper enforces a 36dp minimum) where [bottomBar] can sit —
 * the passes add-code editor's arrangement (keys, gap, bar inside the
 * keyboard's background).
 */
@Composable
fun LightEmbeddedLp3Keyboard(
    viewModel: Lp3KeyboardViewModel<*>,
    additionalBottomHeight: Dp = 0.dp,
    bottomBar: (@Composable () -> Unit)? = null,
    onOverlayDismissed: (() -> Unit)? = null,
    overlay: (@Composable () -> Unit)? = null,
) {
    val layout by viewModel.layoutFlow.collectAsState()
    val keyboardOptions by viewModel.keyboardOptionsFlow.collectAsState()
    val layoutOptions by viewModel.layoutOptionsFlow.collectAsState()

    // Key-press haptic, gated by the user's global haptics preference — same
    // wiring as LightTextInputEditor. Wrapped here (devices layer) so every
    // tool's embedded keyboard gets it without the tool needing a Context
    // (the tool plugin bans it).
    val hapticsEnabled = LocalHapticsEnabled.current
    val context = LocalContext.current
    val fireHaptic by rememberUpdatedState {
        if (hapticsEnabled) LightHapticFeedback.click(context)
    }
    val hapticCallback = object : Lp3KeyboardCallback {
        override fun onKeyPressed(code: Int) { fireHaptic(); viewModel.onKeyPressed(code) }
        override fun onSpecialKeyPressed(key: SpecialKey) { fireHaptic(); viewModel.onSpecialKeyPressed(key) }
        override fun onKeyReleased(code: Int) = viewModel.onKeyReleased(code)
        override fun onSpecialKeyReleased(key: SpecialKey) = viewModel.onSpecialKeyReleased(key)
        override fun onKeyLongPressed(code: Int) = viewModel.onKeyLongPressed(code)
        override fun onSpecialKeyLongPressed(key: SpecialKey) = viewModel.onSpecialKeyLongPressed(key)
        override fun onSubmitWord(word: CharSequence) = viewModel.onSubmitWord(word)
        override fun onKeyCancelled(code: Int) = viewModel.onKeyCancelled(code)
    }

    val keyboardColors = if (LightThemeTokens.colors == LightThemeColors.Light) {
        LightKeyboardColors
    } else {
        DarkKeyboardColors
    }
    Lp3KeyboardTheme(keyboardColors) {
        val colors = LocalKeyboardColors.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(top = 10.dp),
        ) {
            Lp3KeyboardWrapper(
                layout = layout,
                keyboardOptions = keyboardOptions,
                layoutOptions = layoutOptions,
                callback = hapticCallback,
                swipeCallback = null,
                additionalBottomHeight = additionalBottomHeight,
                bottomBar = bottomBar,
                onOverlayDismissed = onOverlayDismissed,
                overlay = overlay,
            )
        }
    }
}
