# Implementation Plan - Radio Tool for Light Phone III

Build a "Radio" tool using the Light Phone SDK, featuring live audio streaming via Media3 ExoPlayer, monochrome styling, and necessary manifest configurations.

## User Review Required

> [!IMPORTANT]
> The Light SDK Plugin currently does not support `sharedUserId` in `lighttool.toml`. I will update the plugin to support this field so the tool can match the emulator's signature as requested.

> [!NOTE]
> I will also expose the `playbackState` in `LightAudioPlayer` to allow the tool to display "Connecting..." and "Error" statuses idiomatically.

## Proposed Changes

### SDK Plugin

#### [MODIFY] [LightToolMetadata.kt](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/plugin/src/main/kotlin/com/thelightphone/plugin/LightToolMetadata.kt)
- Add `sharedUserId` field to `LightToolMetadata` data class.
- Update `parse` method to extract `sharedUserId` from TOML.

#### [MODIFY] [ManifestGenerator.kt](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/plugin/src/main/kotlin/com/thelightphone/plugin/ManifestGenerator.kt)
- Update `render` to include `android:sharedUserId` in the `<manifest>` tag if provided.

---

### SDK Client

#### [MODIFY] [LightAudioPlayer.kt](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/sdk/client/src/main/kotlin/com/thelightphone/sdk/audio/LightAudioPlayer.kt)
- Add `playbackState` `StateFlow<Int>` to expose ExoPlayer's internal state (Buffering, Ready, Idle, Ended).

---

### Radio Tool (`tool` module)

#### [MODIFY] [lighttool.toml](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/tool/lighttool.toml)
- Set metadata: `id="com.thelightphone.app"`, `label="Radio"`, `versionName="1.0.0"`.
- Add `sharedUserId = "com.thelightphone.sdk"`.
- Ensure `android.permission.INTERNET` is present.

#### [MODIFY] [build.gradle.kts](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/tool/build.gradle.kts)
- Add `androidx.media3:media3-exoplayer` and `androidx.media3:media3-common` dependencies.

#### [NEW] [HomeScreen.kt](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/tool/src/main/kotlin/com/thelightphone/tool/HomeScreen.kt)
- Implement monochrome Jetpack Compose UI.
- Integrate `LightAudioPlayer` with live stream support.
- Handle playback states: Stopped, Connecting, Playing Live Stream, Error.

#### [NEW] [ToolEntryPoint.kt](file:///Users/familyimac/AndroidStudioProjects/light-sdk-main/tool/src/main/kotlin/com/thelightphone/tool/ToolEntryPoint.kt)
- Move entry point to the correct package.

#### [DELETE] `tool/src/main/kotlin/com/thelightphone/sample/`
- Remove redundant sample code.

## Verification Plan

### Automated Tests
- Build the `tool` module using Gradle: `./gradlew :tool:assembleDebug` to verify manifest generation and code compilation.

### Manual Verification
- Deploy the tool to the emulator.
- Verify the "RADIO KPS" title and monochrome UI.
- Test playing the default stream URL.
- Verify status changes from "Stopped" -> "Connecting..." -> "Playing Live Stream...".
- Verify editing the URL updates the stream source.
