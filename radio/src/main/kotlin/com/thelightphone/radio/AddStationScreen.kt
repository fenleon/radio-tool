// Initial build design compiled by Rob Ashcroft, August 2026
package com.thelightphone.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import com.thelightphone.sdk.ui.LightScrollView
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * logic for the manual station entry screen.
 */
class AddStationViewModel : LightViewModel<Station?>() {
    // Input field state
    val name = MutableStateFlow("")
    val url = MutableStateFlow("")
    
    private var currentScreen: SimpleLightScreen<Station?>? = null

    override fun onScreenShow(screen: SimpleLightScreen<Station?>) {
        currentScreen = screen
    }

    /**
     * Validates and returns the new station data to the caller (HomeScreen).
     */
    fun save() {
        val stationName = name.value.ifBlank { "Untitled Station" }
        val streamUrl = url.value
        if (streamUrl.isNotBlank()) {
            // Deliver the result and navigate back
            currentScreen?.goBack(Station(stationName, streamUrl))
        }
    }
}

/**
 * Screen for manually entering a radio station name and its stream URL.
 */
class AddStationScreen(private val sealedActivity: SealedLightActivity) : LightScreen<Station?, AddStationViewModel>(sealedActivity) {
    override val viewModelClass = AddStationViewModel::class.java
    override fun createViewModel() = AddStationViewModel()

    @Composable
    override fun Content() {
        val stationName by viewModel.name.collectAsState()
        val streamUrl by viewModel.url.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            val colors = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                // Simple header with back navigation
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("ADD STATION")
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Input for station name
                        OutlinedTextField(
                            value = stationName,
                            onValueChange = { viewModel.name.value = it },
                            label = { LightText("Station Name", variant = LightTextVariant.Detail) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = outlinedTextFieldColors(colors)
                        )

                        // Input for stream URL
                        OutlinedTextField(
                            value = streamUrl,
                            onValueChange = { viewModel.url.value = it },
                            label = { LightText("Stream URL", variant = LightTextVariant.Detail) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            colors = outlinedTextFieldColors(colors)
                        )

                        // Primary action button (Play/Save)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { viewModel.save() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                com.thelightphone.sdk.ui.LightIcon(
                                    icon = LightIcons.PLAY,
                                    size = 2f,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LightText("PLAY STATION", variant = LightTextVariant.Button)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Helper to apply consistent Light Phone styling to the Material OutlinedTextField. */
    @Composable
    private fun outlinedTextFieldColors(colors: com.thelightphone.sdk.ui.LightColors) = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.content,
        unfocusedTextColor = colors.content,
        focusedBorderColor = colors.content,
        unfocusedBorderColor = colors.contentSecondary,
        focusedLabelColor = colors.content,
        unfocusedLabelColor = colors.contentSecondary,
        cursorColor = colors.content
    )
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun PreviewAddStationScreen() {
    LightTheme(colors = LightThemeColors.Dark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = {}),
                center = LightTopBarCenter.Text("ADD STATION")
            )

            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { LightText("Station Name", variant = LightTextVariant.Detail) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { LightText("Stream URL", variant = LightTextVariant.Detail) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = Color.White
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.thelightphone.sdk.ui.LightIcon(
                            icon = LightIcons.PLAY,
                            size = 2f,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LightText("PLAY STATION", variant = LightTextVariant.Button)
                    }
                }
            }
        }
    }
}
