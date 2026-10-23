# 🎵 Sangita — Native Android Music Experience

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-green?logo=android)](https://www.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Media3](https://img.shields.io/badge/AndroidX%20Media3-1.5.1-orange)](https://developer.android.com/media/media3)
[![Display](https://img.shields.io/badge/Display-120%20FPS%20Fluid%20Motion-purple)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

**Sangita** is an audiophile-grade, cloud-powered native Android music streaming client designed from the ground up for high-refresh-rate displays (90Hz / 120Hz / 144Hz). Built with modern Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and real-time Socket.IO synchronization, Sangita seamlessly bridges local playback, remote cloud streaming, and synchronized multi-device listening.

---

## 🚀 Instant Downloads

Get started immediately with pre-built binaries:

| Platform | Download Link | Description |
|:---|:---|:---|
| 📱 **Android Phone / Tablet** | [**Download Sangita-v1.0.0.apk**](./releases/Sangita-v1.0.0.apk) | Direct installable APK for Android (API 26 to 35+). Supports 120Hz displays, background playback, and offline mode. |
| 💻 **Windows Desktop** | [**Download Sangita-Desktop.exe**](./releases/Sangita-Desktop.exe) | Portable, standalone Windows desktop client. Double-click to run without any installation. |

### How to Install on Android
1. Download [**`Sangita-v1.0.0.apk`**](./releases/Sangita-v1.0.0.apk) directly on your Android phone.
2. Open the downloaded file from your Notifications or Downloads folder.
3. If prompted by Android, tap **Settings** and enable **"Allow from this source"**.
4. Tap **Install** and launch Sangita!

---

## ✨ Key Features

### ⚡ 120 FPS Navigation & Motion Architecture
- **Unified Material 3 Spatial Navigation**: Synchronized lateral enter/exit slides (`0.25f` entering, `-0.15f` exiting) with `FastOutSlowInEasing`.
- **Zero Black Dip**: Overlapping alpha transitions with `initialAlpha = 0.3f` eliminate mid-transition window black flashes.
- **GPU Hardware Layer Isolation**: Offscreen compositing (`CompositingStrategy.Offscreen`) on all root screens isolates complex UI trees into GPU texture buffers for first-frame hitch elimination.
- **Android 14 & 15 Predictive Back**: Native gesture tracking via `enableOnBackInvokedCallback` allows seamless edge-swipe back navigation.

### 🔄 Real-Time Multi-Device Sync
- **Socket.IO Bidirectional Relay**: Instant synchronization between Web player, Android app, and connected devices.
- **In-Memory RAM Caching**: Zero-latency playback state sync across active sessions.
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
│   ├── local/              # Room Database, DAOs, UserPreferences (DataStore)
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
- **Async & Reactive**: Kotlin Coroutines & StateFlow `2.0.21`

---

## 💻 Building from Source

### Prerequisites
- **JDK 17** (Eclipse Adoptium or Oracle OpenJDK 17+)
- **Android Studio** Ladybug (2024.2.1+) or Koala
- **Android SDK** API 35 (Android 15) with platform-tools installed

### Build Steps
```bash
# 1. Clone repository
git clone https://github.com/bluedragon320/sangita-android-app.git
cd sangita-android-app

# 2. Check Kotlin compilation
./gradlew compileDebugKotlin

# 3. Run unit tests
./gradlew testDebugUnitTest

# 4. Assemble Debug APK
./gradlew assembleDebug

# The built APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Install to Connected Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Device Compatibility

- **Target SDK**: Android 15 (API 35)
- **Minimum SDK**: Android 8.0 Oreo (API 26)
- **Refresh Rates**: Optimized for 60Hz, 90Hz, 120Hz, and 144Hz AMOLED / OLED displays.
- **Tested Hardware**: Verified on POCO F7 (Snapdragon 8s Gen 4 / HyperOS), Xiaomi, Pixel, Samsung Galaxy devices.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
