package id.zenithl.launcher.player

import id.zenithl.launcher.api.ApiStatus
import id.zenithl.launcher.world.StateResult

data class Player(val netId: Int, val userId: Int, val name: String, val x: Float, val y: Float)
data class LocalPlayer(val player: Player, val punchId: Int)
interface PlayerStateProvider { fun local(sessionId: String): StateResult<LocalPlayer?>; fun players(sessionId: String): StateResult<List<Player>> }
class InterfaceOnlyPlayerStateProvider : PlayerStateProvider {
    override fun local(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided player state for session $sessionId")
    override fun players(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided player state for session $sessionId")
}
class PlayerState(private val sessionId: String, private val provider: PlayerStateProvider) {
    fun getLocal() = provider.local(sessionId)
    fun getPlayerList() = provider.players(sessionId)
    fun getPlayerByNetID(netId: Int): StateResult<Player?> = when (val result = getPlayerList()) {
        is StateResult.Available -> StateResult.Available(result.value.firstOrNull { it.netId == netId })
        is StateResult.Unavailable -> result
    }
    fun findPlayer(name: String): StateResult<Player?> = when (val result = getPlayerList()) {
        is StateResult.Available -> StateResult.Available(result.value.firstOrNull { it.name.equals(name, true) })
        is StateResult.Unavailable -> result
    }
}
