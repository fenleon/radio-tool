# Radio Tool for Light Phone III

A minimal, high-contrast radio streaming application built with the Light Phone SDK. This tool allows users to discover, play, and curate their favorite online radio stations.

## Initial Build Design
Initial build design compiled by **Rob Ashcroft**, August 2026.

## Features

- **Live Streaming**: High-quality audio playback using Media3 ExoPlayer.
- **Background Playback**: Continues playing audio even when the tool is minimized or the screen is off.
- **Home Screen Integration**: Control playback (Play/Pause/Stop) directly from the LightOS Home screen.
- **Station Search**: Discover thousands of stations globally via the Radio Browser community API.
- **Library Management**:
    - **Favourites**: Curate a list of your most-loved stations.
    - **Recently Played**: Automatically tracks your listening history.
- **Manual Entry**: Add custom stream URLs manually.
- **Theme Support**: Seamlessly follows system-wide Light and Dark mode preferences.
- **Bluetooth Quick Access**: Shortcut to system Bluetooth settings for easy headphone connection.

## How it Works

### Audio Engine
The tool uses the `LightAudioPlayer` provided by the Light SDK, which is built on top of **AndroidX Media3**. It supports various streaming formats including MP3, AAC, and HLS (.m3u8).

### Persistence
User data is stored locally in the tool's private directory:
- `stations.json`: Stores favorited stations.
- `recent_played.json`: Stores the last 10 played stations.
- `last_played.json`: Remembers the station active when the app was closed.

### Networking
Station discovery is powered by the **Radio Browser API**. The tool uses **Ktor** for safe, asynchronous network requests and handles URL encoding to ensure robust search results.

## Implementation Notes

- **Background Service**: Background audio is enabled via `LightMediaService` and a registered `MediaSession`. This ensures the Android system prioritizes the audio process.
- **Cleartext Traffic**: The tool is configured to allow `http` connections to support older radio station servers that haven't migrated to `https`.
- **User Agent**: Network requests identify as `LightPhoneRadioTool/1.0` to prevent being blocked by streaming servers.
- **Navigation**: Uses the standard SDK `navigateTo` and `goBack(result)` patterns for a consistent LightOS experience.

## How to Test

To test the Radio tool in your local development environment:
1.  **Run the `sdk.emulator` module**: This starts the LightOS system app emulator.
2.  **Run the `radio` module**: This installs the Radio tool onto the emulator.
3.  Navigate to the Radio tool in the emulator's tool list to start listening.

## Technical Details

- **Module Name**: `:radio`
- **Package**: `com.thelightphone.radio`
- **SDK Compatibility**: Requires Light SDK v1.0.0+
- **Permissions**: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
