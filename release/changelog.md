# Pengu v1.0.0-beta.1 — Release Notes

## Title
**Pengu — Modern Video & Audio Downloader (`1.0.0-beta.1`)**

---

## About
**Pengu** is a fast, clean, and modern open-source Android video and audio downloader built with **Kotlin** and **Jetpack Compose (Material 3)**. Powered by `yt-dlp`, `FFmpeg`, and `aria2c`, Pengu lets you download high-resolution videos (up to 4K) and extract crisp audio (MP3, M4A, FLAC, OPUS, OGG, WAV) from 1,000+ supported platforms with full metadata & thumbnail embedding — completely free, without ads or trackers.

- **App Name:** Pengu
- **Version:** `1.0.0-beta.1`
- **Package ID:** `com.devrayan.pengu`
- **Developer / Publisher:** Dev Rayan
- **Repository:** https://github.com/Zeus756653658/Pengu
- **License:** GNU General Public License v3.0 (GPL-3.0)

---

## Changelog (`1.0.0-beta.1`)
- **Complete VidSnap-Style UI Experience**:
  - **Home Hub & Quick Sheet**: Gradient hero download hub (`Download anything, anytime.`), Video/MP3 **Quick Quality Picker** sheet (4K / 1080p / 720p / 480p / 360p & MP3 320k / M4A / FLAC / OPUS), quick Browser & Tools strip, supported platforms bar, and **Recent Downloads** preview cards.
  - **Live Progress Tracker**: Dedicated `Progress` tab with `Active` / `Completed` tabs, live download speed hero banner, circular progress ring, pause/cancel/resume controls, and custom commands access.
  - **Upgraded Downloads Library**: Segmented `All | Videos | MP3` pill tabs, live device `Storage used` indicator bar, and one-tap playback inside the built-in player.
- **Built-in "Now Playing" Video & MP3 Player**: Native video surface & audio player with animated waveform visualizer, seekbar, `-10s / +10s` skips, playback speed toggle (`1x / 1.25x / 1.5x / 2x`), Share, Trim/MP3 shortcut, and delete confirmation.
- **In-App Browser Downloader**: Full WebView browser with quick platform bookmarks, automatic WebView cookies sync for login-gated reels/stories, and a floating **Download Page** button.
- **Offline FFmpeg Media Tools**: Built-in offline **Video to MP3** converter and **Video/Audio Trimmer** powered by Pengu's bundled `FFmpeg` library.
- **Smart Clipboard Link Auto-Detect & Splash**: Automatically detects copied video/audio links with a one-tap floating download banner on launch/resume, paired with a smooth animated Pengu splash intro.
- **Custom Pengu Branding**: Official penguin launcher icon across all screen densities (`mdpi` to `xxxhdpi`), matching status-bar notification icon, and unified `Pengu` app naming across all builds.
- **Upgraded Auto-Update System**:
  - Auto-update enabled by default and connected directly to `Zeus756653658/Pengu` GitHub Releases.
  - Smart version detection (supports both release title and tag fallback) and pre-release/stable channel tracking.
  - Automatic ABI matching (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) with `universal` APK fallback.
  - Safe `.tmp` download handling and Android 8+ install permission flow.
- **Clean Release Packaging**: Removed `-debug` / `.debug` suffixes for clean release APK naming and `com.devrayan.pengu` package identity.

---

## Which APK Should I Install?
- **`Pengu-1.0.0-beta.1-generic-arm64-v8a.apk`** — Best for most modern Android phones (64-bit ARM, smallest recommended build).
- **`Pengu-1.0.0-beta.1-generic-armeabi-v7a.apk`** — For older 32-bit Android devices.
- **`Pengu-1.0.0-beta.1-generic-universal.apk`** — Works on all Android CPU architectures.
- **`Pengu-1.0.0-beta.1-generic-x86_64.apk` / `x86.apk`** — For Android emulators and x86 tablets.
