# Pengu v1.2.0 — Feature Upgrade Release Notes
**Pengu — Modern Video & Audio Downloader (`1.2.0`)**

---

## 1. About
**Pengu** is a fast, clean, and modern open-source Android video and audio downloader built with **Kotlin** and **Jetpack Compose (Material 3)**. Powered by `yt-dlp`, `FFmpeg`, and `aria2c`, Pengu lets you download high-resolution videos (up to 4K), extract crisp audio, enjoy a full-featured **Pengu Music Player** with **Picture-in-Picture (PiP)** & System Dynamic Island support, create custom sounds with the built-in **Ringtone Maker**, and opt into experimental builds inside the **Pengu Beta Program** — completely free, without ads.

- **App Name:** Pengu
- **Version:** `1.2.0` (`versionCode 102000400`)
- **Package ID:** `com.devrayan.pengu`
- **Target / Min SDK:** Android 15 (`API 35`) / Android 7.0 (`API 24`)
- **Developer / Publisher:** Dev Rayan
- **Repository:** https://github.com/Zeus756653658/Pengu
- **License:** GNU General Public License v3.0 (GPL-3.0)

---

## 2. Add
- **Pengu Beta Testing Program (`BetaProgramPage`, `MyTestingProgramPage`, `BetaReleaseDetailsPage`, `BetaReleaseHistoryPage`)**:
  - **Open Beta & Closed Beta Enrollment:** Join **Open Beta** immediately with zero manual approval, or request **Closed Beta** seats via invite code & capacity controls inside **Settings -> Pengu Beta Program**.
  - **Channel Isolation (`Beta`, `Stable`, `Archived`):** Strictly separated `Stable` and `Beta` update channels with complete release history filtering (`All`, `Beta`, `Stable`, `Archived`) and manual channel reversion back to Stable without automatic silent switching.
- **Pengu Music Tab & Full-Screen Audio Player**:
  - **Main Tab Bar Music Button:** Added a dedicated **Music** tab directly inside the floating pill navigation bar between **Downloads** and **Settings** (`MusicLibraryPage`) with active Now Playing hero controls, Shuffle All, and full audio track list.
  - **Dual Player Looks:** Switch between **Apple Blur Style** and **Material You Expressive Style** featuring the rotating scalloped `PenguWavyShape` play button.
  - **Dynamic Artwork Backdrops & Codec Info:** Real-time HSV album-art **Gradient + Blurred Mesh** backdrops (`PenguColorExtractor`) with live **Codec & Bitrate badge** (e.g. `MP3 • 320 kbps`).
  - **3 Custom Seekbar Styles:** **Apple Slim**, **Squiggly Wave**, and **Classic** scrubbers (`PenguPlayerSliders`).
  - **Queue Navigation & Library Auto-Queue:** `Previous`, `Next`, `Shuffle`, `Repeat Off/All/One`, plus horizontal swipe Left/Right on artwork to skip tracks.
  - **Synchronized Lyrics (`LRCLIB` + `.lrc`):** Live timestamped lyrics view with tap-any-line-to-seek (`PenguLyricsUtil`).
  - **Hardware Equalizer & Bass Boost FX:** Built-in audio presets (`Flat`, `Bass+`, `Pop`, `Rock`, `Vocal`, `Jazz`) via `PenguAudioManager`.
- **Dedicated Ringtone Maker System (`RingtoneToolsUtil`)**:
  - Cut custom clips from any downloaded song, video, or device audio file with visual waveform highlight, live preview loop playback, Fade-In/Out smoothing, `15s`/`30s`/`40s` presets, and `192k`/`320k` MP3 export.
  - **5 Save & Apply Actions:** **Save** (Ringtones library), **Set Ringtone** (default phone ringtone), **Set Contact Ringtone** (pick a contact directly), **Set Alarm Sound**, and **Set Notification**.
- **Floating Pill Tab Bar, PiP Video & System Dynamic Island**:
  - **Floating Pill Dock (`PenguBottomBar`):** `CircleShape` bottom capsule with a spring-sliding active pill indicator and expanding active tab label across all 5 top-level tabs, paired with a swipe-to-skip `PenguMiniPlayerBar`.
  - **Picture-in-Picture (PiP) Support:** Declared native Android PiP (`supportsPictureInPicture="true"`) on `MainActivity` + Play/Pause PiP action and auto-enter when leaving active video playback.
  - **System Media Player & Live Updates (`MediaPlaybackService`):** Real Android `MediaSession` + `Notification.MediaStyle` exposing artwork, scrubbable lockscreen/Control Center seekbars, OEM System Island cards, and Android 16 `POST_PROMOTED_NOTIFICATIONS` Live Updates (`PenguIslandOverlayService` optional in Settings).
- **GitHub `.md` Preview Changelog Viewer & Star Prompt**:
  - Added an in-app **Changelog** viewer (`ChangelogPage`) accessible from Settings, About, Auto-Update, and Navigation Drawer, designed in **GitHub `.md` preview** style with section filters (`All 1.2.0`, `1.3.0 Plan`, `Add & Fix`, `Compare`, `CHANGELOG.md`, `GitHub Releases`).
  - Added a non-intrusive **Rate / Star Pengu on GitHub** prompt (`PenguRateManager` + `RatePenguDialog`) shown at most **2 times per week** (`72h` minimum spacing) linking straight to the GitHub repository.
- **Quick Share Overlay, Smart Duplicate Guard & Personalization**:
  - Apple-style `QuickDownloadActivity` Share overlay, normalized duplicate URL guard (`Play Now` / `Download Again`), **Clean titles** filename sanitizer, Settings Activity Hub counters, 5 **Curated Theme Presets** (`Violet`, `Apple Blue`, `Emerald`, `Rose`, `Obsidian`), **Pengu Header Badge Styles**, and waving `"Hi!"` startup mascot animation (`PenguWavingMascot`).

---

## 3. Fix
- **HTTPS-Only + SHA-256 + Signing Certificate Update Hardening**:
  - Enforced `https://` URLs across all APK downloads and Beta endpoints, verified **SHA-256** checksums + installed Pengu signing certificates before launching Android's explicit package installer confirmation (`UpdateUtil`, `UpdateDialog`, `AppUpdater`), and added CI `.sha256` / `SHA256SUMS.txt` release artifacts in `.github/workflows/android.yml`.
- **Picture-in-Picture Permission & System Registration Fix**:
  - Added missing `android:supportsPictureInPicture="true"` and full `configChanges` in `AndroidManifest.xml` so Pengu appears properly under **Android Settings -> Picture-in-picture**, and added `onUserLeaveHint` / `onPictureInPictureModeChanged` + full-bleed PiP video view inside `MediaPlayerPage`.
- **Downloads Multi-Select Delete Bar Overlap Fix**:
  - Fixed the Downloads selection bar (`1 video(s), 1 audio` + Delete button) appearing trapped underneath the floating pill tab bar by automatically sliding away `PenguBottomBar` during active Downloads multi-select and elevating the floating selection bar cleanly above system navigation insets.
- **True Full-Screen Edge-to-Edge & Display Cutout Support**:
  - Eliminated the top black cutout strip and system nav contrast washout (`values-v28/themes.xml`, `values-v29/themes.xml`, `MainActivity.kt`, `Theme.kt`), removed bottom `Scaffold` clipping (`AppEntry.kt`), pinned steady `TopAppBar` headers (`VideoListPage.kt`, `SettingsPage.kt`, `MusicLibraryPage.kt`, `ChangelogPage.kt`), and removed duplicate system-bar padding across all screens.
- **Styled Update Dialog**:
  - Upgraded `UpdateDialog.kt` to render formatted GitHub Markdown headings, bold/code spans, bullets, tables, verification progress, and verified install confirmation.
- **Critical Bottom Navigation Freeze & Downloader Stability (`v1.1.1` Carry-Forward)**:
  - Fixed bottom tabs locking up after starting a download (`DownloadDialogViewModel`), serialized `DownloaderV2` task updates via `Snapshot.withMutableSnapshot` on `Dispatchers.IO`, prevented duplicate active tasks, and hardened `AppUpdater` permissions and `FileProvider` paths.

---

## 4. 1.3.0 Plan
Planned milestones and upcoming features for **Pengu v1.3.0**:
- **Playlist & Music Library Hub:** Create custom offline audio playlists, drag-to-reorder the live playback queue drawer, and edit/cache offline `.lrc` lyrics inside Pengu Music.
- **Batch URL Queue & Smart Scheduler:** Paste or import multiple links at once with Wi-Fi-only scheduling, automatic pause/resume rules, and priority queue controls.
- **Gesture Video Player Upgrades:** Swipe gestures inside full-screen video playback for frame seek, screen brightness, and volume alongside Picture-in-Picture.
- **Encrypted Backup & Library Restore:** One-tap encrypted ZIP/JSON backup & restore for Pengu download history, playlists, and custom command templates.
- **Embedded ID3 Artwork Tagging & Subtitles:** Automatically embed cover artwork/ID3 metadata for MP3 tracks and dual-language `.srt`/`.vtt` subtitle merging.

---

## 5. Compare

| Feature / Area | Pengu `v1.1.0` / `v1.1.1` | Pengu `v1.2.0` (Current) |
| :--- | :--- | :--- |
| **Beta Testing & Security** | Basic Stable / Pre-release toggle | **Open & Closed Beta Program** + **HTTPS-only, SHA-256 & Signing Certificate Verification** + `Beta / Stable / Archived` history |
| **Music & Audio Player** | Basic waveform player with `1x–2x` speed | **Pengu Music Tab** + **Apple Blur / Material Expressive Player** (`PenguWavyShape`, 3 seekbars, Artwork Mesh, Codec badge, Queue, Synced Lyrics, Hardware EQ & Bass Boost) |
| **Navigation & Screen Layout** | 4 tabs with collapsing headers | **5-Tab Floating Pill Dock** (`Home`, `Progress`, `Downloads`, `Music`, `Settings`) + **True Full-Screen Edge-to-Edge** cutout support & selection bar clearance |
| **Video Playback & PiP** | Standard fullscreen video playback | **Native Picture-in-Picture (PiP)** window with auto-enter & Play/Pause action |
| **Audio Utility** | Trim / Video-to-MP3 tools | Dedicated **Ringtone Maker** with waveform clip preview + **Save**, **Set Ringtone**, **Set Contact Ringtone**, **Set Alarm Sound**, **Set Notification** |
| **Changelog & Community** | Raw text update dialog | **GitHub `.md` Preview Changelog Viewer** + **1.3.0 Plan** + verified `UpdateDialog` + **2x/Week Star on GitHub Prompt** |
| **System Dynamic Island** | Foreground notification only | `MediaPlaybackService` + `MediaSession` + `Notification.MediaStyle` (Lockscreen seekbar, OEM System Islands, Android 16 Live Updates, Sleep Timer, A-B Loop) |

---

## Which APK Should I Install?
- **`Pengu-1.2.0-generic-arm64-v8a.apk`** — Best for most modern Android phones (64-bit ARM, smallest recommended build).
- **`Pengu-1.2.0-generic-armeabi-v7a.apk`** — For older 32-bit Android devices.
- **`Pengu-1.2.0-generic-universal.apk`** — Works on all Android CPU architectures.
- **`Pengu-1.2.0-generic-x86_64.apk` / `x86.apk`** — For Android emulators and x86 tablets.
