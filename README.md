# WebViewShell

A clean, no-nonsense **Android WebView app template**. Point it at any website and
you get an app that feels native: no URL bar, no browser menu, no "open in Chrome"
hand-offs — just the site, edge to edge.

Change **one line**, build, and it's your app.

```xml
<!-- app/src/main/res/values/app_config.xml -->
<string name="start_url" translatable="false">https://your-site.com</string>
```

<p align="left">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-green">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-green">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="no sdk" src="https://img.shields.io/badge/third--party%20SDKs-0-brightgreen">
</p>

---

## Why this exists

Most "turn my website into an app" templates are either full of ads/analytics, or
they break the moment a user plays a video, uploads a file or opens a link. This
one is a single readable Kotlin file that handles the boring-but-critical stuff
properly.

## What works out of the box

| Feature | Status |
|---|---|
| Any website (`http`, `https`, single-page apps, web apps) | ✅ |
| HTML5 video & audio, autoplay | ✅ |
| Fullscreen video (immersive, screen stays awake) | ✅ |
| Background audio (keeps playing when you leave the app) | ✅ |
| Popups / `target="_blank"` / `window.open()` | ✅ opens in the same screen |
| File uploads (`<input type="file">`, multiple files) | ✅ |
| Downloads (PDF, zip, images …) via the system download manager | ✅ |
| Camera & microphone for WebRTC (video calls, voice notes) | ✅ |
| Geolocation | ✅ |
| Pull-to-refresh, back-button history, offline screen with retry | ✅ |
| Dark mode (system), adaptive launcher icon, splash screen | ✅ |
| Browser UI (URL bar, tabs, menu buttons) | ❌ by design |
| Handing links to an external browser | ❌ by design |

Everything is configurable in `app_config.xml` — no code editing needed for the
common cases:

| Setting | Default | Meaning |
|---|---|---|
| `start_url` | `https://example.com` | The site the app opens |
| `enable_pull_to_refresh` | `true` | Pull down to reload |
| `keep_audio_playing_in_background` | `true` | Audio/video keeps playing when app is backgrounded |
| `open_non_web_links_in_other_apps` | `false` | When `false` (`tel:`, `mailto:`, `market:` …) are blocked, so the user never leaves the app |
| `show_loading_bar` | `true` | Thin progress bar |
| `allow_cleartext_traffic` | `true` | Allow plain `http://` sites |
| `enable_pinch_zoom` | `false` | Pinch-to-zoom (off feels more app-like) |

App name: `app/src/main/res/values/strings.xml` → `app_name`.

## Make it yours (3 steps)

1. `start_url` in `app/src/main/res/values/app_config.xml`
2. `app_name` in `app/src/main/res/values/strings.xml`
3. Optional: brand colour `brand` in `app/src/main/res/values/colors.xml` and
   `applicationId` / `namespace` in `app/build.gradle.kts`

## Build

### Android Studio
Open the folder, let Gradle sync, press **Run**.

### Command line
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions (no local toolchain)
Every push builds a debug APK. **Actions** → latest **Build APK** run → download
the `WebViewShell-debug-apk` artifact.

### Release build
Release is unsigned on purpose. Add your own keystore:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../my-release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
```

## Project structure

```
WebViewShell/
├── app/src/main/
│   ├── java/com/webviewshell/MainActivity.kt   # the whole app, ~400 readable lines
│   ├── res/values/app_config.xml               # <-- change the URL here
│   ├── res/values/strings.xml                  # app name, UI copy
│   ├── res/layout/activity_main.xml            # WebView + progress + error + fullscreen layer
│   └── AndroidManifest.xml                     # permissions
├── .github/workflows/build.yml                 # CI that ships the APK
└── README.md
```

## How the "stays in the app" rule works

`shouldOverrideUrlLoading` never returns `true` for web URLs, so navigation always
happens inside the WebView. Non-web schemes (`tel:`, `mailto:`, `intent:`,
`market:`) are blocked with a short toast unless you flip
`open_non_web_links_in_other_apps` to `true`. Downloads are handled by Android's
`DownloadManager` (a system service, not another browser).

## Known WebView limits (not fixable from the app)

- **Google / Facebook "Sign in with"** is blocked inside embedded WebViews by the
  providers themselves. Use email/OTP login, or Custom Tabs, for those flows.
- **DRM video** (Netflix, Prime, Spotify) needs Widevine L1 + a native player;
  WebView only plays clear HTML5 media.
- **`blob:` downloads** and file-system APIs need a JavaScript bridge.
- **Push notifications** and aggressive background sync need a native service.
- Some **payment gateways** refuse WebView user agents.

If your site depends on one of these, a WebView shell is the wrong tool and you
should use native screens (or a Trusted Web Activity).

## Security notes

- SSL errors are cancelled, never bypassed.
- `allowFileAccess` / `allowContentAccess` are off, no `@JavascriptInterface`
  bridge is exposed.
- Cleartext HTTP is enabled by default for compatibility — set
  `allow_cleartext_traffic` to `false` for a public release.

## License

[MIT](LICENSE) © 2026 johirxofficial

Not affiliated with Google. "Android" and "WebView" are trademarks of Google LLC.
