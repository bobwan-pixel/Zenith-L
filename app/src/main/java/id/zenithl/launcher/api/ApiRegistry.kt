package id.zenithl.launcher.api

enum class ApiStatus { IMPLEMENTED, PARTIAL, INTERFACE_ONLY, NOT_IMPLEMENTED }
data class ApiParameter(val name: String, val type: String, val optional: Boolean = false)
data class ApiDefinition(
    val name: String,
    val description: String,
    val parameters: List<ApiParameter>,
    val returnType: String,
    val example: String,
    val compatibility: String,
    val status: ApiStatus,
)

object ApiRegistry {
    const val API_VERSION = "0.4"
    const val GL_COMPATIBILITY_VERSION = "community-reference-2025-08 (unverified)"

    val definitions: List<ApiDefinition> = buildList {
        add(ApiDefinition("Zenith.getVersion", "Return Zenith'L application version.", emptyList(), "string", "Zenith.getVersion()", "Zenith extension", ApiStatus.IMPLEMENTED))
        val zenithNames = listOf(
            "getRuntimeInfo", "getGameInfo", "getGameVersion", "getBridgeStatus", "getSessionInfo", "getSessions",
            "getBots", "getBot", "startBot", "stopBot", "pauseBot", "restartBot",
        ) + listOf("world", "player", "inventory", "objects", "npc", "session", "script", "database", "bridge", "diagnostics").map { "$it.*" }
        zenithNames.forEach { name ->
            add(ApiDefinition("Zenith.$name", "Zenith runtime extension; requires its Lua adapter/provider.", emptyList(), "varies", "Zenith.$name(...) -- capability status is reported here", "Zenith extension API 0.4", ApiStatus.INTERFACE_ONLY))
        }
        val gl = listOf(
            ApiDefinition("getTile", "Return a tile at world coordinates.", listOf(ApiParameter("x", "integer"), ApiParameter("y", "integer")), "Tile?", "getTile(10, 20)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getTiles", "Return tiles from the current world snapshot.", emptyList(), "Tile[]", "getTiles()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getWorldName", "Return the current world name.", emptyList(), "string?", "getWorldName()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getWorld", "Return the current session world snapshot.", emptyList(), "World?", "getWorld()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getInventory", "Return inventory state for this session.", emptyList(), "InventoryItem[]", "getInventory()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getLocal", "Return local player state for this session.", emptyList(), "LocalPlayer?", "getLocal()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getPlayerList", "Return players for this session.", emptyList(), "Player[]", "getPlayerList()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getObjectList", "Return world objects for this session.", emptyList(), "WorldObject[]", "getObjectList()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getNPCList", "Return NPC state for this session.", emptyList(), "NPC[]", "getNPCList()", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("findItemID", "Find item ID by exact case-insensitive item name.", listOf(ApiParameter("name", "string")), "integer?", "findItemID(\"Dirt\")", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("getItemInfoByID", "Look up cached item metadata.", listOf(ApiParameter("id", "integer")), "ItemInfo?", "getItemInfoByID(2)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("FindPath", "Pathfinding request for the active session.", listOf(ApiParameter("x", "integer"), ApiParameter("y", "integer")), "boolean", "FindPath(10, 20)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("Sleep", "Pause the current script coroutine.", listOf(ApiParameter("milliseconds", "integer")), "void", "Sleep(1000)", ApiStatus.NOT_IMPLEMENTED),
            ApiDefinition("runThread", "Start a child Lua task in the current runtime.", listOf(ApiParameter("function", "function"), ApiParameter("args", "any", true)), "task", "runThread(function() end)", ApiStatus.NOT_IMPLEMENTED),
            ApiDefinition("runCoroutine", "Start a coroutine in the current runtime.", listOf(ApiParameter("function", "function"), ApiParameter("args", "any", true)), "thread", "runCoroutine(function() end)", ApiStatus.NOT_IMPLEMENTED),
            ApiDefinition("AddHook", "Subscribe to a session-scoped game event.", listOf(ApiParameter("callback", "function"), ApiParameter("event", "string")), "hookId", "AddHook(function(e) end, \"OnPacket\")", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("removeHook", "Remove a hook subscription.", listOf(ApiParameter("hookId", "integer|string")), "void", "removeHook(id)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("SendPacket", "Send a game text packet through GameBridge.", listOf(ApiParameter("type", "integer"), ApiParameter("packet", "string")), "boolean", "SendPacket(2, packet)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("SendPacketRaw", "Send a raw game packet through GameBridge.", listOf(ApiParameter("packet", "PacketRaw")), "boolean", "SendPacketRaw(packet)", ApiStatus.INTERFACE_ONLY),
            ApiDefinition("SendVariant", "Send a game variant through GameBridge.", listOf(ApiParameter("variant", "VariantList")), "boolean", "SendVariant(variant)", ApiStatus.INTERFACE_ONLY),
        )
        gl.forEach { add(it.copy(compatibility = "GL Lua community reference (2025-08)")) }
    }

    fun search(prefix: String): List<ApiDefinition> = definitions.filter { it.name.startsWith(prefix, ignoreCase = true) }
    fun find(name: String): ApiDefinition? = definitions.firstOrNull { it.name == name }
}

private fun ApiDefinition(name: String, description: String, parameters: List<ApiParameter>, returnType: String, example: String, status: ApiStatus) =
    ApiDefinition(name, description, parameters, returnType, example, "GL Lua API reference", status)
