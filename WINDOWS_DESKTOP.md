# Windows desktop target

The `:desktop` Gradle module builds a separate JVM/Swing workspace for Windows. It supports local `.lua` file creation/import/editing and launches a native restricted Lua 5.4 runner. It stores scripts under `%APPDATA%\ZenithL\scripts`.

## Build

Requirements: Windows x64, JDK 17, CMake, Visual Studio C++ Build Tools, and the Android SDK/NDK configuration currently required when Gradle configures the shared Android project.

```powershell
cmake -S desktop/native -B desktop/native/build -A x64
cmake --build desktop/native/build --config Release
.\gradlew.bat :desktop:build :desktop:jpackageWindows
```

The Lua runner has a 32 MiB allocator, instruction and wall-clock budget, 1 MiB source limit, and omits Lua `io`, `os`, `package`, `debug`, dynamic loading, and garbage-collector control. It is a local script runtime, not an Android shell.

## Platform limits

Windows is currently a standalone script workspace. It does not include Android overlay permissions, Android Credential Manager, account authentication, multi-session runtime, shared item database, or a Growtopia Game Bridge. The desktop UI labels account and game integration unavailable; no Android feature parity is claimed.
