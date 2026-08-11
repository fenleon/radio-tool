package com.thelightphone.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * logic for the Tuner tool.
 * This will eventually handle microphone capture and pitch detection.
 */
class TunerViewModel : LightViewModel<Unit>() {
    val note = MutableStateFlow("---")
    val frequency = MutableStateFlow("0.0 Hz")
    val isListening = MutableStateFlow(false)

    fun toggleListening() {
        isListening.value = !isListening.value
        if (isListening.value) {
            // TODO: Start audio capture
            note.value = "A"
            frequency.value = "440.0 Hz"
        } else {
            // TODO: Stop audio capture
            note.value = "---"
            frequency.value = "0.0 Hz"
        }
    }
}

/**
 * Main screen for the Tuner tool.
 */
@InitialScreen
class HomeScreen(private val sealedActivity: SealedLightActivity) : LightScreen<Unit, TunerViewModel>(sealedActivity) {

    override val viewModelClass: Class<TunerViewModel> = TunerViewModel::class.java

    override fun createViewModel(): TunerViewModel = TunerViewModel()

    @Composable
    override fun Content() {
        val note by viewModel.note.collectAsState()
        val freq by viewModel.frequency.collectAsState()
        val listening by viewModel.isListening.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            val colors = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("TUNER")
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LightText(
                        text = note,
                        variant = LightTextVariant.Title,
                        align = TextAlign.Center
                    )

                    LightText(
                        text = freq,
                        variant = LightTextVariant.Heading,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .padding(top = 64.dp)
                            .lightClickable { viewModel.toggleListening() }
                            .padding(16.dp)
                    ) {
                        com.thelightphone.sdk.ui.LightIcon(
                            icon = if (listening) LightIcons.STOP else LightIcons.MICROPHONE,
                            size = 3f
                        )
                    }
                    
                    LightText(
                        text = if (listening) "LISTENING..." else "START TUNING",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
