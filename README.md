# Zenith'L Launcher & Runtime Workspace

Zenith'L is a Growtopia-oriented launcher and scripting workspace targeting Android and Windows. The Android app includes the native Lua 5.4.9 runtime and overlay; the Windows target is a separate desktop workspace with a restricted native Lua runner. Neither target currently ships a real Game Bridge or trusted account backend.

## Current status

- **Implemented:** Android native Lua 5.4.9 execution through Kotlin → JNI → native Lua, isolated Lua states, bounded allocator, stop/pause hooks, traceback reporting, local script CRUD and metadata, SQLite persistence, file logs, validated global item catalog with staged update/backup/rollback, session-scoped state abstractions, module/API browsers, watchdog foundations, incident persistence, and SYSTEM_ALERT_WINDOW overlay controls. Google sign-in uses Credential Manager when a Web Client ID is configured.
- **Windows:** Separate Swing desktop workspace, local Lua file editor/import, and native restricted Lua 5.4 runner with memory, instruction, and time budgets. Build/package with Windows JDK 17 and CMake; see [Windows desktop](WINDOWS_DESKTOP.md).
- **Partial:** Package/version detection and compatibility profile reporting; bot/session profile manager UI and persistence; global local catalog validation/update mechanics; runtime watchdog wall-clock supervision; Windows workspace and isolated Lua runner. Package inspection cannot report another app's foreground lifecycle.
- **Interface only / unavailable:** Growtopia Game Bridge, world/player/inventory/object/NPC providers, hooks, game APIs, operational bots, remote item-catalog source, trusted Google token validation, and account authentication. No game data or login success is fabricated. Android AccessibilityService is not used.
- **Not implemented:** GL `Sleep`, `runThread`, and `runCoroutine`; foreground game detection; editor diagnostics/autocomplete; catalog remote manifest fetch; Android/Windows product parity. Windows target has no overlay/Game Bridge/session API.

The GL names and shapes are tracked against a community-maintained reference, not an official compatibility guarantee. See [ZENITH_L_API_REFERENCE.md](ZENITH_L_API_REFERENCE.md).

## Build

Install JDK 17, Android SDK Platform 35, Android NDK `27.2.12479018`, and CMake `3.22.1`. Android support starts at API 23 (Android 6.0). The checked-in Gradle Wrapper downloads Gradle 8.9. Then run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

On a connected/emulated Android device, run native runtime instrumentation tests with:

```sh
./gradlew connectedDebugAndroidTest
```

GitHub Actions builds the debug APK, executes JVM tests, and runs native Lua instrumentation tests in an Android emulator.

Google Credential Manager requires a public Web Client ID supplied through Gradle property `googleWebClientId` or environment variable `GOOGLE_WEB_CLIENT_ID`. Even after configuring that ID, sign-in will not create a Zenith account until a trusted token-validation backend is implemented.

## App data

Scripts, profiles, session profiles, application preferences, scheduler rules, item database metadata, and diagnostic events use app-private storage and SQLite. The item database is an updateable validated TSV file; Zenith'L does not bundle a fabricated full Growtopia item list. Lua source/license attribution is in `app/src/main/cpp/lua-5.4.9/`.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Lua scripts and lifecycle](SCRIPT_SYSTEM.md)
- [API reference and status](ZENITH_L_API_REFERENCE.md)
- [Multi-session foundation](MULTI_SESSION.md)
- [Item database](DATABASE.md)
- [Troubleshooting](TROUBLESHOOTING.md)
