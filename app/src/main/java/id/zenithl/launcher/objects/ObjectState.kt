package id.zenithl.launcher.objects

import id.zenithl.launcher.api.ApiStatus
import id.zenithl.launcher.world.StateResult

data class WorldObject(val id: Int, val itemId: Int, val x: Float, val y: Float, val amount: Int)
data class NPC(val id: Int, val type: Int, val x: Float, val y: Float)
interface ObjectStateProvider { fun objects(sessionId: String): StateResult<List<WorldObject>>; fun npcs(sessionId: String): StateResult<List<NPC>> }
class InterfaceOnlyObjectStateProvider : ObjectStateProvider {
    override fun objects(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided object state for session $sessionId")
    override fun npcs(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided NPC state for session $sessionId")
}
class ObjectState(private val sessionId: String, private val provider: ObjectStateProvider) {
    fun getObjectList() = provider.objects(sessionId)
    fun getNPCList() = provider.npcs(sessionId)
}
