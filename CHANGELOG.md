# Changelog

All notable changes to **Pengu** will be documented in this file.

## [1.3.0] - Planned Roadmap
### 1.3.0 Plan
- **Playlist & Music Library Hub**: Custom offline playlists, drag-to-reorder playback queue drawer, and offline `.lrc` lyrics editing/caching in Pengu Music.
- **Batch URL Queue & Smart Scheduler**: Multi-link import queue, Wi-Fi-only download scheduling, and priority ordering.
- **Gesture Video Player Upgrades**: Swipe gestures for video seek, brightness, and volume alongside Picture-in-Picture.
- **Encrypted Backup & Library Restore**: One-tap encrypted backup/restore for download index, playlists, and command templates.
- **Embedded ID3 Artwork & Bilingual Subtitles**: Cover-art ID3 tagging for MP3s and dual-language `.srt`/`.vtt` subtitle merging.

## [1.2.0] - 2026-11-28
### Feature Release Highlights
- **Structured Bug Reporting, Crash Consent & Tracking System**:
  - Added full bug reporting workflow under **Settings -> Troubleshooting -> Report a Bug** (`BugReportPage`, `MyBugReportsPage`, `BugReportDetailPage`, `BugReportRepository`, `BugReportViewModel`): 9 categories, field & character limit validation, duplicate submission protection, up to 3 validated screenshot attachments, local offline/timeout preservation with retry, live status retrieval (`Open`, `Reviewing`, `In Progress`, `Fixed`, `Rejected`, `Closed`), and developer responses.
  - Extended `CrashReportActivity` with **Send Bug Report**, **Copy Crash Log**, and **Exit**, enforcing explicit user consent and automatic diagnostic redaction (`DiagnosticRedactor`) before submission.
  - Extended `beta-backend/` (`services/bugReportService.js`, `routes/bugReports.js`, `data/bug-reports.json`) with concurrency-safe atomic JSON storage + `.bak` recovery, magic-byte attachment checks, rate limiting, tracking-token status endpoints, and an authenticated server-side Developer Bug Management Dashboard (`/admin/bug-reports`).
- **WhatsApp / Instagram Style Pengu Beta Testing Program**:
  - **Open & Closed Beta Enrollment**: Dedicated `BetaProgramPage`, `MyTestingProgramPage`, `BetaReleaseDetailsPage`, and `BetaReleaseHistoryPage` in Settings (`BETA` badges, membership pass, installed vs latest beta build timeline, known bugs, experimental features, tester eligibility checks, Open Beta instant opt-in, Closed Beta invite/capacity verification, and explicit channel switch back to Stable).
  - **Strict Release Channels (`Beta`, `Stable`, `Archived`)**: Separated Stable production builds from Beta pre-releases inside `UpdateUtil` & `BetaRepository` so users are never silently switched between channels.
  - **Verified HTTPS + SHA-256 + Signing Certificate Updates**: Enforced HTTPS-only APK downloads (`requireHttpsUrl`), automatic SHA-256 digest verification (`computeSha256` + companion `.sha256` / GitHub digest parsing + CI `SHA256SUMS.txt` generation), APK signing certificate comparison against the installed Pengu build, and explicit two-step user confirmation before opening Android's package installer (`UpdateDialog`, `AppUpdater`, `BetaReleaseDetailsPage`).
- **Pengu Music Tab & Full Audio Player**: Added a dedicated `Music` tab between `Downloads` and `Settings` (`MusicLibraryPage`), featuring **Apple Blur** & **Material You Expressive** themes (`PenguMusicPlayerView`), scalloped rotating play button (`PenguWavyShape`), blurred dynamic album-art mesh (`PenguColorExtractor`), **Apple Slim / Squiggly Wave / Classic** sliders, live **Synchronized Lyrics (`LRCLIB` & `.lrc`)** (`PenguLyricsUtil`), **Auto-Queue** (`Prev`/`Next`/`Shuffle`/`Repeat`), real-time **Hardware Equalizer & Bass Boost FX**, and swipe-to-skip Floating Mini-Player (`PenguMiniPlayerBar`).
- **Dedicated Ringtone Maker System**: Replaced general media cutter/converter tools with a focused **Ringtone Maker** (`RingtoneToolsUtil`, `MediaToolsPage`) with waveform range preview, Fade-In/Out, and 5 actions: **Save**, **Set Ringtone**, **Set Contact Ringtone**, **Set Alarm Sound**, and **Set Notification**.
- **Native Picture-in-Picture (PiP) Support**: Registered `supportsPictureInPicture="true"` on `MainActivity`, wired auto-enter PiP & Play/Pause action (`PenguPipState`), and added a PiP button in the video player (`MediaPlayerPage`).
- **Downloads Selection Bar Fix**: Elevated the Downloads multi-select delete bar (`VideoListPage`) cleanly and auto-hid `PenguBottomBar` while selecting so Delete & Select All buttons are never covered by the bottom dock.
- **True Full-Screen Edge-to-Edge, Display Cutout & Steady Headers**: Configured `shortEdges` cutout support (`values-v28/themes.xml`, `values-v29/themes.xml`, `MainActivity.kt`) and synced status/navigation bar contrast in `Theme.kt`; pinned steady headers across top tabs and eliminated double system-bar insets.
- **GitHub `.md` Preview Changelog Viewer, Update UI & Startup Wave**: Added `ChangelogPage` styled like GitHub Markdown preview (`MarkdownReleaseNotes.kt`) with offline bundled release notes + `1.3.0 Plan` + live GitHub Releases fetch, upgraded `UpdateDialog`, added **Star on GitHub** prompt (`PenguRateManager` max 2x/week), and added Pengu's waving `"Hi!"` startup animation (`PenguWavingMascot`).
- **Floating Pill Tab Bar & System Dynamic Island (`MediaSession` + Live Updates)**: Redesigned bottom navigation (`PenguBottomBar`) into a 5-tab capsule pill (`Home`, `Progress`, `Downloads`, `Music`, `Settings`), and backed lockscreen/Control Center cards, OEM System Islands, and Android 16 Promoted Live Updates via `MediaPlaybackService`.
- **Smart Duplicate Link Alert, Quick Share & Theme Presets**: Normalized URL duplicate guard (`Play Now` / `Download Again`), `CLEAN_FILENAMES`, Apple-style `QuickDownloadActivity`, Settings Activity Hub, 5 curated theme presets, and custom Pengu header badge styles.

## [1.1.1] - 2026-11-26
### Hotfix Highlights
- **Critical Navigation Fix**: Fixed bottom tab bar (`Progress`, `Downloads`, `Settings`, `Home`) becoming unresponsive after pasting a link and starting a download by resetting `DownloadDialogViewModel` sheet state properly on hide/command completion and guarding `AppEntry` backstack returns to expanded sheets only.
- **Downloader Threading & Stability**: Serialized `DownloaderV2` task state updates via `Snapshot.withMutableSnapshot` on `Dispatchers.IO`, prevented duplicate active task collisions, fixed Home `selectedTask` cleanup bug, offloaded FFmpeg probing & file-size reads from UI composition, and added `MediaPlayer` error handling.
- **Auto-Updater Hardening**: Improved `AppUpdater` permission checks, download cancellation/reset, session dismiss tracking, GitHub `/releases/latest` fallback, `<external-files-path>` `FileProvider` support, and numeric tag release workflow triggers (`[0-9]*`).

## [1.1.0] - 2026-11-26
### Release Highlights
- **Stable Version 1.1.0**: Promoted **Pengu** to stable `1.1.0` (`versionCode 101000400`) with stable release channel auto-updates enabled by default.
- **Complete Modern UI & Media Suite**: Includes Quick Video/MP3 quality picker, Live Progress tracker, Downloads storage bar & filter tabs, Built-in "Now Playing" Video & MP3 player, In-App Browser downloader, Smart Clipboard link auto-detect, and animated startup splash.

## [1.0.0-beta.1] - 2026-11-26
### Release Highlights
- **Version 1.0.0-beta.1**: Initial clean release metadata as **Pengu**.
- **Modern UI & Core Downloader**: Vibrant gradient home hub, Quick Video/MP3 quality picker sheet, Live Progress tracker, Downloads library, In-App Browser downloader with cookie sync, auto-update updater (`Zeus756653658/Pengu`), `yt-dlp` / `FFmpeg` / `aria2c`, package `com.devrayan.pengu`, GPL-3.0.
