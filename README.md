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

Version 1.0.0 is a **debug-signed APK for manual testing**. No automated or device tests were run, at the owner's request. Successful compilation does not establish runtime correctness. A later release using a different signing key may require uninstalling this build first, which removes its local data.

## Data and limitations

Tabs, bookmarks, history, and settings stay on the phone. Website cookies are shared among tabs. There are no cloud accounts or synchronization. Tabs restore URLs rather than navigation stacks or unfinished forms. Clearing browsing data closes tabs and clears history, cookies, site storage, and cache while retaining bookmarks and downloads.

Browsing uses Android System WebView rather than Electron. JavaScript stays enabled. SSL errors are cancelled, mixed content and third-party cookies are blocked, and websites have no native JavaScript bridge. Plain HTTP sites remain accessible. Camera, microphone, and website location permissions are unavailable. Blob/data URL downloads are unsupported.

Ad blocking works locally for known embedded ad/tracking hostnames and supported service-worker requests. Some first-party, video, cached, redirected, or otherwise unsupported ads may remain. Disable the blocker and reload if a site has trouble loading. The bundled list ships with app releases; no automatic filter updates or browsing-data reporting are performed. See [filter attribution](app/src/main/assets/adblock/NOTICE.txt).

Download sounds use the device's notification audio settings. Failed and cancelled downloads do not play the completion cue. Silent mode, Do Not Disturb, or Android background restrictions may suppress sounds.

The desktop DevSurf local account system, encrypted AI history and memory, image generation, VPN, full Ghostery filtering engine, Windows updater, and extensions are not ported. The local `desktop-source/` reference copy, generated files, SDK paths, signing keys, and IDE settings are excluded from this repository.
