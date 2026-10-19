# Architecture

## Layers

```text
Activities / overlay UI
    ↓ controller boundary
ScriptController · BotController · OverlayController
    ↓
ScriptManager · ScriptScheduler · BotManager · state providers
    ↓
SQLite repositories · Logger · session EventBus · GameBridge interface
    ↓
LuaExecutionEngine → JNI → native Lua 5.4.9
```

The UI does not call JNI or SQLite directly. `ZenithApp` composes services and repositories. Persistent app data is private to the app. Native Lua has one isolated state per session, runs on a bounded worker pool, and has a per-runtime memory limit. Lua base/table/string/math/utf8/coroutine libraries are opened; filesystem, OS, package loading, dynamic code loading, and debug libraries are unavailable.

## Modules

Kotlin packages divide scripts, Lua, API compatibility, item data, world/player/inventory/object state, hooks, sessions/bots, overlay, diagnostics, storage, and logging. `networking/GameBridge.kt` defines the required provider boundary; its default implementation explicitly reports unavailable. State interfaces and model types are foundations, not live game integrations.

## Lifecycle and safety

Script execution is asynchronous. Stop/pause are checked by a native instruction hook; Lua errors return tracebacks and are recorded per script/session. Hook registries are session-local and script-owned hooks can be removed together. Session profiles persist independently; starting a live game session currently returns unavailable because the bridge is absent. No credentials or game packets are fabricated or altered.

## Android game detection and overlay

`GamePackageManager` reads only the target package's installed version and launch intent. `GameVersionDetector` exposes the installed version; `GameCompatibilityManager` currently returns an unverified profile. Android does not expose another app's foreground lifecycle to an ordinary app, so Zenith'L does not claim to detect game foreground/background or world readiness. Launching Growtopia from Zenith'L can request the overlay service; the floating overlay uses only `SYSTEM_ALERT_WINDOW`. Automatic script scheduling waits for a real Game Bridge lifecycle signal.

`GameBridge` exposes `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `DEGRADED`, and `UNAVAILABLE`; the shipped provider is `UNAVAILABLE`. No service, permission flow, fallback, or runtime code depends on Android Accessibility.

## Compatibility status

See the status table and complete registry in [ZENITH_L_API_REFERENCE.md](ZENITH_L_API_REFERENCE.md). `PARTIAL` means the described app-level foundation exists but some runtime/game behavior is absent. `INTERFACE_ONLY` means a typed boundary or registry entry exists without a working game provider. `NOT_IMPLEMENTED` means no callable implementation exists.
