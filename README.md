# MyTube Android App

A native Android wrapper for your MyTube web app, built as a full-screen
WebView "shell." It behaves like a real app — its own icon, splash/theme,
autoplaying muted Shorts, file uploads (thumbnails), downloads (Shorts
"Save" button, watch-page downloads), full-screen video, pull-to-refresh,
and proper back-button navigation — without rewriting any of your UI.

## 1. Set your site's URL

Open `app/src/main/res/values/strings.xml` and change `base_url` to your
deployed Cloudflare Worker address (the `*.workers.dev` URL, or your custom
domain if you attached one):

```xml
<string name="base_url">https://mytubepersonal.workers.dev/</string>
```

## 2. Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Hedgehog or newer).
2. `File → Open` and select this `MyTubeApp` folder.
3. Let Gradle sync (it will download the Android SDK/build tools it needs —
   requires internet access the first time).
4. Press ▶️ Run to install it on an emulator or a connected device.

## 3. Build a release APK/AAB

- **Quick APK for testing**: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
  The APK lands in `app/build/outputs/apk/release/`.
- **Play Store bundle**: `Build → Generate Signed Bundle / APK`, choose
  Android App Bundle, and create/select a signing key when prompted.
  You'll need a signing key to publish to the Play Store — Android Studio
  can generate one for you in that same wizard.

## What's included

| Feature | How |
|---|---|
| Full-screen app, no browser chrome | `WebView` fills the whole screen |
| Muted Shorts autoplay | `mediaPlaybackRequiresUserGesture = false` |
| Full-screen video button | `onShowCustomView`/`onHideCustomView` in `MainActivity.kt` |
| Thumbnail/file uploads | `onShowFileChooser` opens the system picker |
| Shorts "Save" / video downloads | `DownloadManager`, forwarding your session cookie |
| Stay logged in between app launches | `CookieManager` persists cookies |
| Back button | Goes back within site history before exiting the app |
| Pull-to-refresh | `SwipeRefreshLayout` |
| Offline screen + Retry | Shown automatically when there's no connection |
| External links (not your domain) | Open in the system browser, not inside the app |

## Notes / things you may want to tweak

- **App icon**: `res/drawable/ic_launcher_foreground.xml` is a simple
  placeholder red play-triangle icon. Swap in real launcher icons (e.g.
  generate a full set via Android Studio's Image Asset tool —
  right-click `res` → `New → Image Asset`) whenever you're ready.
- **Camera/mic permissions**: included in the manifest in case you ever
  add in-page video recording (`<input capture>` or `getUserMedia`). If
  you never plan to, you can delete the `CAMERA`/`RECORD_AUDIO` lines from
  `AndroidManifest.xml`.
- **Local dev testing**: if you want to point the app at a local
  `wrangler dev` server instead of your deployed URL, see the commented-out
  block in `res/xml/network_security_config.xml` — cleartext HTTP is
  blocked everywhere else by default for security.
- **App name/package**: currently `com.mytube.app` / "MyTube". Change the
  `applicationId`/`namespace` in `app/build.gradle.kts` before publishing
  if you want a different package name.
