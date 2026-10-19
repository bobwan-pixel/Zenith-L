# Multi-session foundation

`BotManager` and `SessionManager` persist named `BotProfile` records and cap the configured set at three profiles (supporting the requested 2+ foundation). Each profile has a stable ID, label, optional attached script, world label, and restart policy. Account credentials are deliberately not stored.

Each `BotSession` owns its EventBus, hook registry, world/player/inventory/object provider wrappers, and session ID. LuaExecutionEngine creates separate native states per session. EventBus rejects mismatched session IDs; hooks can be removed by script. A focused JVM test verifies state object and event isolation. Native instrumentation tests verify two Lua state isolation.

This is not multi-login or a game connection implementation. The default GameBridge is unavailable; `startBot` refuses to transition into a fake running state. Item definitions remain global and shared; mutable world/player/inventory/entity/event data belongs to each session. Session UI controls and Lua `Zenith.*` bindings remain interface-only until a supported bridge is implemented.
