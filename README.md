# Photo Frame — Google TV + phone photo frame

One app with two roles. On a **Google TV / Android TV** it's a full-screen photo-frame slideshow;
on a **phone or tablet** it's a touch manager for the photos the frame shows. Both share a single
private "Photo Frame" folder in your Google Drive. Built with Kotlin + Jetpack Compose (TV Material3
on the TV, Material3 on the phone).

## How it works
- Photos live in a **"Photo Frame" folder** in your own Google Drive. The app uses the
  **`drive.file`** scope, so it can only ever touch the folder it created — never the rest of your
  Drive.
- Add photos from the **phone app** (or via Android's Share sheet); they appear on the **TV**
  automatically.
- Pair a TV by **scanning the QR code** it shows with the phone app.
- Prefer no account? The TV can instead show a **local folder or USB drive** (Storage Access
  Framework) with no Google sign-in at all.

## Features
### TV (slideshow)
- Photos from the shared Drive folder, or a local/USB folder, with next-image prefetch.
- Transitions: **Fade / Slide / Ken Burns / None**; scaling **Fit / Fill / Stretch**.
- Interval between photos, periodic auto-refresh, sequential or shuffled order.
- Clock overlay (12/24h) and an overnight dim schedule.
- Start automatically on boot.
- First launch lets you choose **Drive** or **Local** before anything else; the pairing QR appears
  only when you pick Drive. The frame can cancel pairing and reissue a new connection.

### Phone / tablet (manager)
- A gallery of the frame's photos — **view, add, and delete**.
- Add from the photo picker or share images to the app from any gallery.
- Scan a TV's QR to pair it; disconnect when you want.

### Both
- **Device-flow sign-in** (OAuth 2.0 Device Authorization Grant, RFC 8628) — approve with a short
  code/QR, no passwords typed on a remote.
- 7 languages with RTL support — English, Hebrew, Arabic, German, Spanish, French, Russian —
  auto-selected from the device locale, changeable in Settings.
- **Demo mode** shows built-in sample photos so you can try the UI with no Google setup.

## Remote controls (TV slideshow)
| Button | Action |
| --- | --- |
| ◀ / ▶ (D-pad left/right) | Previous / next photo |
| OK / Center | Play / pause |
| ▲ (D-pad up) or Menu | Open Settings |
| Back (in Settings/Browser) | Go back |

## Requirements
- JDK 17+ (this repo points `org.gradle.java.home` at a JDK 21 in `gradle.properties` — change it
  to your JDK if different).
- Android SDK with platform **android-36** installed.

## Build & run (demo mode — no Google setup needed)
Demo mode is on by default (`DEMO_MODE=true` in `local.properties`).
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# TV launcher:
adb shell monkey -p com.photoframe.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```
The app auto-detects the device: TVs (leanback / no touchscreen) get the slideshow, phones/tablets
get the manager.

## Connecting your real Google Drive (one-time setup)
Auth uses the **OAuth 2.0 Device Authorization Grant** with the non-sensitive **`drive.file`**
scope — no verification or CASA assessment required.

1. [Google Cloud Console](https://console.cloud.google.com/) → **create a project**.
2. **APIs & Services → Library → enable "Google Drive API"**.
3. **APIs & Services → OAuth consent screen** → User type **External**; add the scope
   `https://www.googleapis.com/auth/drive.file` (non-sensitive).
4. **APIs & Services → Credentials → Create credentials → OAuth client ID** → application type
   **TVs and Limited Input devices**. This gives you a **client ID + client secret**.
5. Put them in `local.properties` and turn off demo mode:
   ```properties
   TV_CLIENT_ID=xxxx.apps.googleusercontent.com
   TV_CLIENT_SECRET=xxxx
   DEMO_MODE=false
   ```
6. Rebuild & reinstall. On the TV choose **Drive**, scan the QR (or enter the code) and approve.
   On the phone, connect the same account and start adding photos.

> `drive.file` is the least-privilege scope that fits: the app sees only the "Photo Frame" folder it
> creates, never your other files. Nothing is sent anywhere except between your devices and Google.

## Release build
A signed App Bundle for Play (signing read from `keystore.properties` + `upload-keystore.jks`,
both git-ignored):
```bash
./gradlew :app:bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
```
See [`play/PUBLISHING.md`](play/PUBLISHING.md) for the full Play Store walkthrough and
[`play/listing/`](play/listing/) for the store copy and graphics.

## Project layout
```
app/src/main/java/com/photoframe/tv/
  App.kt, MainActivity.kt        # Application + single Activity; detects TV vs phone role
  ShareUploadActivity.kt         # phone Share-sheet target -> upload to the Drive folder
  BootReceiver.kt                # relaunch the frame on power-on
  di/AppGraph.kt                 # manual dependency graph
  auth/                          # device-flow auth (DeviceAuthManager) + OkHttp interceptor
  drive/                         # Drive REST client, Drive + local + demo repositories
  data/                          # settings model + DataStore repository
  ui/                            # Compose screens: TV (slideshow/settings/connect) + phone (gallery)
  Localization.kt, res/values-*  # 7-language strings + locale handling
```

## Notes
- `compileSdk`/`targetSdk` are 36. Keep AGP/Gradle compatible if you change the SDK.
- Secrets (`local.properties`, `keystore.properties`, `*.jks`, OAuth JSON) are git-ignored — never
  commit them.
