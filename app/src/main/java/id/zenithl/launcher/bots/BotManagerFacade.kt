package id.zenithl.launcher.bots

import id.zenithl.launcher.sessions.BotManager
import id.zenithl.launcher.sessions.BotProfile

/** Stable manager boundary for future Lua Zenith.* bindings; Game Bridge is still absent. */
class BotManagerFacade(private val manager: BotManager) {
    fun getBots(): List<BotProfile> = manager.getBots().map { it.profile }
    fun getBot(id: String): BotProfile? = manager.getBot(id)?.profile
    fun startBot(id: String): Boolean = manager.startBot(id)
    fun stopBot(id: String): Boolean = manager.stopBot(id)
    fun pauseBot(id: String): Boolean = manager.pauseBot(id)
    fun restartBot(id: String): Boolean = manager.restartBot(id)
}
