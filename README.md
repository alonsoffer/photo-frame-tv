# Photo Frame — Google TV Drive Slideshow

A Google TV / Android TV app that turns your TV into a wall photo frame, cycling through the
photos in a Google Drive folder. Built with Kotlin + Jetpack Compose for TV.

## Features
- **Slideshow** of photos from a chosen Google Drive folder, with next-image prefetch for smooth transitions.
- **Device-pairing sign-in** (Google's official flow for TVs): the TV shows a code + QR, you approve on your phone.
- **In-app folder browser** — walk your Drive and pick which folder to display.
- **Settings** (persisted): interval between photos, scaling (**Fit / Fill / Stretch**),
  transition (**Fade / Slide / Ken Burns / None**), sequential/shuffle order, include subfolders,
  clock overlay (12/24h), and an overnight dim schedule.
- **Demo mode** shows built-in sample photos so you can try the UI before setting up Google credentials.

## Remote controls (on the slideshow)
| Button | Action |
| --- | --- |
| ◀ / ▶ (D-pad left/right) | Previous / next photo |
| OK / Center | Play / pause |
| ▲ (D-pad up) or Menu | Open Settings |
| Back (in Settings/Browser) | Go back |

## Requirements
- JDK 17+ (this repo is configured for the JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` via
  `org.gradle.java.home` in `gradle.properties` — change it to your JDK if different).
- Android SDK with platform **android-36** installed.

## Build & run (demo mode — no Google setup needed)
Demo mode is on by default (`DEMO_MODE=true` in `local.properties`).

```bash
# Build the debug APK
./gradlew assembleDebug

# Install to a running emulator / device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.photoframe.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```

No TV emulator image is installed by default. Options:
- Use an existing **phone** emulator (the app also registers the normal LAUNCHER category), or
- Install a TV image and make a TV AVD:
  ```bash
  sdkmanager "system-images;android-36;android-tv;x86_64"
  avdmanager create avd -n tv -k "system-images;android-36;android-tv;x86_64" --device "tv_1080p"
  emulator -avd tv &
  ```

## Connecting your real Google Drive (one-time setup)

Auth uses **Google Identity Services** — the app signs in with the Google account already on the TV
(account picker + on-device consent, no password typing). Google matches the app by its **package
name + signing SHA-1**, so there is **no client id/secret** in the code.

> Note: the device-pairing flow does NOT support the `drive.readonly` scope — Google rejects it with
> `invalid_scope`. That's why this app uses on-device authorization instead.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and **create a project**.
2. **APIs & Services → Library → enable "Google Drive API"**.
3. **APIs & Services → OAuth consent screen**:
   - User type: **External**.
   - Add the scope `https://www.googleapis.com/auth/drive.readonly`.
   - Under **Test users**, add your own Google account email (required until the app is verified).
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**.
   - **Package name:** `com.photoframe.tv`
   - **SHA-1:** the fingerprint of the key that signs the build you install. Get it with:
     ```bash
     # debug build:
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
       -storepass android -keypass android | grep SHA1
     # release build (upload key):
     keytool -list -v -keystore upload-keystore.jks -alias upload \
       -storepass "$(grep storePassword keystore.properties | cut -d= -f2)" | grep SHA1
     ```
     (For the Play production app you must ALSO add the **Play App Signing** SHA-1 shown in Play Console.)
5. Turn off demo mode in `local.properties`:
   ```properties
   DEMO_MODE=false
   ```
6. Rebuild & reinstall. Launch → **Connect Google Drive** → pick your account → **Allow**. The
   slideshow starts; open **Settings → Photo source** to pick a folder.

> The app requests the least-privilege **read-only** Drive scope and never modifies your files.
> Access is only between your TV and Google's APIs — nothing is sent anywhere else.

## Project layout
```
app/src/main/java/com/photoframe/tv/
  App.kt, MainActivity.kt        # Application + single Activity (immersive, keep-awake)
  di/AppGraph.kt                 # manual dependency graph
  auth/                          # Google on-device authorization + OkHttp interceptor
  drive/                         # Drive REST client, repository, demo repository
  data/                          # settings model + DataStore repository
  ui/                            # Compose screens: connect, slideshow, settings, folder browser
```

## Notes
- `compileSdk`/`targetSdk` are 36; AGP 8.7.3 with Gradle 8.9. If you change the SDK, keep AGP/Gradle compatible.
- Auth is Google Identity Services (`play-services-auth`); Drive access tokens are cached in memory and
  silently re-authorized in the background, so a wall frame keeps running without re-prompting.
