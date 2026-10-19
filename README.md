# Zenith'L Android Starter (v0.2.0)

Minimal Kotlin Android launcher and floating overlay for a Growtopia-focused Lua workspace. Designed for a Windows PC with 4 GB RAM, JDK 17, and a free Android SDK installation.

## Implemented
- Dark main screen branded Zenith'L.
- Launches the installed Growtopia app when its package is `com.rtsoft.growtopia`.
- Overlay permission flow and movable floating `Z'` button.
- App Watcher accessibility service shows the floating button while Growtopia is in the foreground.
- Per-script auto-run selection is stored locally and shown in the main screen and overlay.
- Play/Pause controls explain that execution is unavailable until a Lua runtime is added.
- Custom launcher icon made from the supplied Zenith'L image.
- Floating panel opens the local Lua workspace.
- Creates, lists, edits, saves and deletes `.lua` files in app-private storage.
- Filename validation.

## Not implemented yet
- Lua 5.4 execution engine (no NDK/JNI dependency added yet).
- In-game Growtopia APIs/hooks, game state access or script injection.
- Selected auto-run scripts are not executed until a supported runtime exists.
- Actual MAC/GID/RID modification. Android does not allow a normal app to freely change the device Wi-Fi MAC address; game identifiers need separate verification.
- Syntax highlighting and import/export through Android Storage Access Framework.

## Requirements
- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9 (or use wrapper generated below)

## Build on Windows CMD

```bat
cd C:\Users\Acer\Documents\project
```
Extract this folder as `ZenithL`, then:

```bat
cd ZenithL
gradle wrapper --gradle-version 8.9
gradlew.bat assembleDebug
```

APK output:
`app\build\outputs\apk\debug\app-debug.apk`

If `gradle` is not recognized, install Gradle 8.9 or use Android Studio once to generate the Gradle wrapper. The first build needs internet to download Gradle and Android Gradle Plugin; subsequent builds can use cached dependencies.

## Install
Copy the APK to Android and install it. Install the official Growtopia app separately. In Zenith'L, tap **Set up auto floating menu**, allow **Display over other apps**, then enable **Zenith'L App Watcher** under Android Accessibility settings. Android requires these one-time permissions for foreground detection and the overlay. The watcher reads window change events and does not retrieve screen content.

## Storage
Scripts are kept inside Zenith'L app-private storage (`files/scripts`). They are not directly visible to other apps. Import/export via Android's document picker is a planned feature.
