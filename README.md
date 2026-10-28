# 🎵 Sangita — Native Android Music Experience

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-green?logo=android)](https://www.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Media3](https://img.shields.io/badge/AndroidX%20Media3-1.5.1-orange)](https://developer.android.com/media/media3)
[![Display](https://img.shields.io/badge/Display-high--refresh%20support-purple)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

**Sangita** is a native Android client for the self-hosted [Sangita music server](https://github.com/BlueDragon320/sangita). Built with modern Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and real-time Socket.IO synchronization, Sangita seamlessly bridges local playback, remote cloud streaming, and synchronized multi-device listening.

---

## 🚀 Instant Downloads

**Note:** The bundled APK predates the latest source changes, including the scrolling and seeking fixes. Build from source to try the current version. The existing binaries are retained here for convenience:

| Platform | Download Link | Description |
|:---|:---|:---|
| 📱 **Android Phone / Tablet** | [**Download Sangita-v1.0.0.apk**](./releases/Sangita-v1.0.0.apk) | Direct installable APK for Android (API 26 to 35+). Supports background playback and offline mode. |
| 💻 **Windows Desktop** | [**Download Sangita-Desktop.exe**](./releases/Sangita-Desktop.exe) | Portable, standalone Windows desktop client. Double-click to run without any installation. |

### How to Install on Android
1. Download [**`Sangita-v1.0.0.apk`**](./releases/Sangita-v1.0.0.apk) directly on your Android phone.
2. Open the downloaded file from your Notifications or Downloads folder.
3. If prompted by Android, tap **Settings** and enable **"Allow from this source"**.
4. Tap **Install** and launch Sangita!

---

## ✨ Key Features

### ⚡ Responsive browsing and playback
- Library, playlist, home and download screens use lazy lists with stable item keys.
- Artwork lookup and extraction run off the UI thread, with a small short-lived cache for repeated lookups. Playback progress updates are kept separate from the list screens.
- The app requests the highest refresh rate supported at the current screen resolution. Android and the phone's display, battery and thermal settings ultimately determine the rate; a higher refresh rate is not guaranteed.
- Dragging the player progress bar previews a position and seeks when released. The playback service permits seeking once a seekable track is loaded. For MP3 files without a seek table, Media3 can estimate a position when the stream length is known; the result may be approximate.
- AMOLED mode uses a black mini-player background. Unknown track durations are shown as a dash.

### 🔄 Real-Time Multi-Device Sync
- **Socket.IO Bidirectional Relay**: Instant synchronization between Web player, Android app, and connected devices.
- **Playback State Sync**: Shares the active device and playback state across sessions.
- **Universal Remote Control**: Control playback, scrub position, toggle shuffle/repeat, and transfer active playback between devices with one tap.

### 📶 Seamless Offline Playback & Downloads
- **Background Download Manager**: Download individual songs or entire playlists via Android DownloadManager with live progress notifications.
- **ExoPlayer Offline Cache**: Automatic local cache playback fallbacks ensure smooth music even with zero internet connectivity.

### 🎤 Synchronized Moving Lyrics
- **Real-Time Auto-Scroll**: Time-synced `.lrc` lyrics auto-center on the active vocal line.
- **Dynamic Ambient Canvas**: Animated GPU mesh gradient reflecting album artwork and color themes.
- **Split-Screen Landscape**: Immersive 2-column view tailored for docked phones and tablets.

### 📊 Listening Overview & Analytics
- 2x2 metric overview tracking total listening hours, song plays, data consumption, and top playlists.
- Live data counters with automatic storage estimation.

---

## 🛠 Tech Stack & Architecture

Sangita follows Android Clean Architecture principles with unidirectional data flow (MVI/MVVM):

```
app/src/main/java/com/sangita/app/
├── data/
│   ├── local/              # Room database, DAOs, and SharedPreferences
│   └── remote/             # Retrofit API, NetworkClient, DownloadManager
├── domain/
│   ├── model/              # Immutable domain entities (Track, Playlist, User, Lyrics)
│   └── repository/         # Repository interfaces
├── di/                     # Dependency injection (AppContainer)
├── player/                 # Media3 ExoPlayer service, AudioCacheManager, PlaybackController
├── sync/                   # Socket.IO DeviceSyncManager (Multi-device protocol)
├── lyrics/                 # Dynamic ambient mesh canvas, LRC parser
└── ui/
    ├── components/         # MiniPlayer, DeviceSheet, SettingsSheet, TrackRow
    ├── navigation/         # NavGraph, Screen routes, unified motion specs
    ├── screens/            # Home, Library, PlaylistDetail, Downloads, Player, Lyrics, Login
    └── theme/              # AMOLED dark theme (#000000), dynamic light theme, typography
```

### Libraries & Dependencies
- **UI & Toolkit**: Jetpack Compose BOM `2024.10.01`, Material 3, Navigation Compose `2.8.5`
- **Audio Engine**: AndroidX Media3 ExoPlayer `1.5.1`, MediaSessionService
- **Database**: AndroidX Room `2.6.1` with KSP compiler
- **Networking**: Retrofit `2.11.0`, OkHttp `4.12.0`, Kotlinx Serialization `1.7.3`
- **Real-Time Protocol**: Socket.IO Client `2.1.1`
- **Image Pipeline**: Coil Compose `2.7.0`
- **Async & Reactive**: Kotlin Coroutines and StateFlow

---

## 💻 Building from Source

### Prerequisites
- JDK 17.
- Android SDK Platform 35 and Android SDK Build-Tools, with the SDK license accepted.
- Android SDK Platform-Tools for installation over USB.
- Android Studio is optional: the SDK command-line tools and Gradle wrapper are sufficient.

### Build and test
```bash
git clone https://github.com/BlueDragon320/sangita-android-app.git
cd sangita-android-app

# Point ANDROID_HOME to your Android SDK directory, for example:
export ANDROID_HOME="$HOME/Android/Sdk"

# The Gradle wrapper downloads its configured Gradle version if needed.
bash gradlew testDebugUnitTest assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. For a non-debug build, run `bash gradlew assembleRelease`. The project does not configure release signing, so configure a signing key before distributing or installing a signed release.

### Install or update on a connected phone

Enable USB debugging on the phone, connect it by USB, and approve the debugging prompt. Then run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android only installs an APK as an update when its application ID and signing certificate match the existing app and its version code is compatible. If the signing keys differ, the existing app cannot be updated with that APK. Uninstalling it removes its app data, including its settings and downloaded music. Keep your signing key safe and out of Git; losing it prevents in-place updates signed with that key.

For meaningful scrolling performance checks, use a non-debug build and compare the same screen and device settings. Debug builds can add UI overhead. If the display is set to 60 Hz, Android may keep Sangita at 60 Hz even on a higher-refresh-rate screen.

---

## 📱 Device Compatibility

- **Target SDK**: Android 15 (API 35)
- **Minimum SDK**: Android 8.0 Oreo (API 26)
- **Refresh Rates**: Requests the highest supported rate at the current resolution; the actual rate depends on the device and its settings.
- **Device testing**: Recent scrolling and seeking changes were checked on a connected Xiaomi Android phone. Results can vary by device, library size, and network conditions.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
