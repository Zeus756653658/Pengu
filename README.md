# Pengu

### Modern Video & Audio Downloader for Android

**Version:** `1.2.0`  
**Repository:** [github.com/Zeus756653658/Pengu](https://github.com/Zeus756653658/Pengu)  
**Publisher / Developer:** **Dev Rayan**  
**License:** [GNU General Public License v3.0](LICENSE) (Open Source)

[![Releases](https://img.shields.io/github/v/release/Zeus756653658/Pengu?include_prereleases&color=7C3AED&label=Release&logo=github)](https://github.com/Zeus756653658/Pengu/releases)
[![Issues](https://img.shields.io/github/issues/Zeus756653658/Pengu?logo=github)](https://github.com/Zeus756653658/Pengu/issues)

---

## 📱 About Pengu

**Pengu** is a fast, clean, and modern open-source Android media downloader and audio player built with **Kotlin** and **Jetpack Compose (Material Design 3)**. Powered by `yt-dlp`, `FFmpeg`, and `aria2c`, Pengu lets you download high-resolution videos and audio from thousands of platforms, listen in a flagship music player, and opt into early-access builds safely via the **Pengu Beta Testing Program**.

---

## ✨ Features

- **Multi-Platform Support**: Download videos and audio from YouTube, Instagram, Twitter/X, TikTok, Reddit, Facebook, Vimeo, and over 1,000+ sites supported by [yt-dlp](https://github.com/yt-dlp/yt-dlp).
- **High Resolution Downloads & Audio Extraction**: Supports up to 4K, 1080p, 720p, and 60fps video plus crisp MP3, M4A, FLAC, OPUS, OGG, and WAV audio.
- **Pengu Beta Testing Program (Open & Closed Beta)**:
  - **Open Beta & Closed Beta Enrollment**: Join Open Beta instantly or request Closed Beta access via invite code inside **Settings -> Pengu Beta Program**.
  - **Strict Release Channels (`Stable`, `Beta`, `Archived`)**: Dedicated channel switching (`Stable` vs `Beta`) that never switches your channel automatically.
  - **Verified HTTPS APK Distribution**: HTTPS-only APK downloads with automatic **SHA-256 checksum** and **APK signing certificate verification** before prompting Android's explicit package installer confirmation.
  - **Release Details, Known Bugs & History**: Inspect WhatsApp/Instagram-style Beta release details, experimental features, known bugs, full **Beta / Stable / Archived** history, and your **My Testing Program** pass.
- **Ringtone Maker**: Cut clips from any song or video with waveform preview & Fade-In/Out smoothing, then **Save**, **Set Ringtone**, **Set Contact Ringtone**, **Set Alarm Sound**, or **Set Notification** directly.
- **Pengu Music Tab, PiP Video & System Dynamic Island**: Enjoy downloaded music in **Apple Blur** or **Material You Expressive** style with **Squiggly Wave** & **Apple Slim** sliders, **LRCLIB Synchronized Lyrics**, **Hardware Equalizer + Bass Boost**, auto-queued `Prev`/`Next`/`Shuffle`/`Repeat`, **Native Picture-in-Picture (PiP)** video playback, **5-Tab Floating Pill Bottom Bar**, real `MediaSession` + `MediaStyle` lockscreen/control-center & **System Dynamic Island** support, Android 16 **Promoted Live Updates**, and **GitHub `.md` Preview Changelog Viewer**.
- **Apple-Style Quick Share Overlay & Duplicate Alert**: Share links straight into a streamlined Quick Quality Sheet with automatic normalized duplicate-link detection (**Play Now** / **Download Again**) and auto-clean titles.
- **In-App Browser Downloader & Theme Presets**: Built-in WebView with automatic cookie sync, Settings **Activity Hub** metrics, one-tap **Curated Theme Presets**, and custom Pengu header badge styles.
- **Battery & Privacy Friendly**: 100% open-source, zero ads, zero ad SDKs.

---

## 🛠️ Technology Stack

- **Language:** 100% Pure Kotlin (Coroutines, Flow, Kotlinx Serialization)
- **UI Framework:** Android Jetpack Compose + Material Design 3 (Material You)
- **Architecture:** Single Activity, Component-driven Architecture
- **Dependency Injection:** Koin
- **Networking & Verification:** OkHttp (HTTPS-only updates, SHA-256 + `PackageManager` signing certificate verification)
- **Media Engine:** [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/), [Aria2c](https://github.com/aria2/aria2)
- **Database & Storage:** Android Room SQLite + Tencent MMKV
- **Optional Self-Hosted Beta Server:** Zero-dependency Node.js server in [beta-backend/](beta-backend/README.md)

---

## 🏗️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 21
- Android SDK (API Level 35)

### Build & Test Commands
Clone the repository, run unit tests, and build Release APKs using the Gradle wrapper:

```bash
git clone https://github.com/Zeus756653658/Pengu.git
cd Pengu

# Run Android unit tests
./gradlew testGenericReleaseUnitTest

# Build Release APKs
./gradlew assembleGenericRelease
```

The compiled APKs will be located in `app/build/outputs/apk/generic/release/`.

---

## 📄 License & Open Source Attribution

Pengu is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

- **Repository:** [https://github.com/Zeus756653658/Pengu](https://github.com/Zeus756653658/Pengu)
- **Developer / Publisher:** Dev Rayan
- **Core Engines:** [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android), [FFmpeg](https://ffmpeg.org/), and [Aria2c](https://github.com/aria2/aria2).
- See [LICENSE](LICENSE) for full license details.
