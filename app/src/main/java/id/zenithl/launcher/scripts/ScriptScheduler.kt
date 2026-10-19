package id.zenithl.launcher.scripts

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class SchedulerTrigger { IMMEDIATE, GAME_BRIDGE_READY, SESSION_CREATED, WORLD_READY, AFTER_SCRIPT }
data class ScheduleRule(
    val scriptId: String,
    val trigger: SchedulerTrigger,
    val delayMs: Long = 0,
    val afterScriptId: String? = null,
    val enabled: Boolean = true,
)
interface ScheduleRuleStore { fun load(): List<ScheduleRule>; fun save(rules: List<ScheduleRule>) }

/** All dispatch occurs on a bounded scheduler worker; callers may invoke lifecycle events from any thread. */
class ScriptScheduler(private val manager: ScriptManager, private val store: ScheduleRuleStore? = null) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "zenith-scheduler") }
    private val rules = java.util.concurrent.CopyOnWriteArrayList<ScheduleRule>()
    private val delayed = java.util.concurrent.ConcurrentHashMap<String, ScheduledFuture<*>>()
    init { worker.execute { rules.addAll(store?.load().orEmpty()) } }
    fun replaceRules(newRules: List<ScheduleRule>) { require(newRules.all { it.delayMs >= 0 }); rules.clear(); rules.addAll(newRules); store?.save(newRules) }
    fun cancelAll() { delayed.values.forEach { it.cancel(false) }; delayed.clear() }
    fun cancelSession(sessionId: String) {
        val prefix = "$sessionId:"
        delayed.keys.filter { it.startsWith(prefix) }.forEach { key -> delayed.remove(key)?.cancel(false) }
    }
    fun onGameBridgeReady(sessionId: String) = dispatch(SchedulerTrigger.GAME_BRIDGE_READY, sessionId)
    fun onSessionCreated(sessionId: String) = dispatch(SchedulerTrigger.SESSION_CREATED, sessionId)
    fun onWorldReady(sessionId: String) = dispatch(SchedulerTrigger.WORLD_READY, sessionId)
    fun onScriptFinished(scriptId: String, sessionId: String) =
        rules.filter { it.enabled && it.trigger == SchedulerTrigger.AFTER_SCRIPT && it.afterScriptId == scriptId }.forEach { schedule(it, sessionId) }
    fun runImmediately(scriptId: String, sessionId: String, delayMs: Long = 0) {
        val record = manager.list().firstOrNull { it.id == scriptId } ?: error("Unknown script")
        check(record.enabled) { "Disabled scripts cannot be scheduled" }
        schedule(ScheduleRule(scriptId, SchedulerTrigger.IMMEDIATE, delayMs), sessionId)
    }
    private fun dispatch(trigger: SchedulerTrigger, sessionId: String) {
        rules.filter { it.enabled && it.trigger == trigger }.forEach { rule ->
            val record = manager.list().firstOrNull { it.id == rule.scriptId }
            if (record?.enabled == true && (trigger != SchedulerTrigger.GAME_BRIDGE_READY || record.autoRun)) schedule(rule, sessionId)
        }
    }
    private fun schedule(rule: ScheduleRule, sessionId: String) {
        val key = "$sessionId:${rule.scriptId}"
        delayed.remove(key)?.cancel(false)
        delayed[key] = worker.schedule({ delayed.remove(key); runCatching { manager.run(rule.scriptId, sessionId) } }, rule.delayMs, TimeUnit.MILLISECONDS)
    }
    override fun close() { cancelAll(); worker.shutdownNow() }
}

class CrashRecovery(private val manager: ScriptManager) {
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    fun onSessionFailure(
        sessionId: String,
        profile: id.zenithl.launcher.sessions.BotProfile,
        schedule: (Long, () -> Unit) -> Unit,
        restartSession: (String) -> Unit,
    ): Boolean {
        if (!profile.autoRestart) return false
        val count = attempts.merge(sessionId, 1, Int::plus) ?: 1
        if (count > profile.maxRestartCount.coerceAtLeast(0)) return false
        schedule(profile.restartDelayMs.coerceAtLeast(0)) { manager.closeSession(sessionId); restartSession(sessionId) }
        return true
    }
    fun onSessionHealthy(sessionId: String) { attempts.remove(sessionId) }
}
