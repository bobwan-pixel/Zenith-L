# Troubleshooting

## Build fails before Kotlin compilation

Install JDK 17, Android SDK Platform 35, NDK `27.2.12479018`, CMake `3.22.1`, and Gradle 8.9. The NDK build compiles vendored Lua 5.4.9 sources into `libzenith_lua.so` for Android ABIs.

## A script reports unavailable GL API

Check the API Browser and [API status table](ZENITH_L_API_REFERENCE.md). World/player/inventory/packet/hook APIs require GameBridge; the present build exposes only `Zenith.getVersion()` as a callable custom binding. No game state is simulated.

## Script stops slowly

Lua stop/pause checks run at instruction checkpoints. A blocking native function cannot yield to the hook. The runtime currently registers no game/network native calls.

## Overlay does not appear

Grant Android's display-over-other-apps permission and enable the Zenith'L accessibility watcher. Android foreground package detection does not prove Growtopia world readiness. See app logs under private `files/logs/zenith.log` using Android app diagnostics/logcat.

## Runtime errors

Inspect script/session-filtered app logs and the per-script last error. Lua tracebacks can include the source chunk and line. Stop the failed script before editing/restarting if it remains marked active.
