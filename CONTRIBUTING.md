# Contributing to Pengu

Thank you for helping improve **Pengu**, maintained by **Dev Rayan**. Pengu pairs a modern Jetpack Compose Material Design 3 interface with [yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/), and [Aria2c](https://github.com/aria2/aria2).

For issues and pull requests, please search existing issues first (including closed ones). If there are no duplicates, open a [GitHub Issue](https://github.com/Zeus756653658/Pengu/issues/new) with reproduction details.

For discussions or feedback, visit [Pengu on GitHub](https://github.com/Zeus756653658/Pengu).

---

## Bug Reports

Include your Pengu version, Android API level, the failing URL or custom `yt-dlp` command template, and exact reproduction steps in a GitHub Issue.

---

## Beta Testing & Release Verification

Pengu maintains separate release channels:
- **Stable Channel:** Production builds only (`Version.Stable`).
- **Beta Channel:** Open Beta & Closed Beta experimental builds (`-beta.*`).

Contributors modifying update or download logic must preserve:
- Strict channel separation (never automatically switching a user's selected update channel).
- **HTTPS-only** (`https://`) download enforcement.
- **SHA-256** checksum validation and `PackageManager` APK signing certificate comparison.
- Explicit user confirmation via Android's standard package installer dialog.

---

## Feature Requests

Pengu aims to remain a clean, dependable Android media downloader & player powered by `yt-dlp`. Requests that depend on unsupported extractors outside `yt-dlp` may not be accepted; advanced flags can already be tested in Pengu's **Custom Command Mode**.

---

## Pull Requests

1. Comment on the issue you plan to work on or open one first so contributors do not duplicate effort.
2. Keep existing Room, MMKV, `yt-dlp`, `FFmpeg`, and `Aria2c` architecture intact.
3. Verify your changes before submitting:
   ```bash
   ./gradlew testGenericReleaseUnitTest
   ./gradlew compileGenericReleaseKotlin
   ```

---

## Building From Source

1. Fork [Pengu](https://github.com/Zeus756653658/Pengu).
2. Open in Android Studio Ladybug / Meerkat or newer with **JDK 21** and **Android SDK API 35**.
3. Run unit tests cleanly before submitting PRs.
