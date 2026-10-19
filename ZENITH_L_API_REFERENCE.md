# Zenith'L Lua API Reference

- Zenith API version: `0.3`
- GL compatibility reference label: `community-reference-2025-08`
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
| `Zenith.getRuntimeInfo()` | → table | INTERFACE_ONLY | Metadata entry only; native binding is pending. |
| `Zenith.getBots()` | → varies | INTERFACE_ONLY | Kotlin BotManager façade exists; no Lua binding or live sessions. |
| `Zenith.getBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.startBot(id)` | id → varies | INTERFACE_ONLY | GameBridge unavailable; session start is refused. |
| `Zenith.stopBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.pauseBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `Zenith.restartBot(id)` | id → varies | INTERFACE_ONLY | Lua binding pending. |
| `getTile(x,y)` / `getTiles()` | coordinates / none → Tile / Tile[] | INTERFACE_ONLY | Requires real world snapshots. |
| `getWorldName()` | none → string? | INTERFACE_ONLY | Requires active game state. |
| `getInventory()` / `getLocal()` | none → items / player | INTERFACE_ONLY | Session state providers have no game provider. |
| `getPlayerList()` / `getObjectList()` / `getNPCList()` | none → arrays | INTERFACE_ONLY | Session state providers have no game provider. |
| `findItemID(name)` / `getItemInfoByID(id)` | name/id → item result | INTERFACE_ONLY | File-backed database exists; Lua binding is pending. |
| `FindPath(x,y)` | coordinates → boolean | INTERFACE_ONLY | Requires supported navigation bridge. |
| `AddHook(callback,event)` / `removeHook(id)` | callback/event or ID → hook/void | INTERFACE_ONLY | Kotlin session EventBus exists; Lua/game adapters are absent. |
| `SendPacket(type,packet)` / `SendPacketRaw(packet)` / `SendVariant(variant)` | packet data → boolean | INTERFACE_ONLY | Requires a legitimate game bridge. |
| `Sleep(ms)` | milliseconds → void | NOT_IMPLEMENTED | No cooperative scheduler binding. |
| `runThread(fn,args?)` / `runCoroutine(fn,args?)` | function → task/thread | NOT_IMPLEMENTED | Coroutine library exists; GL lifecycle adapters do not. |

The runtime exposes only the documented `Zenith.getVersion()` binding from this registry. Entries marked otherwise are not callable GL APIs; attempts produce explicit unavailable errors. No 100% compatibility claim is made.

## Object models

Kotlin model/provider boundaries exist for `Tile`, `ItemInfo`, `InventoryItem`, `Player`, `LocalPlayer`, `WorldObject`, `NPC`, `WorldSnapshot`, and `BotSession`. Fields are defined in the corresponding package source. Lua object marshaling and live values are `INTERFACE_ONLY` until the bridge exists.
