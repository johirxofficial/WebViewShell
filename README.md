# WebViewShell

A clean Android **WebView app template**. Point it at any website and you get an
app that feels native: no URL bar, no browser menu, no hand-offs to Chrome — just
the site, edge to edge.

### Everything you can customise lives in one file

```
app-config.properties      <-- the only file you edit
```

URL, app name, package name, version, logo, every colour, every behaviour
toggle. Nothing else needs to change — no Kotlin, no XML.

```properties
start_url=https://your-site.com
app_name=My App
app_id=com.yourname.myapp
app_logo=logo
color_brand=#4338CA
```

<p align="left">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-green">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-green">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="no sdk" src="https://img.shields.io/badge/third--party%20SDKs-0-brightgreen">
</p>

---

## Make it yours in 60 seconds

1. Open **`app-config.properties`** and change `start_url`, `app_name`,
   `app_id`, colours — whatever you like.
2. Drop your logo into `app/src/main/res/drawable/` as **`logo.png`**
   (512×512, transparent background) or `logo.xml` (vector). Keep
   `app_logo=logo`, or rename the file and update that line.
3. Build.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> In Android Studio: after editing the properties file, click **Sync Project with
> Gradle Files** (or just hit Run) so Gradle picks up the new values.

## The config file

| Key | Default | What it does |
|---|---|---|
| `start_url` | `https://example.com` | Website the app opens |
| `app_name` | `WebViewShell` | Name under the launcher icon |
| `app_id` | `com.webviewshell` | Package / application id — **use your own** |
| `version_code` | `1` | Integer, bump on every release |
| `version_name` | `1.0.0` | Shown in app info |
| `app_logo` | `logo` | Drawable name used for icon, splash and error screen |
| `color_brand` | `#4338CA` | Primary colour (buttons, progress, accents) |
| `color_brand_dark` | `#312E81` | Darker brand shade |
| `color_brand_light` | `#6366F1` | Lighter brand shade |
| `color_on_brand` | `#FFFFFF` | Text/icon colour on top of brand colour |
| `color_background` | `#FFFFFF` | App background |
| `color_text_primary` | `#111827` | Error screen title |
| `color_text_secondary` | `#6B7280` | Error screen body |
| `color_status_bar` | *(= brand)* | Status bar colour |
| `color_icon_background` | *(= brand)* | Adaptive icon background |
| `color_splash_background` | *(= brand)* | Splash screen background |
| `enable_pull_to_refresh` | `true` | Pull down to reload |
| `keep_audio_playing_in_background` | `true` | Audio/video keeps playing when app is backgrounded |
| `open_non_web_links_in_other_apps` | `false` | `false` keeps `tel:`/`mailto:`/`market:` inside/blocks them |
| `show_loading_bar` | `true` | Thin progress bar |
| `allow_cleartext_traffic` | `true` | Allow plain `http://` sites |
| `enable_pinch_zoom` | `false` | Pinch to zoom |

Colours accept `#RGB`, `#RRGGBB` or `#AARRGGBB`. Booleans accept `true` / `false`.

### About the package name

`app_id` is what users and Google Play see. The Kotlin source stays in the
internal package `com.webviewshell` so you never have to refactor code — that is a
supported Android setup (namespace ≠ application id). If you *do* want the source
package renamed too, use Android Studio's *Refactor → Rename* on the package and
update `namespace` in `app/build.gradle.kts`.

## What works out of the box

| Feature | Status |
|---|---|
| Any website (`http`, `https`, SPAs, web apps) | ✅ |
| HTML5 video & audio, autoplay | ✅ |
| Fullscreen video (immersive, screen stays awake) | ✅ |
| Background audio when leaving the app | ✅ |
| Popups / `target="_blank"` / `window.open()` | ✅ same screen |
| File uploads (`<input type="file">`, multiple) | ✅ |
| Downloads (PDF, zip, images …) via DownloadManager | ✅ |
| Camera & microphone for WebRTC | ✅ |
| Geolocation | ✅ |
| Pull-to-refresh, back-button history, offline screen | ✅ |
| Adaptive icon, splash screen, flat native styling | ✅ |
| URL bar / tabs / browser menu / external hand-offs | ❌ by design |

## Build

- **Android Studio**: open the folder, sync, Run.
- **CLI**: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.
- **GitHub Actions**: every push builds a debug APK — **Actions** → latest
  **Build APK** run → download the `WebViewShell-debug-apk` artifact.

Release builds are unsigned on purpose; add your keystore in
`app/build.gradle.kts` → `signingConfigs`.

## Project structure

```
WebViewShell/
├── app-config.properties                       # <-- the one file you edit
├── app/src/main/
│   ├── java/com/webviewshell/MainActivity.kt   # the whole app, ~400 lines
│   ├── res/drawable/logo.xml                   # default logo (replace it)
│   ├── res/layout/activity_main.xml            # WebView + progress + error + fullscreen
│   └── AndroidManifest.xml
└── .github/workflows/build.yml                 # CI that ships the APK
```

## How "stays in the app" works

`shouldOverrideUrlLoading` never hands web URLs to another app, so navigation
always happens inside the WebView. Popups are re-routed into the same screen.
Downloads go through Android's `DownloadManager` (a system service, not a
browser). Non-web schemes are blocked with a toast unless you flip
`open_non_web_links_in_other_apps`.

## Known WebView limits (not fixable from the app)

- **Google / Facebook "Sign in with"** is blocked inside embedded WebViews by the
  providers themselves. Use email/OTP login or Custom Tabs for those flows.
- **DRM video** (Netflix, Prime, Spotify) needs Widevine L1 + a native player.
- **`blob:` downloads** and file-system APIs need a JavaScript bridge.
- **Push notifications** need a native service.
- Some **payment gateways** refuse WebView user agents.

If your product depends on one of these, a WebView shell is the wrong tool —
use native screens or a Trusted Web Activity.

## Security notes

- SSL errors are cancelled, never bypassed.
- No `@JavascriptInterface` bridge is exposed; file/content access is off.
- Cleartext HTTP is allowed by default for compatibility — set
  `allow_cleartext_traffic=false` before publishing.

## Troubleshooting

| Error | Fix |
|---|---|
| `'-' is not a valid file-based resource name character` | Android resource file names may only use lowercase `a-z`, `0-9`, `_`. Rename `my-logo.png` → `my_logo.png`. |
| `app_id ... is not a valid Android application id` | No dashes or spaces, at least two segments: `com.yourname.yourapp`. |
| `duplicate resources: drawable/logo` | Only one `logo.xml` **or** `logo.png` may exist. Delete the one you don't use. |
| `app_logo = "x" not found` | The drawable doesn't exist in `app/src/main/res/drawable/`. |
| Launcher icon looks cropped or tiny | Use a 512×512 PNG with ~17% transparent padding and the artwork in the centre 66%. |
| New config values not applied | Android Studio: **Sync Project with Gradle Files**, then rebuild. |

The build checks the config for you, so most mistakes fail fast with a readable
message instead of a stack trace.

## License

[MIT](LICENSE) © 2026 johirxofficial

Not affiliated with Google. "Android" and "WebView" are trademarks of Google LLC.
