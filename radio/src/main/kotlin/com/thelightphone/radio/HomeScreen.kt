// Initial build design compiled by Rob Ashcroft, August 2026
package com.thelightphone.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data model for a radio station.
 * Marked as @Serializable to allow easy saving/loading to JSON files.
 */
@Serializable
data class Station(val name: String, val url: String)

/**
 * The core logic for the Radio tool.
 * Handles audio playback via Media3, persistence of station data, and screen navigation.
 */
class RadioViewModel(
    filesDir: File,
    private val sealedActivity: SealedLightActivity
) : LightViewModel<Unit>() {
    // SDK provided audio player wrapper
    private val audio = DefaultLightAudio(sealedActivity)
    private val player: LightAudioPlayer = audio.newPlayer()
    
    // File paths for local persistence
    private val stationsFile = File(filesDir, "stations.json")
    private val recentPlayedFile = File(filesDir, "recent_played.json")
    private val lastPlayedFile = File(filesDir, "last_played.json")

    private var currentScreen: SimpleLightScreen<Unit>? = null
    
    // Observable state for the UI
    val streamUrl = MutableStateFlow("https://stream.radiokps.nz/")
    val stationName = MutableStateFlow("Radio")
    val stations = MutableStateFlow<List<Station>>(emptyList())
    val recentStations = MutableStateFlow<List<Station>>(emptyList())
    
    // Playback state forwarded from the SDK player
    val isPlaying = player.isPlaying
    val playbackState = player.playbackState
    val error = player.error
    val isFavourite = MutableStateFlow(false)

    init {
        // Initial setup: load saved data and find what we were playing last
        loadStations()
        loadRecentStations()
        loadLastPlayed()
        updateFavouriteState()
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        currentScreen = screen
        // Always refresh favourite status when returning to home
        updateFavouriteState()
    }

    /** Updates the boolean state used to show a solid or outline star. */
    private fun updateFavouriteState() {
        isFavourite.value = stations.value.any { it.url == streamUrl.value }
    }

    /** Loads the list of user-favorited stations from disk. */
    private fun loadStations() {
        if (stationsFile.exists()) {
            try {
                val json = stationsFile.readText()
                stations.value = Json.decodeFromString(json)
            } catch (e: Exception) {
                android.util.Log.e("RadioViewModel", "Failed to load favourites", e)
            }
        }
    }

    /** Loads the history of played stations from disk. */
    private fun loadRecentStations() {
        if (recentPlayedFile.exists()) {
            try {
                val json = recentPlayedFile.readText()
                recentStations.value = Json.decodeFromString(json)
            } catch (e: Exception) {
                android.util.Log.e("RadioViewModel", "Failed to load recent", e)
            }
        }
    }

    /** Loads the station metadata that was active when the app was last closed. */
    private fun loadLastPlayed() {
        if (lastPlayedFile.exists()) {
            try {
                val json = lastPlayedFile.readText()
                val station = Json.decodeFromString<Station>(json)
                stationName.value = station.name
                streamUrl.value = station.url
            } catch (e: Exception) {
                android.util.Log.e("RadioViewModel", "Failed to load last played", e)
            }
        }
    }

    /** Saves current favorites to disk. */
    private fun saveStations() {
        try {
            val json = Json.encodeToString(stations.value)
            stationsFile.writeText(json)
        } catch (e: Exception) {}
    }

    /** Saves play history to disk. */
    private fun saveRecentStations() {
        try {
            val json = Json.encodeToString(recentStations.value)
            recentPlayedFile.writeText(json)
        } catch (e: Exception) {}
    }

    /** Saves currently active station metadata to disk. */
    private fun saveLastPlayed() {
        try {
            val station = Station(stationName.value, streamUrl.value)
            val json = Json.encodeToString(station)
            lastPlayedFile.writeText(json)
        } catch (e: Exception) {}
    }

    /** Adds a station to the top of the history list, maintaining a max of 10 items. */
    private fun addToRecent(station: Station) {
        val newList = recentStations.value.toMutableList()
        newList.removeAll { it.url == station.url }
        newList.add(0, station)
        if (newList.size > 10) {
            newList.removeAt(newList.size - 1)
        }
        recentStations.value = newList
        saveRecentStations()
    }

    /** Toggles playback of the current station URL. */
    fun togglePlayback() {
        if (isPlaying.value) {
            player.stop()
        } else {
            val station = Station(stationName.value, streamUrl.value)
            val item = LightAudioItem(
                source = LightAudioSource.UrlSource(station.url),
                metadata = LightMediaMetadata(title = station.name)
            )
            player.setMediaQueue(listOf(item))
            player.play()
            saveLastPlayed()
            addToRecent(station)
            updateFavouriteState()
        }
    }

    /** Adds or removes the current station from the user's curated Favorites list. */
    fun toggleFavourite() {
        val currentStation = Station(stationName.value, streamUrl.value)
        val newList = stations.value.toMutableList()
        val alreadyFavourite = newList.any { it.url == currentStation.url }
        
        if (alreadyFavourite) {
            newList.removeAll { it.url == currentStation.url }
        } else {
            newList.add(0, currentStation)
        }
        
        stations.value = newList
        saveStations()
        updateFavouriteState()
    }

    /** Transitions the player to a new station and starts playback immediately. */
    fun playStation(station: Station) {
        android.util.Log.d("RadioViewModel", "Playing: ${station.name} | URL: ${station.url}")
        stationName.value = station.name
        streamUrl.value = station.url
        
        val item = LightAudioItem(
            source = LightAudioSource.UrlSource(station.url),
            metadata = LightMediaMetadata(title = station.name)
        )
        player.setMediaQueue(listOf(item))
        player.play()
        saveLastPlayed()
        addToRecent(station)
        updateFavouriteState()
    }

    /** Navigation handlers for sub-screens */
    
    fun openSearch() {
        currentScreen?.navigateTo({ SearchScreen(it) }) { selectedStation ->
            selectedStation?.let {
                playStation(it)
            }
        }
    }

    fun openLibrary() {
        currentScreen?.navigateTo({ LibraryScreen(it) }) { selectedStation ->
            selectedStation?.let { playStation(it) }
        }
    }

    fun openAddStation() {
        currentScreen?.navigateTo({ AddStationScreen(it) }) { selectedStation ->
            selectedStation?.let {
                playStation(it)
            }
        }
    }

    fun openBluetooth() {
        viewModelScope.launch {
            // This relies on the LightOS server implementing this custom bridge method.
            // On early SDK builds for physical hardware, this may not trigger an action yet.
            callRemoteServiceMethod(LightServiceMethod.OpenBluetoothSettings, Unit)
        }
    }

    override fun onCleared() {
        // Clean up resources when the app is fully closed
        player.release()
        super.onCleared()
    }
}

/**
 * The main "Now Playing" screen of the Radio tool.
 * Annotated with @InitialScreen so the SDK knows to launch this first.
 */
@InitialScreen
class HomeScreen(private val sealedActivity: SealedLightActivity) : LightScreen<Unit, RadioViewModel>(sealedActivity) {

    override val viewModelClass: Class<RadioViewModel> = RadioViewModel::class.java

    override fun createViewModel(): RadioViewModel = RadioViewModel(lightContext.filesDir, sealedActivity)

    @Composable
    override fun Content() {
        // Collect observable state from the ViewModel
        val name by viewModel.stationName.collectAsState()
        val playing by viewModel.isPlaying.collectAsState()
        val state by viewModel.playbackState.collectAsState()
        val error by viewModel.error.collectAsState()
        val isFavourite by viewModel.isFavourite.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Derive user-friendly status text from ExoPlayer states
        val statusText = when {
            error != null -> "Error loading stream"
            playing && state == Player.STATE_READY -> "Playing Live Stream..."
            state == Player.STATE_BUFFERING -> "Connecting..."
            else -> "Stopped"
        }

        // Apply the standard Light Phone theme (follows system-wide Light/Dark mode)
        LightTheme(colors = themeColors) {
            val colors = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                // Top Bar with standard back button and tool title
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { minimize() }),
                    center = LightTopBarCenter.Text("RADIO")
                )

                // Main "Now Playing" area centered on screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Station Title Row with Favourite Star
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LightText(
                            text = name,
                            variant = LightTextVariant.Heading,
                            align = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .lightClickable { viewModel.toggleFavourite() }
                        ) {
                            com.thelightphone.sdk.ui.LightIcon(
                                icon = if (isFavourite) LightIcons.STAR else LightIcons.STAR_OUTLINE,
                                size = 1.5f
                            )
                        }
                    }

                    // Playback Status Indicator
                    LightText(
                        text = statusText,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Large Center Play/Stop Button
                    Box(
                        modifier = Modifier
                            .lightClickable { viewModel.togglePlayback() }
                            .padding(16.dp)
                    ) {
                        com.thelightphone.sdk.ui.LightIcon(
                            icon = if (playing) LightIcons.STOP else LightIcons.PLAY,
                            size = 2.5f
                        )
                    }
                }

                // Standard LightOS Bottom Navigation Bar
                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(LightIcons.SEARCH, onClick = viewModel::openSearch),
                        LightBarButton.LightIcon(LightIcons.ADD, onClick = viewModel::openAddStation),
                        LightBarButton.LightIcon(LightIcons.LIST, onClick = viewModel::openLibrary),
                        LightBarButton.LightIcon(LightIcons.BLUETOOTH, onClick = viewModel::openBluetooth)
                    )
                )
            }
        }
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun PreviewHomeScreenDark() {
    LightTheme(colors = LightThemeColors.Dark) {
        PreviewContent()
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun PreviewHomeScreenLight() {
    LightTheme(colors = LightThemeColors.Light) {
        PreviewContent()
    }
}

@Composable
private fun PreviewContent() {
    val colors = LightThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = {}),
            center = LightTopBarCenter.Text("RADIO")
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                LightText(
                    text = "Radio",
                    variant = LightTextVariant.Heading,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
                
                com.thelightphone.sdk.ui.LightIcon(
                    icon = LightIcons.STAR_OUTLINE,
                    size = 1.5f,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            LightText(
                text = "Stopped",
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            com.thelightphone.sdk.ui.LightIcon(
                icon = LightIcons.PLAY,
                size = 2.5f
            )
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(LightIcons.SEARCH, onClick = {}),
                LightBarButton.LightIcon(LightIcons.ADD, onClick = {}),
                LightBarButton.LightIcon(LightIcons.LIST, onClick = {}),
                LightBarButton.LightIcon(LightIcons.BLUETOOTH, onClick = {})
            )
        )
    }
}
