# TANAM Smart Eating Monitor — Android

Native Android port of the iOS app (Kotlin, Jetpack Compose, CameraX, ML Kit).
It does the same things as the iOS version, using the same timings and rules.

## Build the APK (Mac)

You need **Android Studio** installed (it includes the Android SDK and Java).

### Option A: Terminal

```bash
cd ~/Downloads/TanamSmartEatingMonitor_Android      # wherever you unzipped it
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleRelease
```

The first build downloads its dependencies and takes 3–5 minutes. The APK ends up here:

```
app/build/outputs/apk/release/app-release.apk
```

### Option B: Android Studio

File → Open → select this folder → wait for Gradle sync → **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
That builds a debug APK at `app/build/outputs/apk/debug/app-debug.apk`. It works the same way.

## Install on the phone

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If you see `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an older build with a different signature is already installed. Remove it first:

```bash
adb uninstall com.tanam.smarteatingmonitor
```

## iOS → Android feature map

| iOS | Android |
|---|---|
| Vision face landmarks (inner lips) | ML Kit face contours (bundled model, works offline) |
| Mouth openness = lip gap ÷ mouth width, 4-frame smoothing, 0.35 threshold | Same |
| Empty-mouth colour check (dark / red-pink / white = mouth, else food) | Same rules, read straight from the camera frame |
| Check every 50 s, 10 s grace period, hold mouth open 1 s | Same (adjustable in Settings) |
| Lock on: missed check, food in mouth, no face for 10 s, leaving the app | Same |
| Maths unlock (Easy / Medium / Hard, +/−), new question on a wrong answer, shake | Same |
| Optional re-check after unlock | Same |
| WKWebView YouTube / YouTube Kids, sign-in kept, JS video control, external sites blocked | Android WebView with the same injected script; Google sign-in works |
| Camera bubble with green/red face border, next-check countdown, OPEN/CLOSED | Same |
| Session bar: time, passes, locks, END with confirmation | Same |
| Summary: duration, passes, locks, success rate | Same |
| Guided Access instructions | **Screen pinning** toggle in Settings (pins the app during a session) |
| Haptics + sounds | Vibration patterns + tones |

### Android extras
- Settings are saved between launches.
- The Back button steps back inside YouTube and asks before ending the session. It does nothing while locked.
- YouTube full-screen video is supported.
- If the WebView crashes, it rebuilds itself instead of closing the app.
- The camera turns off automatically when the app goes to the background.

## Project layout

```
app/src/main/java/com/tanam/smarteatingmonitor/
├── MainActivity.kt            permissions, pinning, keep-awake, lifecycle → lock
├── SessionViewModel.kt        session state machine (port of SessionManager.swift)
├── model/SessionState.kt      states, lock reasons, config, summary
├── data/SettingsRepository.kt saved settings
├── camera/CameraController.kt CameraX front camera, ~12 fps analysis
├── detection/MouthDetector.kt ML Kit contours → openness
├── detection/MouthContentAnalyzer.kt empty-mouth colour check
├── web/YouTubeController.kt   WebView + JS video control
├── feedback/Feedback.kt       vibration + tones
└── ui/                        Compose screens (start, session, lock, end, settings)
```

Requirements: Android 8.0 (API 26) or newer, with a front camera.
