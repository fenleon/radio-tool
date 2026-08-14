// Initial build design compiled by Rob Ashcroft, August 2026
package com.thelightphone.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.*
import com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple concrete ViewModel for text entry screens.
 */
class SimpleEntryViewModel<T> : LightViewModel<T>()

/**
 * Screen for renaming a station using the standard full-screen editor style.
 */
class RenameScreen(
    private val sealedActivity: SealedLightActivity,
    private val initialName: String
) : LightScreen<String?, SimpleEntryViewModel<String?>>(sealedActivity) {

    override val viewModelClass: Class<SimpleEntryViewModel<String?>> = SimpleEntryViewModel::class.java as Class<SimpleEntryViewModel<String?>>
    override fun createViewModel(): SimpleEntryViewModel<String?> = SimpleEntryViewModel()

    @Composable
    override fun Content() {
        val state = rememberTextFieldState(initialName)
        
        LightTheme(colors = LightThemeColors.Dark) {
            LightTextInputEditor(
                title = "Rename",
                state = state,
                keyboardOptionsFlow = MutableStateFlow(defaultKeyboardOptions()),
                onSubmit = { 
                    val trimmed = it.toString().trim()
                    if (trimmed.isNotBlank()) {
                        goBack(trimmed)
                    }
                },
                onBack = { goBack(null) },
                submitLabel = "SAVE",
                singleLine = true // Ensures no line returns allowed
            )
        }
    }
}

/**
 * Redesigned screen for adding a station URL.
 */
class AddStationUrlScreen(
    private val sealedActivity: SealedLightActivity
) : LightScreen<Station?, SimpleEntryViewModel<Station?>>(sealedActivity) {

    override val viewModelClass: Class<SimpleEntryViewModel<Station?>> = SimpleEntryViewModel::class.java as Class<SimpleEntryViewModel<Station?>>
    override fun createViewModel(): SimpleEntryViewModel<Station?> = SimpleEntryViewModel()

    @Composable
    override fun Content() {
        val state = rememberTextFieldState()
        val colors = LightThemeTokens.colors
        val scrollState = rememberScrollState()

        val onAdd = {
            val input = state.text.toString().trim()
            if (input.isNotBlank()) {
                goBack(Station("Untitled", input))
            }
        }
        
        LightTheme(colors = LightThemeColors.Dark) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }),
                        center = LightTopBarCenter.Text("Untitled")
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        // "Stream URL..." label
                        LightText(
                            text = "Stream URL...",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp())
                        )

                        // Horizontally scrollable text area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState)
                        ) {
                            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            
                            // Auto-scroll to keep cursor in view when typing
                            LaunchedEffect(state.text) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }

                            BasicText(
                                text = state.text.toString(),
                                // Switched to 'Superfine' - the size between 'Fine' and 'Micro'
                                style = LightThemeTokens.typography.superfine.copy(color = colors.content),
                                onTextLayout = { textLayout = it },
                                modifier = Modifier.width(IntrinsicSize.Max),
                                maxLines = 1,
                                softWrap = false
                            )

                            // Cursor
                            textLayout?.let { layout ->
                                val cursorPos = state.selection.min.coerceIn(0, layout.layoutInput.text.length)
                                val rect = layout.getCursorRect(cursorPos)
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                                        .width(1.5.dp)
                                        .height(with(LocalDensity.current) { rect.height.toDp() })
                                        .background(colors.content),
                                )
                            }
                        }
                        
                        // Fixed underline below the scrollable box
                        Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.content))
                    }

                    // Standard keyboard logic
                    val keyboardCallback = remember(state) {
                        object : Lp3RepeatableKeyboardCallback {
                            override fun onKeyPressed(code: Int) {}
                            override fun onSpecialKeyPressed(key: SpecialKey) {}
                            override fun onKeyReleased(code: Int) {
                                state.edit {
                                    val start = selection.min
                                    replace(start, selection.max, buildString { appendCodePoint(code) })
                                    selection = TextRange(start + 1)
                                }
                            }
                            override fun onSpecialKeyReleased(key: SpecialKey) {
                                when (key) {
                                    SpecialKey.Backspace -> state.edit {
                                        val start = selection.min
                                        if (start > 0) {
                                            delete(start - 1, start)
                                            selection = TextRange(start - 1)
                                        }
                                    }
                                    SpecialKey.Return -> onAdd()
                                    SpecialKey.Space -> state.edit {
                                        val start = selection.min
                                        replace(start, selection.max, " ")
                                        selection = TextRange(start + 1)
                                    }
                                    else -> Unit
                                }
                            }
                            override fun onKeyRepeated(code: Int) {
                                state.edit {
                                    val start = selection.min
                                    replace(start, selection.max, buildString { appendCodePoint(code) })
                                    selection = TextRange(start + 1)
                                }
                            }
                            override fun onSpecialKeyRepeated(specialKey: SpecialKey) {
                                if (specialKey == SpecialKey.Space) {
                                    state.edit {
                                        val start = selection.min
                                        replace(start, selection.max, " ")
                                        selection = TextRange(start + 1)
                                    }
                                }
                            }
                            override fun onKeyLongPressed(code: Int) {}
                            override fun onSpecialKeyLongPressed(key: SpecialKey) {
                                if (key == SpecialKey.Backspace) {
                                    state.edit {
                                        val cur = state.text.toString()
                                        val end = selection.min
                                        if (end > 0) {
                                            val start = cur.substring(0, end).trimEnd().indexOfLast { it.isWhitespace() } + 1
                                            delete(start, end)
                                            selection = TextRange(start)
                                        }
                                    }
                                }
                            }
                            override fun onSubmitWord(word: CharSequence) {
                                state.edit {
                                    val start = selection.min
                                    replace(start, selection.max, word.toString())
                                    selection = TextRange(start + word.length)
                                }
                            }
                        }
                    }

                    val keyboardViewModel = viewModel<EnQwertyLp3KeyboardViewModel<Unit>>(
                        key = "add-station-keyboard-v4",
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return EnQwertyLp3KeyboardViewModel<Unit>(
                                    keyboardCallback,
                                    keyboardOptionsFlow = MutableStateFlow(defaultKeyboardOptions()),
                                    optionsForLayout = { LayoutOptions(!it.isRootLayout) }
                                ) as T
                            }
                        }
                    )

                    LightEmbeddedLp3Keyboard(viewModel = keyboardViewModel)

                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.ADD,
                                onClick = onAdd
                            )
                        )
                    )
                }
            }
        }
    }
}
