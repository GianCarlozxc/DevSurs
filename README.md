# DevSurf Mobile

A native Android browser with DevSurf's dark workspace and branding.

**[Download the APK](https://github.com/GianCarlozxc/DevSurfsReleases/releases/latest)** · **[All releases](https://github.com/GianCarlozxc/DevSurfsReleases/releases)**

Requires **Android 10 or newer** and an up-to-date Android System WebView. Download `DevSurf.apk` to your phone, open it, and allow installation from your file manager when Android asks.

## Features

- Dark interface, bottom navigation, and animated new-tab transitions.
- Home page with upward-sliding quotes and Google, YouTube, and Wikipedia shortcuts.
- Address-bar suggestions from popular websites, bookmarks, and history. Suggestions are generated locally.
- Google, DuckDuckGo, and Bing search.
- Up to 20 tabs, with a two-column preview grid and restoration of open URLs.
- Full-page bookmarks and history, with date groups and individual removal controls.
- In-app Downloads page with progress, cancellation, and opening completed files.
- Separate download-start and successful-completion sounds, with a mute option in Settings.
- Built-in ad blocking, enabled by default, using Peter Lowe's bundled ad/tracking hostname list and limited ad-slot hiding.
- Dedicated AI Chat page with ChatGPT and Claude cards. Each opens the provider's website in a browser tab.
- In-app update checks, verified APK downloads, Android installation approval, and a relaunch attempt with notification fallback.
- Settings page, desktop-site mode, sharing, find in page, file uploads, fullscreen video, and default-browser selection.

## Build

Open this repository in Android Studio. Install Android SDK 35 and configure the SDK location in your local `local.properties` if necessary. The project uses Android Gradle Plugin 8.3.2, Gradle 8.4, and Java 17 source compatibility.

On Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The script selects Android Studio's bundled Java when available, runs `assembleDebug`, and writes `artifacts/DevSurf.apk`. It does not run tests, lint, an emulator, or the app. Alternatively, use `./gradlew assembleDebug`; its APK is in `app/build/outputs/apk/debug/`.

## Release repositories

- Android source: [GianCarlozxc/DevSurs](https://github.com/GianCarlozxc/DevSurs)
- APK releases: [GianCarlozxc/DevSurfsReleases](https://github.com/GianCarlozxc/DevSurfsReleases)

Version 1.1.0 is a **debug-signed APK for manual testing**. No automated or device tests were run, at the owner's request. Successful compilation does not establish runtime correctness. A later release using a different signing key may require uninstalling this build first, which removes its local data.

## Data and limitations

Tabs, bookmarks, history, and settings stay on the phone. Website cookies are shared among tabs. There are no cloud accounts or synchronization. Tabs restore URLs rather than navigation stacks or unfinished forms. Clearing browsing data closes tabs and clears history, cookies, site storage, and cache while retaining bookmarks and downloads.

Browsing uses Android System WebView rather than Electron. JavaScript stays enabled. SSL errors are cancelled, mixed content and third-party cookies are blocked, and websites have no native JavaScript bridge. Plain HTTP sites remain accessible. Camera, microphone, and website location permissions are unavailable. Blob/data URL downloads are unsupported.

Ad blocking works locally for known embedded ad/tracking hostnames and supported service-worker requests. Some first-party, video, cached, redirected, or otherwise unsupported ads may remain. Disable the blocker and reload if a site has trouble loading. The bundled list ships with app releases; no automatic filter updates or browsing-data reporting are performed. See [filter attribution](app/src/main/assets/adblock/NOTICE.txt).

Download sounds use the device's notification audio settings. Failed and cancelled downloads do not play the completion cue. Silent mode, Do Not Disturb, or Android background restrictions may suppress sounds.

The desktop DevSurf local account system, encrypted AI history and memory, image generation, VPN, full Ghostery filtering engine, Windows updater, and extensions are not ported. The local `desktop-source/` reference copy, generated files, SDK paths, signing keys, and IDE settings are excluded from this repository.

## In-app updates

Open **Settings → General → Check for updates**. DevSurf checks the latest stable public release in `GianCarlozxc/DevSurfsReleases` without opening GitHub in a browser. Choose **Download & update** to download with progress. Once verified, DevSurf proceeds to Android's installation approval while the update screen is open. Downloads continue if you leave; return to App updates to finish installation.

Android requires permission to install unknown apps from DevSurf and asks you to approve the package replacement. On Android 13+, the updater also offers notification permission for the reopen fallback. After an approved installation, DevSurf attempts to restart and restore saved tabs. Android's background activity policy may prevent automatic reopening; tap the update notification or app icon if that happens. Declining or cancelling installation leaves the downloaded update available for another attempt.

Install 1.1.0 manually once to obtain the updater, because 1.0.0 has no update screen. Existing browser data is retained by a normal update signed with the same key.

For each future release:

1. Increase both `versionCode` and `versionName` in `app/build.gradle`.
2. Build with the **same signing certificate** as the installed app. The current published builds use this development machine's Android debug keystore. Keep it backed up privately; never commit or upload it.
3. Publish a stable release with tag `v<versionName>` in the releases repository and attach the universal APK named **DevSurf.apk**. GitHub must expose a `sha256:` asset digest; `SHA256SUMS.txt` remains useful for manual downloads.

The updater verifies the repository URL, maximum and exact file sizes, GitHub asset SHA-256, package name, newer Android version code, matching version name, supported minimum Android version, and signing certificate before writing an Android PackageInstaller session. Cached APKs are verified again before installation. Only HTTPS metadata is accepted, and no GitHub credentials are shipped in the app. Failed verification never starts installation.
