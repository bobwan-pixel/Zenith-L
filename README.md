# Zenith'L Android Launcher

Zenith'L is an Android launcher and local Lua scripting workspace for Growtopia. Phase 2 adds a native Lua 5.4.9 runtime, persistent script management, diagnostics, session scoped state interfaces, and a documented compatibility registry.

## Current status

- **Implemented:** Native Lua 5.4.9 execution through Kotlin → JNI → native Lua, asynchronous per session, isolated Lua states, bounded allocator, stop and pause hooks, traceback reporting, selected standard libraries, local script CRUD and metadata, profiles, SQLite persistence, file logs, item database file validation/update/backup/rollback, session scoped event/state abstractions, API browser, and the Phase 1 overlay flow.
- **Partial:** Script scheduling and auto-run after Growtopia foreground detection; bot/session profile manager UI and persistence; item database storage/update mechanics. Scheduling responds to app detection and session lifecycle; world-ready and script dependency events require a real bridge or explicit caller.
- **Interface only:** Growtopia game bridge, world/player/inventory/object/NPC state providers, game hooks, packet APIs, pathfinding, and operational bot sessions. No game state is fabricated and these calls report unavailable.
- **Not implemented:** GL `Sleep`, `runThread`, and `runCoroutine`; editor syntax highlighting, undo/redo, and static validation; automated Gradle build locally (Gradle/Android SDK are not installed in this workspace).

The GL names and shapes are tracked against a community-maintained reference, not an official compatibility guarantee. See [ZENITH_L_API_REFERENCE.md](ZENITH_L_API_REFERENCE.md).

## Build

Install JDK 17, Android SDK Platform 35, Android NDK `27.2.12479018`, CMake `3.22.1`, and Gradle 8.9. Then run:

```sh
gradle testDebugUnitTest assembleDebug
```

On a connected/emulated Android device, run native runtime instrumentation tests with:

```sh
gradle connectedDebugAndroidTest
```

GitHub Actions builds the debug APK, executes JVM tests, and runs native Lua instrumentation tests in an Android emulator.

## App data

Scripts, profiles, session profiles, application preferences, scheduler rules, item database metadata, and diagnostic events use app-private storage and SQLite. The item database is an updateable validated TSV file; Zenith'L does not bundle a fabricated full Growtopia item list. Lua source/license attribution is in `app/src/main/cpp/lua-5.4.9/`.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Lua scripts and lifecycle](SCRIPT_SYSTEM.md)
- [API reference and status](ZENITH_L_API_REFERENCE.md)
- [Multi-session foundation](MULTI_SESSION.md)
- [Item database](DATABASE.md)
- [Troubleshooting](TROUBLESHOOTING.md)
