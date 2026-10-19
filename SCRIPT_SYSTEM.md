# Script system

`ScriptManager` owns persistent script metadata and execution lifecycle. Records include name/path, enabled, auto-run, favorite, state, last run/error, timestamps, and a profile map. It supports create, rename, duplicate, delete, import/export at the service boundary, enable/disable, search/sort, run, pause/resume, stop, and restart. Disabled scripts are rejected. Script source is app-private; profiles are stored separately in SQLite.

`ScriptController` is the UI boundary and performs file/database/execution operations on a worker executor. Lua runs asynchronously through one serialized native executor per session, with an upper bound of three session executors. Stop and pause are cooperative at Lua instruction checkpoints, so a native blocking C function cannot be interrupted until it returns. One Lua state is created per session; script globals do not share a state.

The scheduler supports immediate, Game Bridge ready, session-created, world-ready, and after-script triggers with delays. Bridge/world triggers are dormant until a real provider dispatches them; package installation/version detection does not prove the game is foreground or ready. Auto-run metadata is persisted but no script is auto-started by package detection. Crash recovery policy has bounded restart counts and delay, but automatic native process recovery is not a guarantee.

Native runtime errors include Lua traceback text. The logger records component/script/session/level/timestamp; structured diagnostic errors preserve file, line when Lua provides it, message, stack, and session. Static API/config validation and editor syntax highlighting are not implemented.

Security limits: no Lua filesystem, OS, package, dynamic loader, or debug standard library. No remote code loader or credential storage is included.
