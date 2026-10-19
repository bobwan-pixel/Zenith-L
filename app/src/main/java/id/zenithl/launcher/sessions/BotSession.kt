package id.zenithl.launcher.sessions

import id.zenithl.launcher.hooks.EventBus
import id.zenithl.launcher.hooks.HookRegistry
import id.zenithl.launcher.inventory.InterfaceOnlyInventoryStateProvider
import id.zenithl.launcher.inventory.InventoryState
import id.zenithl.launcher.objects.InterfaceOnlyObjectStateProvider
import id.zenithl.launcher.objects.ObjectState
import id.zenithl.launcher.player.InterfaceOnlyPlayerStateProvider
import id.zenithl.launcher.player.PlayerState
import id.zenithl.launcher.world.InterfaceOnlyWorldStateProvider
import id.zenithl.launcher.world.WorldScanner
import id.zenithl.launcher.world.WorldState
import java.util.UUID

enum class BotState { CREATED, STARTING, RUNNING, PAUSED, STOPPING, STOPPED, ERROR }
data class BotProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val world: String? = null,
    val accountLabel: String? = null,
    val accountId: String? = null,
    val scriptId: String? = null,
    val autoRestart: Boolean = false,
    val restartDelayMs: Long = 5_000,
    val maxRestartCount: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Each session owns its state providers and event bus; these are never global singletons. */
class BotSession(@Volatile var profile: BotProfile) {
    val id = profile.id
    @Volatile var state: BotState = BotState.CREATED
        private set
    val events = EventBus(id)
    val hooks = HookRegistry(events)
    val worldState = WorldState(id, InterfaceOnlyWorldStateProvider())
    val worldScanner = WorldScanner(worldState)
    val playerState = PlayerState(id, InterfaceOnlyPlayerStateProvider())
    val inventoryState = InventoryState(id, InterfaceOnlyInventoryStateProvider())
    val objectState = ObjectState(id, InterfaceOnlyObjectStateProvider())

    @Synchronized fun transition(next: BotState) {
        val allowed = when (state) {
            BotState.CREATED -> setOf(BotState.STARTING, BotState.STOPPED)
            BotState.STARTING -> setOf(BotState.RUNNING, BotState.ERROR, BotState.STOPPING)
            BotState.RUNNING -> setOf(BotState.PAUSED, BotState.STOPPING, BotState.ERROR)
            BotState.PAUSED -> setOf(BotState.RUNNING, BotState.STOPPING, BotState.ERROR)
            BotState.STOPPING -> setOf(BotState.STOPPED, BotState.ERROR)
            BotState.STOPPED -> setOf(BotState.STARTING)
            BotState.ERROR -> setOf(BotState.STARTING, BotState.STOPPED)
        }
        require(next in allowed) { "Invalid bot session transition $state -> $next" }
        state = next
        if (next == BotState.STOPPED || next == BotState.ERROR) events.clear()
    }
    fun cleanup() { events.clear() }
}

interface BotProfileRepository { fun all(): List<BotProfile>; fun save(profile: BotProfile); fun delete(id: String) }
class SessionManager(private val repository: BotProfileRepository? = null) {
    private val sessions = LinkedHashMap<String, BotSession>()
    init { repository?.all()?.take(MAX_SESSIONS)?.forEach { sessions[it.id] = BotSession(it) } }
    @Synchronized fun create(profile: BotProfile): BotSession {
        require(sessions.size < MAX_SESSIONS) { "Maximum supported sessions is $MAX_SESSIONS" }
        require(profile.id !in sessions) { "Session ${profile.id} already exists" }
        return BotSession(profile).also { sessions[it.id] = it; repository?.save(profile) }
    }
    @Synchronized fun get(id: String): BotSession? = sessions[id]
    @Synchronized fun all(): List<BotSession> = sessions.values.toList()
    @Synchronized fun remove(id: String): BotSession? = sessions.remove(id)?.also { it.cleanup(); repository?.delete(id) }
    @Synchronized fun attachScript(id: String, scriptId: String?) { sessions[id]?.let { it.profile = it.profile.copy(scriptId = scriptId); repository?.save(it.profile) } }
    companion object { const val MAX_SESSIONS = 3 }
}

class BotManager(private val sessionManager: SessionManager = SessionManager()) {
    fun create(profile: BotProfile) = sessionManager.create(profile)
    fun getBots() = sessionManager.all()
    fun getBot(id: String) = sessionManager.get(id)
    fun startBot(id: String): Boolean = sessionManager.get(id)?.let { session ->
        if (session.state !in setOf(BotState.CREATED, BotState.STOPPED, BotState.ERROR)) return false
        // No Game Bridge: do not claim that the account/session has started.
        false
    } ?: false
    fun stopBot(id: String): Boolean = sessionManager.get(id)?.let { session ->
        if (session.state == BotState.STOPPED) return true
        if (session.state == BotState.CREATED) session.transition(BotState.STOPPED)
        else if (session.state in setOf(BotState.STARTING, BotState.RUNNING, BotState.PAUSED)) {
            session.transition(BotState.STOPPING); session.transition(BotState.STOPPED)
        }
        true
    } ?: false
    fun pauseBot(id: String): Boolean = sessionManager.get(id)?.let { if (it.state != BotState.RUNNING) false else { it.transition(BotState.PAUSED); true } } ?: false
    fun restartBot(id: String): Boolean = stopBot(id) && startBot(id)
    fun stopAll(): Int = sessionManager.all().count { stopBot(it.id) }
    fun attachScript(id: String, scriptId: String?) = sessionManager.attachScript(id, scriptId)
    fun removeBot(id: String) = sessionManager.remove(id)
}
