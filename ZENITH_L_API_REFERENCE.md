# Zenith'L Lua API Reference

- Zenith API version: `0.4`
- GL compatibility reference label: `community-reference-2025-08 (unverified)`
- Baseline: [GrowLauncher community documentation](https://github.com/PowerKuy/Growlauncher-Documentation/blob/main/Readme.md). This is a community reference and may be incomplete or outdated; it is not an official GL contract.
- Statuses describe Zenith'L implementation, not whether a name exists elsewhere.

## Status meanings

| Status | Meaning |
| --- | --- |
| `IMPLEMENTED` | Lua callable exists and works as documented. |
| `PARTIAL` | A useful part exists, with stated limitations. |
| `INTERFACE_ONLY` | Registry/model/provider boundary exists; live behavior is unavailable. |
| `NOT_IMPLEMENTED` | No callable implementation exists. |

## API registry

The in-app API browser reads the same `ApiRegistry` metadata used by completion/search clients. No editor completion widget is implemented yet.

| API | Parameters → return | Status | Notes |
| --- | --- | --- | --- |
| `Zenith.getVersion()` | → string | IMPLEMENTED | Returns app version. |
| `Zenith.getRuntimeInfo()` | → table | IMPLEMENTED | Returns `luaVersion`, `memoryLimitBytes`, and `maxSessions`. |
| `Zenith.getGameInfo()` / `getGameVersion()` | → table / string? | INTERFACE_ONLY | Package detector exists; Lua binding and live bridge state are absent. |
| `Zenith.getBridgeStatus()` | → string | IMPLEMENTED | Returns `UNAVAILABLE` while Game Bridge is not implemented. `Zenith.gameBridgeStatus` exposes the same state. |
| `Zenith.getSessionInfo()` | → table | IMPLEMENTED | Returns the current isolated runtime session `id` and `runtimeStatus`. |\n| `Zenith.getSessions()` | → varies | INTERFACE_ONLY | Session manager exists in Kotlin; collection binding is pending. |
| `Zenith.getBots()` | → varies | INTERFACE_ONLY | Kotlin BotManager façade exists; no Lua binding or live sessions. |
| `Zenith.getBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.startBot(id)` | id → varies | INTERFACE_ONLY | GameBridge unavailable; session start is refused. |
| `Zenith.stopBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.pauseBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.restartBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `getTile(x,y)` / `getTiles()` | coordinates / none → Tile / Tile[] | INTERFACE_ONLY | Requires real world snapshots. |
| `getWorldName()` | none → string? | INTERFACE_ONLY | Requires active game state. |
| `getWorld()` | none → World? | INTERFACE_ONLY | Requires active game state. |
| `getInventory()` / `getLocal()` | none → items / player | INTERFACE_ONLY | Session state providers have no game provider. |
| `getPlayerList()` / `getObjectList()` / `getNPCList()` | none → arrays | INTERFACE_ONLY | Session state providers have no game provider. |
| `findItemID(name)` / `getItemInfoByID(id)` | name/id → item result | INTERFACE_ONLY | File-backed database exists; Lua binding is pending. |
| `FindPath(x,y)` | coordinates → boolean | INTERFACE_ONLY | Requires supported navigation bridge. |
| `AddHook(callback,event)` / `removeHook(id)` | callback/event or ID → hook/void | INTERFACE_ONLY | Kotlin session EventBus exists; Lua/game adapters are absent. |
| `SendPacket(type,packet)` / `SendPacketRaw(packet)` / `SendVariant(variant)` | packet data → boolean | INTERFACE_ONLY | Requires a legitimate game bridge. |
| `Sleep(ms)` | milliseconds → void | NOT_IMPLEMENTED | No cooperative scheduler binding. |
| `runThread(fn,args?)` / `runCoroutine(fn,args?)` | function → task/thread | NOT_IMPLEMENTED | Coroutine library exists; GL lifecycle adapters do not. |

The runtime currently exposes `Zenith.getVersion()`, `Zenith.getRuntimeInfo()`, `Zenith.getSessionInfo()`, and `Zenith.getBridgeStatus()` as callable Lua functions. Game-dependent names are registered as explicit unavailable stubs and raise an `INTERFACE_ONLY` error; they do not silently return fabricated game data. The runtime opens only the base, table, string, math, UTF-8, and coroutine libraries. File loading, package, IO, OS, and debug libraries are not exposed. Item lookup exists in the app-global Kotlin database but is not currently bound into Lua.

## Object models

Kotlin model/provider boundaries exist for `Tile`, `ItemInfo`, `InventoryItem`, `Player`, `LocalPlayer`, `WorldObject`, `NPC`, `WorldSnapshot`, and `BotSession`. Fields are defined in the corresponding package source. Lua object marshaling and live values are `INTERFACE_ONLY` until the bridge exists.
