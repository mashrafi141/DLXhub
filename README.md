# DLXhub for Android

**DLXhub** is a native Android multi-platform media downloader built with Kotlin and Jetpack Compose.

---

## Supported Media Platforms

| Platform | Short Code | Folder Name | Formats | Cookie Auth |
|---|---|---|---|---|
| **RedGIFs** | `RG` | `.RG_downloads` | MP4 (HD/SD), MP3 | Built-in v2 Bearer Token |
| **XXXFollow** | `XF` | `.XF_downloads` | MP4, MP3 | Direct Progressive Stream |
| **Facebook** | `FB` | `FB_downloads` | MP4 (HD/SD), MP3 | `Fb_cookies.txt` (Netscape format) |
| **Twitter / X** | `X` | `X_downloads` | MP4 (Multi-bitrate), MP3 | `X_cookies.txt` (Netscape format) |
| **YouTube** | `YT` | `YT_downloads` | MP4 (1080p, 720p, 480p, 360p), MP3 (320k, 192k, 128k) | Single videos, batch URLs, and playlists (`list=`) |

---

## Key Architecture & Features

1. **Native Kotlin Engine Layer**:
   - High-throughput streaming via OkHttpClient with smoothed real-time speed (MB/s) and ETA calculations.
   - Preserves original download engines logic, directory layout, and naming patterns.

2. **Android Foreground Service (`DownloadForegroundService`)**:
   - Ongoing system notification channel (`dlxhub_downloads`) showing real-time percentage, download speed, and remaining time.
   - Partial CPU `WakeLock` keeps background downloads running reliably without OS throttling.

3. **Storage & Scoped Storage / SAF (`StorageDestinationManager`)**:
   - Default directory: `Downloads/DLXhub/`
   - Custom folder selection via Android Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with persistable permissions.
   - Strict filename sanitization (`safe_filename`) protecting against illegal characters.

4. **Cookie Profile Manager (`CookieManager`)**:
   - 100% offline local storage in private app directory (`context.filesDir/cookies/`).
   - Standard Netscape HTTP cookie format support (`# Netscape HTTP Cookie File`).
   - One-click import via Android document picker for `Fb_cookies.txt`, `X_cookies.txt`, and `YT_cookies.txt`.

5. **Local Persistence (Room Database)**:
   - Full history persistence with media title, platform badge, file size, timestamp, and saved file URI.
   - Direct media playback and sharing intents.

6. **Share Target Integration**:
   - Direct integration with Android's system share sheet (`ACTION_SEND` with `text/plain`).
   - Share any link from browser or social media apps directly to DLXhub for immediate resolution.

7. **Aesthetic & Design**:
   - Obsidian dark canvas (`#070B14`, `#0D1527`, `#131E36`) with glowing neon accents (Cyan `#00E5FF`, Electric Blue `#2979FF`, Violet `#8B5CF6`).
   - Glassmorphism cards with subtle neon borders, animated progress indicators, and custom adaptive app icon.

---

## Build Instructions

### Standard Debug APK
```bash
gradle :app:assembleDebug
```
Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```bash
gradle :app:testDebugUnitTest
```

## Downloader architecture (final repair)

DLXhub keeps the supplied Python downloader engines as the extraction source of truth:

- `repo_audit/engines/YT_dl.py` — YouTube / yt-dlp
- `repo_audit/engines/fbX_dl.py` — Facebook + X/Twitter / yt-dlp + direct Facebook fallback
- `repo_audit/engines/XF_dl.py` — XXXFollow / yt-dlp + first-party parsing
- `repo_audit/engines/RG_dl.py` — RedGIFs v2 API

The Android runtime includes the same Python engine source under `app/src/main/python/` and runs the yt-dlp extraction layer through Chaquopy. Kotlin handles queueing, background execution, SAF storage, byte streaming, and UI state. FFmpegKit is bundled for real MP4 muxing and MP3 encoding.

### GitHub releases

Push a tag such as `v1.0.0` to trigger `.github/workflows/build-apk.yml`. The workflow builds the APK and attaches it to a GitHub Release. If release keystore secrets are not configured, the Gradle release variant uses the project debug signing key as a personal-distribution fallback. For production/Play signing, configure `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` secrets/variables.
