# Pengu

### Modern Video & Audio Downloader for Android

**Version:** `1.1.0`  
**Repository:** [github.com/Zeus756653658/Pengu](https://github.com/Zeus756653658/Pengu)  
**Publisher / Developer:** **Dev Rayan**  
**License:** [GNU General Public License v3.0](LICENSE) (Open Source)

[![Releases](https://img.shields.io/github/v/release/Zeus756653658/Pengu?include_prereleases&color=7C3AED&label=Release&logo=github)](https://github.com/Zeus756653658/Pengu/releases)
[![Issues](https://img.shields.io/github/issues/Zeus756653658/Pengu?logo=github)](https://github.com/Zeus756653658/Pengu/issues)

---

## 📱 About Pengu

**Pengu** is a fast, beautiful, and feature-rich Android media downloader built from the ground up using **Kotlin** and **Jetpack Compose (Material Design 3)**. Powered by `yt-dlp` and `FFmpeg`, Pengu allows users to easily download video and audio from thousands of supported websites with full quality customization.

---

## ✨ Features

- **Multi-Platform Support**: Download videos and audio from YouTube, Instagram, Twitter/X, TikTok, Reddit, Facebook, Vimeo, and over 1,000+ sites supported by [yt-dlp](https://github.com/yt-dlp/yt-dlp).
- **High Resolution Downloads**: Supports up to 4K, 1080p, 720p, and 60fps video downloads.
- **Audio Extraction**: Extract audio into MP3, M4A, FLAC, OPUS, OGG, or WAV formats.
- **Built-in "Now Playing" Player**: Watch downloaded videos and listen to MP3 tracks with an animated audio waveform, playback speed control, seekbar, sharing, and quick trim/convert access.
- **In-App Browser Downloader**: Browse YouTube, Instagram, TikTok, Facebook, X, and SoundCloud directly inside Pengu with automatic WebView login cookie sync and one-tap Page Download.
- **Offline FFmpeg Media Tools**: Convert local videos to MP3 (128k / 192k / 320k) or trim videos and audio clips completely offline without internet access.
- **Smart Clipboard Auto-Detect & Live Progress**: Floating one-tap banner detects copied media links on app focus, paired with a live download progress queue, storage bar, and Recent Downloads hub.
- **Metadata & Artwork Embedding**: Automatically embed artist names, album info, subtitles, and high-quality cover art/thumbnails into audio and video files.
- **Fast Multi-Threaded Downloads**: Built-in [aria2c](https://github.com/aria2/aria2) external downloader for maximum download speeds.
- **Playlist & Batch Downloading**: Download entire playlists or batches with a single tap.
- **Custom yt-dlp Command Templates**: Advanced users can create and execute custom yt-dlp command templates with customizable arguments.
- **Material You Dynamic Theming**: Clean, modern Material Design 3 interface that dynamically adapts to your device's wallpaper and theme colors.
- **Battery & Privacy Friendly**: 100% open-source, no trackers, no ads, no telemetry.

---

## 🛠️ Technology Stack

- **Language:** 100% Pure Kotlin (Coroutines, Flow, Serialization)
- **UI Framework:** Android Jetpack Compose + Material Design 3 (Material You)
- **Architecture:** Single Activity, Component-driven Architecture
- **Dependency Injection:** Koin
- **Media Engine:** [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/), [Aria2c](https://github.com/aria2/aria2)
- **Database:** Android Room SQLite Database
- **Storage & Preferences:** Tencent MMKV

---

## 🏗️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 21
- Android SDK (API Level 35)

### Build Commands
Clone the repository and build the APK using the Gradle wrapper:

```bash
git clone https://github.com/Zeus756653658/Pengu.git
cd Pengu

# Build Release APKs
./gradlew assembleGenericRelease
```

The compiled APKs will be located in `app/build/outputs/apk/generic/release/` (and exported to `Desktop/Pengu_APKs/`).

---

## 📄 License & Open Source Attribution

Pengu is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

- **Repository:** [https://github.com/Zeus756653658/Pengu](https://github.com/Zeus756653658/Pengu)
- **Developer / Publisher:** Dev Rayan
- **Core Engines:** [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android), [FFmpeg](https://ffmpeg.org/), and [Aria2c](https://github.com/aria2/aria2).
- See [LICENSE](LICENSE) for full license details.
