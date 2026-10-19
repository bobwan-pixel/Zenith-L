package id.zenithl.launcher.modules

/** Navigation and capability inventory for the in-game mod menu. Features derive from this registry. */
enum class CapabilityStatus { IMPLEMENTED, PARTIAL, INTERFACE_ONLY, NOT_IMPLEMENTED, UNAVAILABLE }
data class ModModule(
    val id: String,
    val title: String,
    val description: String,
    val status: CapabilityStatus,
    val icon: String = "◈",
    val version: String = "0.1",
    val requiredBridgeCapabilities: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
    val settings: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
)
data class ModCategory(val id: String, val title: String, val modules: List<ModModule>)

object ModModuleRegistry {
    private fun module(id: String, title: String, description: String, status: CapabilityStatus) = ModModule(
        id, title, description, status,
        requiredBridgeCapabilities = if (status == CapabilityStatus.INTERFACE_ONLY) setOf("GAME_BRIDGE_PROVIDER") else emptySet(),
        permissions = if (id == "overlay") setOf("SYSTEM_ALERT_WINDOW") else emptySet(),
        actions = when (status) { CapabilityStatus.IMPLEMENTED -> listOf("Open"); CapabilityStatus.PARTIAL -> listOf("Inspect status"); else -> emptyList() },
    )
    private val ready = CapabilityStatus.IMPLEMENTED
    private val bridge = CapabilityStatus.INTERFACE_ONLY
    private val unavailable = CapabilityStatus.UNAVAILABLE

    val categories: List<ModCategory> = listOf(
        ModCategory("home", "HOME", listOf(module("runtime", "Runtime Status", "Local runtime and active workspace.", ready), module("quick", "Quick Actions", "Open scripts and session controls.", ready))),
        ModCategory("basic", "BASIC", listOf(module("game", "Game Information", "Installed target package and version metadata.", ready), module("bridge", "Game Bridge", "Live game-state integration.", unavailable))),
        ModCategory("movement", "MOVEMENT", listOf(module("path", "Pathfinding", "Session-bound path query provider.", bridge))),
        ModCategory("visual", "VISUAL", listOf(module("overlay", "Overlay Controls", "Floating workspace controls.", ready), module("world-overlay", "World Overlay", "Live world rendering data.", bridge))),
        ModCategory("world", "WORLD", listOf(module("world-scan", "World Scanner", "Read-only world-state scanner.", bridge), module("tiles", "Tile Queries", "Tile data from the active session.", bridge))),
        ModCategory("player", "PLAYER", listOf(module("players", "Player Overview", "Player state for this session.", bridge))),
        ModCategory("inventory", "INVENTORY", listOf(module("inventory-items", "Inventory Items", "Session inventory state.", bridge), module("catalog", "Item Database", "Shared global item catalog; status depends on installed verified catalog.", CapabilityStatus.PARTIAL))),
        ModCategory("objects", "OBJECTS", listOf(module("objects-scan", "Object Scanner", "World objects from Game Bridge.", bridge))),
        ModCategory("npc", "NPC", listOf(module("npc-scan", "NPC Scanner", "NPC state from Game Bridge.", bridge))),
        ModCategory("automation", "AUTOMATION", listOf(module("scheduler", "Script Scheduler", "Persisted script scheduling rules.", ready), module("recovery", "Recovery", "Bounded restart policy foundation.", CapabilityStatus.PARTIAL))),
        ModCategory("bot", "BOT", listOf(module("sessions", "Bot Sessions", "Isolated session profiles; game attachment unavailable.", CapabilityStatus.PARTIAL))),
        ModCategory("scripts", "SCRIPTS", listOf(module("library", "Script Library", "Manage workspace scripts.", ready), module("runner", "Lua Runtime", "Native Lua 5.4.9 execution.", ready))),
        ModCategory("lua", "LUA", listOf(module("console", "Script Console", "Lua workspace; not an Android shell.", CapabilityStatus.PARTIAL), module("api", "API Explorer", "Compatibility registry and status.", ready))),
        ModCategory("events", "EVENTS", listOf(module("event-bus", "Session Event Bus", "Isolated event bus foundation.", CapabilityStatus.PARTIAL))),
        ModCategory("safety", "SAFETY", listOf(module("watchdog", "Runtime Watchdog", "Monitor Lua completion, errors and emergency stop.", CapabilityStatus.PARTIAL))),
        ModCategory("accounts", "ACCOUNTS", listOf(module("accounts-list", "Account Profiles", "Account linkage awaits a trusted backend.", unavailable))),
        ModCategory("tools", "TOOLS", listOf(module("diagnostics", "Diagnostics", "Technical details and logs.", ready))),
        ModCategory("ui", "UI", listOf(module("menu-layout", "Menu Layout", "Category, module and detail navigation.", CapabilityStatus.PARTIAL))),
        ModCategory("database", "DATABASE", listOf(module("item-db", "Item Database", "Shared, versioned catalog architecture.", CapabilityStatus.PARTIAL))),
        ModCategory("diagnostics", "DIAGNOSTICS", listOf(module("logs", "Runtime Logs", "Filterable app runtime logs.", ready))),
        ModCategory("settings", "SETTINGS", listOf(module("overlay-permission", "Overlay Permission", "Android display-over-other-apps permission.", ready)))
    )

    fun category(id: String) = categories.firstOrNull { it.id == id }
    fun module(id: String) = categories.flatMap { it.modules }.firstOrNull { it.id == id }
    fun search(query: String) = categories.flatMap { category -> category.modules.filter { it.title.contains(query, true) || it.description.contains(query, true) }.map { category to it } }
}
