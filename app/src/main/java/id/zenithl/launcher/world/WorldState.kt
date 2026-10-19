package id.zenithl.launcher.world

import id.zenithl.launcher.api.ApiStatus

data class Tile(val x: Int, val y: Int, val fg: Int, val bg: Int, val flags: Int)
data class WorldSnapshot(val name: String, val width: Int, val height: Int, val tiles: List<Tile>, val ready: Boolean)
sealed interface StateResult<out T> {
    data class Available<T>(val value: T) : StateResult<T>
    data class Unavailable(val status: ApiStatus, val reason: String) : StateResult<Nothing>
}

interface WorldStateProvider {
    fun snapshot(sessionId: String): StateResult<WorldSnapshot>
}

class InterfaceOnlyWorldStateProvider : WorldStateProvider {
    override fun snapshot(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided world state for session $sessionId")
}

class WorldState(private val sessionId: String, private val provider: WorldStateProvider) {
    fun getTile(x: Int, y: Int): StateResult<Tile?> = when (val result = provider.snapshot(sessionId)) {
        is StateResult.Available -> StateResult.Available(result.value.tiles.firstOrNull { it.x == x && it.y == y })
        is StateResult.Unavailable -> result
    }
    fun findTile(x: Int, y: Int) = getTile(x, y)
    fun getTiles(): StateResult<List<Tile>> = when (val result = provider.snapshot(sessionId)) {
        is StateResult.Available -> StateResult.Available(result.value.tiles.toList())
        is StateResult.Unavailable -> result
    }
    fun findTiles(itemId: Int): StateResult<List<Tile>> = when (val result = getTiles()) {
        is StateResult.Available -> StateResult.Available(result.value.filter { it.fg == itemId || it.bg == itemId })
        is StateResult.Unavailable -> result
    }
}

class WorldScanner(private val worldState: WorldState) {
    fun findTile(x: Int, y: Int) = worldState.findTile(x, y)
    fun findTiles(itemId: Int) = worldState.findTiles(itemId)
    fun findTilesByForeground(itemId: Int) = filter { it.fg == itemId }
    fun findTilesByBackground(itemId: Int) = filter { it.bg == itemId }
    private fun filter(predicate: (Tile) -> Boolean): StateResult<List<Tile>> = when (val tiles = worldState.getTiles()) {
        is StateResult.Available -> StateResult.Available(tiles.value.filter(predicate))
        is StateResult.Unavailable -> tiles
    }
}
