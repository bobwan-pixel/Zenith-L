package id.zenithl.launcher.inventory

import id.zenithl.launcher.api.ApiStatus
import id.zenithl.launcher.world.StateResult

data class InventoryItem(val id: Int, val amount: Int)
interface InventoryStateProvider { fun inventory(sessionId: String): StateResult<List<InventoryItem>> }
class InterfaceOnlyInventoryStateProvider : InventoryStateProvider {
    override fun inventory(sessionId: String) = StateResult.Unavailable(ApiStatus.INTERFACE_ONLY, "Game Bridge has not provided inventory state for session $sessionId")
}
class InventoryState(private val sessionId: String, private val provider: InventoryStateProvider) {
    fun getInventory() = provider.inventory(sessionId)
    fun getItemCount(itemId: Int): StateResult<Int> = when (val items = getInventory()) {
        is StateResult.Available -> StateResult.Available(items.value.firstOrNull { it.id == itemId }?.amount ?: 0)
        is StateResult.Unavailable -> items
    }
    fun hasItem(itemId: Int): StateResult<Boolean> = when (val count = getItemCount(itemId)) {
        is StateResult.Available -> StateResult.Available(count.value > 0)
        is StateResult.Unavailable -> count
    }
}
