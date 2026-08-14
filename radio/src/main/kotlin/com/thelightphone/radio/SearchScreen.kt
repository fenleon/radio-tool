// Initial build design compiled by Rob Ashcroft, August 2026
package com.thelightphone.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data model for the Radio Browser API response.
 * Maps JSON fields from the API to Kotlin properties.
 */
@Serializable
data class RadioBrowserStation(
    @SerialName("name") val name: String,
    @SerialName("url") val url: String,
    @SerialName("url_resolved") val urlResolved: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("tags") val tags: String? = null,
    @SerialName("codec") val codec: String? = null,
    @SerialName("bitrate") val bitrate: Int? = null
)

/**
 * logic for searching stations via the Radio Browser community API.
 */
class SearchViewModel : LightViewModel<Station?>() {
    // Ktor HTTP client for network requests
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
            })
        }
        // Identifying our tool to the API servers
        install(DefaultRequest) {
            header("User-Agent", "LightPhoneRadioTool/1.0")
        }
    }

    private var currentScreen: SimpleLightScreen<Station?>? = null
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val isSearching = MutableStateFlow(false)

    override fun onScreenShow(screen: SimpleLightScreen<Station?>) {
        currentScreen = screen
    }

    fun updateQuery(newQuery: String) {
        query.value = newQuery
    }

    /**
     * Executes the search against the Radio Browser API.
     * Uses a specific mirror (de1) for reliability and limits results to 50.
     */
    fun search() {
        val name = query.value.trim()
        if (name.length < 2) return
        
        viewModelScope.launch {
            isSearching.value = true
            try {
                // Radio Browser allows searching by name, tags, and country.
                // We use hidebroken=true to ensure we only get active streams.
                val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                val url = "https://de1.api.radio-browser.info/json/stations/search?name=$encodedName&limit=50&hidebroken=true&order=clickcount&reverse=true"
                
                android.util.Log.d("SearchViewModel", "Searching: $url")
                
                val response: List<RadioBrowserStation> = client.get(url).body()
                android.util.Log.d("SearchViewModel", "Search results for '$name': ${response.size}")
                results.value = response
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Search failed for '$name'", e)
                results.value = emptyList()
            } finally {
                isSearching.value = false
            }
        }
    }

    /** Returns the selected station metadata back to the HomeScreen. */
    fun selectStation(station: RadioBrowserStation) {
        val streamUrl = station.urlResolved?.takeIf { it.isNotBlank() } ?: station.url
        android.util.Log.d("SearchViewModel", "Selected: ${station.name} | URL: $streamUrl | Codec: ${station.codec}")
        currentScreen?.goBack(Station(station.name, streamUrl))
    }

    override fun onCleared() {
        // Ensure network client is closed
        client.close()
        super.onCleared()
    }
}

/**
 * Screen for discovering new radio stations via an online directory.
 */
class SearchScreen(private val sealedActivity: SealedLightActivity) : LightScreen<Station?, SearchViewModel>(sealedActivity) {
    override val viewModelClass = SearchViewModel::class.java
    override fun createViewModel() = SearchViewModel()

    @Composable
    override fun Content() {
        val query by viewModel.query.collectAsState()
        val results by viewModel.results.collectAsState()
        val searching by viewModel.isSearching.collectAsState()
        val focusManager = LocalFocusManager.current

        LightTheme(colors = LightThemeColors.Dark) {
            val colors = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                // Top Bar with Search action
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Find stations"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SEARCH,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.search()
                        }
                    )
                )

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    // Single-line search input
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        label = { LightText("Search stations...", variant = LightTextVariant.Detail) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.search()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.content,
                            unfocusedTextColor = colors.content,
                            focusedBorderColor = colors.content,
                            unfocusedBorderColor = colors.contentSecondary,
                            focusedLabelColor = colors.content,
                            unfocusedLabelColor = colors.contentSecondary,
                            cursorColor = colors.content
                        )
                    )

                    // User feedback during search
                    if (searching) {
                        LightText("Searching...", variant = LightTextVariant.Detail, lighten = true)
                    } else if (query.length > 2 && results.isEmpty()) {
                        LightText("No results found", variant = LightTextVariant.Detail, lighten = true)
                    }

                    // List of search results
                    LightScrollView(modifier = Modifier.weight(1f)) {
                        Column {
                            results.forEach { station ->
                                SearchResultRow(station) {
                                    viewModel.selectStation(station)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Individual search result row showing Name and stream metadata (Codec/Bitrate). */
    @Composable
    private fun SearchResultRow(station: RadioBrowserStation, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onClick)
                .padding(vertical = 12.dp)
        ) {
            LightText(text = station.name, variant = LightTextVariant.Copy)
            val details = mutableListOf<String>()
            station.country?.takeIf { it.isNotBlank() }?.let { details.add(it) }
            station.codec?.takeIf { it.isNotBlank() }?.let { details.add(it.uppercase()) }
            station.bitrate?.takeIf { it > 0 }?.let { details.add("${it}kbps") }
            
            if (details.isNotEmpty()) {
                LightText(text = details.joinToString(" • "), variant = LightTextVariant.Fine, lighten = true, maxLines = 1)
            }
        }
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun PreviewSearchScreen() {
    LightTheme(colors = LightThemeColors.Dark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeColors.Dark.background)
        ) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = {}),
                center = LightTopBarCenter.Text("Find stations"),
                rightButton = LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = {})
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = "Jazz",
                    onValueChange = {},
                    label = { LightText("Search stations...", variant = LightTextVariant.Detail) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
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

                PreviewSearchResultRow("Jazz Radio", "MP3 • 128kbps")
                PreviewSearchResultRow("Classic Jazz FM", "AAC • 64kbps")
            }
        }
    }
}

@Composable
private fun PreviewSearchResultRow(name: String, details: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        LightText(text = name, variant = LightTextVariant.Copy)
        LightText(text = details, variant = LightTextVariant.Fine, lighten = true, maxLines = 1)
    }
}
