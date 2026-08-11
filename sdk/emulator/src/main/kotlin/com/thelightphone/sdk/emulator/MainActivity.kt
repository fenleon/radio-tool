package com.thelightphone.sdk.emulator

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.SessionToken
import com.thelightphone.sdk.server.LightSdkServer
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.thelightphone.sdk.server.LightSdkServer.queryEnabledClients
import com.thelightphone.sdk.server.LightSdkServer.runningAsSystemApp
import com.thelightphone.sdk.server.LightSdkServerSettings
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightModalManager
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LocalLightColors
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    val lightAudioManager get() = (application as EmulatorApplication).lightAudioManager
    private var mediaControllerFuture: ListenableFuture<androidx.media3.session.MediaController>? = null
    private var mediaController: androidx.media3.session.MediaController? = null
    private val activeMediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    private val isMediaPlaying = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!runningAsSystemApp) {
            Log.w(
                "LightEmulator",
                "WARNING: LightOS emulator is NOT running as a system app and may not work."
            )
        }
        val serverSettings = LightSdkServer.provideSdkSettings(this)
        setupMediaController()
        setContent {
            val themeColors by LightThemeController.colors.collectAsState()
            LightTheme(colors = themeColors) {
                Box(Modifier.fillMaxSize()) {
                    PrimaryUI(serverSettings, lightAudioManager)
                    val modal by LightModalManager.activeModal.collectAsState()
                    modal?.Content()
                }
            }
        }
    }

    private fun setupMediaController() {
        LightSdkServer.onMediaSessionRegistered = { packageName, active ->
            if (active) {
                connectToSession(packageName)
            } else {
                disconnectFromSession(packageName)
            }
        }
    }

    private fun connectToSession(packageName: String) {
        val sessionToken = SessionToken(this, ComponentName(packageName, "com.thelightphone.sdk.audio.LightMediaService"))
        mediaControllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                val controller = mediaControllerFuture?.get()
                mediaController = controller
                controller?.addListener(object : Player.Listener {
                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        activeMediaMetadata.value = metadata
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isMediaPlaying.value = playing
                    }
                })
                activeMediaMetadata.value = controller?.mediaMetadata
                isMediaPlaying.value = controller?.isPlaying == true
            } catch (e: Exception) {
                Log.e("LightEmulator", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun disconnectFromSession(packageName: String) {
        if (mediaController?.connectedToken?.packageName == packageName) {
            mediaController?.release()
            mediaController = null
            activeMediaMetadata.value = null
            isMediaPlaying.value = false
        }
    }

    @Composable
    private fun PrimaryUI(
        serverSettings: LightSdkServerSettings,
        lightAudioManager: LightAudioManager
    ) {
        val currentNav by EmulatorNavController.currentNav.collectAsState()
        val metadata by activeMediaMetadata.collectAsState(null)
        val isPlaying by isMediaPlaying.collectAsState(false)

        when (val navSnapshot = currentNav) {
            Nav.LockScreen -> {
                LightLockscreen(
                    metadata = metadata,
                    isPlaying = isPlaying,
                    onToggle = { if (isPlaying) mediaController?.pause() else mediaController?.play() },
                    onStop = {
                        mediaController?.stop()
                        mediaController?.release()
                        mediaController = null
                        activeMediaMetadata.value = null
                        isMediaPlaying.value = false
                    }
                ) {
                    EmulatorNavController.navigateTo(Nav.Toolbox)
                }
            }

            Nav.Toolbox -> {
                ToolList(
                    fetchExternalTools = {
                        queryEnabledClients(serverSettings).map {
                            val appInfo = it.packageInfo.applicationInfo!!
                            val label =
                                packageManager.getApplicationLabel(appInfo).toString()
                            ExternalTool(label, it.packageInfo.packageName)
                        }
                    }, launchPackage = {
                        packageManager.getLaunchIntentForPackage(it)?.let { intent ->
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            val options =
                                android.app.ActivityOptions.makeCustomAnimation(
                                    this@MainActivity,
                                    0,
                                    0
                                )
                            startActivity(intent, options.toBundle())
                        }
                    }, launchDefaultTool = {
                        when (it) {
                            DefaultTool.Settings -> EmulatorNavController.navigateTo(Nav.Settings())
                        }
                    })
            }

            is Nav.Settings -> {
                val emulatorSettingsAudio = object : EmulatorSettingsAudio {
                    override fun setRingerVolume(normalized: Float) =
                        lightAudioManager.setRingerVolume(normalized)

                    override val ringerVolume: StateFlow<Float> = lightAudioManager.ringerVolume
                }
                EmulatorSettings(serverSettings, emulatorSettingsAudio, navSnapshot) {
                    EmulatorNavController.navigateTo(Nav.Toolbox)
                }
            }
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val screenTurnedOff =
            intent.extras?.getBoolean(LightSdkServer.SCREEN_OFF_FLAG, false) == true
        if (screenTurnedOff) {
            EmulatorNavController.navigateTo(Nav.LockScreen)
        }
    }
}

private sealed class Tool(val label: String)
private class ExternalTool(label: String, val packageName: String) : Tool(label)
private sealed class DefaultTool(label: String) : Tool(label) {
    object Settings : DefaultTool("Settings")
}


private val defaultTools: List<Tool> = listOf(
    DefaultTool.Settings
)

@Composable
private fun ToolList(
    fetchExternalTools: suspend () -> List<Tool>,
    launchPackage: (String) -> Unit,
    launchDefaultTool: (DefaultTool) -> Unit
) {
    // TODO page indicator
    val toolPageSize = 6
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf(defaultTools.chunked(toolPageSize)) }
    val currentPage by remember {
        derivedStateOf {
            pages.getOrNull(currentPageIndex) ?: pages.first()
        }
    }
    LaunchedEffect(Unit) {
        val externalTools = fetchExternalTools()
        pages = (defaultTools + externalTools).chunked(toolPageSize)
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {},
                onDragStopped = { velocity ->
                    if (velocity < -200f && currentPageIndex < pages.size - 1) {
                        currentPageIndex++
                    } else if (velocity > 200f && currentPageIndex > 0) {
                        currentPageIndex--
                    }
                }
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (tool in currentPage) {
                LightText(
                    text = tool.label,
                    variant = LightTextVariant.Subtitle,
                    modifier = Modifier.clickable {
                        when (tool) {
                            is DefaultTool -> launchDefaultTool(tool)
                            is ExternalTool -> launchPackage(tool.packageName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LightLockscreen(
    metadata: MediaMetadata?,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onUnlockClicked: () -> Unit
) {
    Surface {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp())
        ) {
            Row(Modifier.fillMaxWidth()) {
                LightIcon(icon = LightIcons.WIFI, size = 1f)
                Spacer(Modifier.width(0.5f.gridUnitsAsDp()))
                LightIcon(icon = LightIcons.BLUETOOTH, size = 1f)
                Spacer(Modifier.weight(1f))
                LightIcon(icon = LightIcons.BATTERY_CHARGING, size = 1f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                var time by remember { mutableStateOf(LocalTime.now()) }

                LaunchedEffect(Unit) {
                    while (true) {
                        time = LocalTime.now()
                        delay(1000)
                    }
                }

                val formatter = remember { DateTimeFormatter.ofPattern("h:mm") }
                
                // Clock stays at a fixed bias position
                LightText(
                    time.format(formatter),
                    variant = LightTextVariant.Title,
                    modifier = Modifier.align(BiasAlignment(0f, -0.1f))
                )
                
                // Controls appear below the clock without shifting it
                if (metadata != null) {
                    Box(
                        modifier = Modifier.align(BiasAlignment(0f, 0.25f))
                    ) {
                        if (isPlaying) {
                            Box(Modifier.clickable(onClick = onToggle).padding(8.dp)) {
                                LightIcon(icon = LightIcons.PAUSE, size = 2f)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clickable(onClick = onStop).padding(8.dp)) {
                                    LightIcon(icon = LightIcons.STOP, size = 2f)
                                }
                                Spacer(Modifier.width(16.dp))
                                Box(Modifier.clickable(onClick = onToggle).padding(8.dp)) {
                                    LightIcon(icon = LightIcons.PLAY, size = 2f)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.clickable(onClick = onUnlockClicked)) {
                LightIcon(
                    icon = LightIcons.CIRCLE,
                    size = 2f
                )
            }
        }
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
fun LockScreenPreview() {
    LightTheme(colors = LightThemeColors.Dark) {
        LightLockscreen(
            metadata = null,
            isPlaying = false,
            onToggle = {},
            onStop = {}
        ) { }
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
fun LockScreenPreviewLight() {
    LightTheme(colors = LightThemeColors.Light) {
        LightLockscreen(
            metadata = null,
            isPlaying = false,
            onToggle = {},
            onStop = {}
        ) { }
    }
}
