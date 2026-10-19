package id.zenithl.launcher.scripts

import id.zenithl.launcher.logging.LogEntry
import id.zenithl.launcher.logging.LogLevel
import id.zenithl.launcher.logging.Logger
import id.zenithl.launcher.lua.LuaExecutionEngine
import id.zenithl.launcher.safety.ScriptWatchdog
import id.zenithl.launcher.safety.WatchdogState
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ScriptState { DISABLED, ENABLED, STARTING, RUNNING, PAUSED, STOPPING, ERROR, TIMEOUT }
data class ScriptProfile(val values: Map<String, String> = emptyMap())
data class ScriptRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val enabled: Boolean = false,
    val autoRun: Boolean = false,
    val favorite: Boolean = false,
    val state: ScriptState = ScriptState.DISABLED,
    val lastRun: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val profile: ScriptProfile = ScriptProfile(),
)
interface ScriptRepository {
    fun all(): List<ScriptRecord>
    fun get(id: String): ScriptRecord?
    fun save(record: ScriptRecord)
    fun delete(id: String)
}

data class ScriptError(val script: String, val file: String, val line: Int?, val message: String, val stackTrace: String, val timestamp: Long, val sessionId: String)

/** Single source of truth for script metadata and execution lifecycle. */
class ScriptManager(
    private val repository: ScriptRepository,
    private val workspace: File,
    private val engine: LuaExecutionEngine,
    private val logger: Logger,
    private val watchdog: ScriptWatchdog? = null,
) {
    private data class ActiveRun(val scriptId: String, val token: String = UUID.randomUUID().toString())
    private val running = ConcurrentHashMap<String, ActiveRun>() // session -> isolated run

    fun list(): List<ScriptRecord> = repository.all()
    fun stopAll() { running.keys.toList().forEach(::stop) }
    fun search(query: String): List<ScriptRecord> = list().filter { it.name.contains(query, true) }
    fun sort(favoritesFirst: Boolean = false): List<ScriptRecord> = list().sortedWith(compareByDescending<ScriptRecord> { favoritesFirst && it.favorite }.thenBy { it.name.lowercase() })

    @Synchronized fun create(name: String, source: String = ""): ScriptRecord {
        val clean = validateName(name)
        require(list().none { it.name.equals(clean, true) }) { "A script named '$clean' already exists" }
        workspace.mkdirs()
        val file = File(workspace, "$clean.lua").canonicalFile
        require(file.parentFile == workspace.canonicalFile) { "Script path must stay inside the workspace" }
        file.writeText(source)
        return ScriptRecord(name = clean, path = file.absolutePath).also(repository::save)
    }

    @Synchronized fun rename(id: String, name: String): ScriptRecord {
        val old = required(id)
        check(running.values.none { it.scriptId == id }) { "Stop the script before renaming it" }
        val clean = validateName(name)
        require(list().none { it.id != id && it.name.equals(clean, true) }) { "A script named '$clean' already exists" }
        val target = File(workspace, "$clean.lua").canonicalFile
        require(target.parentFile == workspace.canonicalFile) { "Script path must stay inside the workspace" }
        require(File(old.path).renameTo(target)) { "Could not rename script file" }
        return old.copy(name = clean, path = target.absolutePath, updatedAt = System.currentTimeMillis()).also(repository::save)
    }

    @Synchronized fun duplicate(id: String, newName: String): ScriptRecord {
        val source = required(id)
        val clean = validateName(newName)
        require(list().none { it.name.equals(clean, true) }) { "A script named '$clean' already exists" }
        val target = File(workspace, "$clean.lua").canonicalFile
        File(source.path).copyTo(target, overwrite = false)
        return ScriptRecord(name = clean, path = target.absolutePath, enabled = false, profile = source.profile).also(repository::save)
    }

    @Synchronized fun delete(id: String) {
        val script = required(id)
        running.entries.filter { it.value.scriptId == id }.forEach { (session, active) ->
            engine.stop(session)
            running.remove(session, active)
        }
        File(script.path).delete()
        repository.delete(id)
    }

    fun importScript(source: File, name: String = source.nameWithoutExtension): ScriptRecord = create(name, source.readText())
    fun exportScript(id: String, destination: File) { File(required(id).path).copyTo(destination, overwrite = true) }
    fun updateProfile(id: String, profile: ScriptProfile) = update(id) { it.copy(profile = profile, updatedAt = System.currentTimeMillis()) }
    fun saveSource(id: String?, name: String, source: String): ScriptRecord {
        if (id == null) return create(name, source)
        val initial = required(id)
        check(running.values.none { it.scriptId == id }) { "Stop the script before editing its source" }
        val old = if (initial.name.equals(validateName(name), true)) initial else rename(id, name)
        File(old.path).writeText(source)
        return old.copy(updatedAt = System.currentTimeMillis()).also(repository::save)
    }
    fun openDocument(id: String): Pair<ScriptRecord, String> = required(id).let { it to File(it.path).readText() }

    fun setEnabled(id: String, enabled: Boolean) {
        val script = required(id)
        if (!enabled) running.entries.filter { it.value.scriptId == id }.forEach { stop(it.key) }
        update(id) { it.copy(enabled = enabled, state = if (enabled) ScriptState.ENABLED else ScriptState.DISABLED, updatedAt = System.currentTimeMillis()) }
    }
    fun setAutoRun(id: String, autoRun: Boolean) = update(id) { it.copy(autoRun = autoRun, updatedAt = System.currentTimeMillis()) }
    fun setFavorite(id: String, favorite: Boolean) = update(id) { it.copy(favorite = favorite, updatedAt = System.currentTimeMillis()) }

    @Synchronized fun run(id: String, sessionId: String) {
        val script = required(id)
        check(script.enabled) { "Disabled scripts cannot be run" }
        check(!running.containsKey(sessionId)) { "A script is already active for session $sessionId" }
        val source = File(script.path).readText()
        check(source.isNotBlank()) { "Script is empty" }
        val active = ActiveRun(id)
        running[sessionId] = active
        watchdog?.start(sessionId, id)
        saveState(id, ScriptState.STARTING, error = null)
        engine.execute(sessionId, id, source, script.name, object : LuaExecutionEngine.Listener {
            override fun onStarted() {
                if (running[sessionId] != active) return
                update(id) { it.copy(state = ScriptState.RUNNING, lastRun = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
                logger.log(LogEntry(level = LogLevel.INFO, component = "ScriptManager", message = "Script started: ${script.name}", scriptId = id, sessionId = sessionId))
            }
            override fun onCompleted() { watchdog?.complete(sessionId); finishIfCurrent(sessionId, active, ScriptState.ENABLED, "Script completed: ${script.name}") }
            override fun onStopped() {
                val timedOut = watchdog?.state(sessionId) == WatchdogState.TIMEOUT
                watchdog?.complete(sessionId, if (timedOut) WatchdogState.TIMEOUT else WatchdogState.STOPPED)
                finishIfCurrent(sessionId, active, if (timedOut) ScriptState.TIMEOUT else if (required(id).enabled) ScriptState.ENABLED else ScriptState.DISABLED,
                    if (timedOut) "Script exceeded watchdog limit: ${script.name}" else "Script stopped: ${script.name}")
            }
            override fun onError(trace: String) {
                if (running[sessionId] != active) return
                watchdog?.reportError(sessionId, trace.lineSequence().firstOrNull().orEmpty(), trace)
                val line = Regex("\\.lua:(\\d+):").find(trace)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val error = ScriptError(script.name, script.path, line, trace.lineSequence().firstOrNull().orEmpty(), trace, System.currentTimeMillis(), sessionId)
                val timeout = trace.contains("runtime budget", ignoreCase = true) || watchdog?.state(sessionId) == WatchdogState.TIMEOUT
                watchdog?.complete(sessionId, if (timeout) WatchdogState.TIMEOUT else WatchdogState.ERROR)
                saveState(id, if (timeout) ScriptState.TIMEOUT else ScriptState.ERROR, error.toJson())
                running.remove(sessionId, active)
                logger.log(LogEntry(level = LogLevel.ERROR, component = "LuaRuntime", message = error.message, scriptId = id, sessionId = sessionId, throwable = error.stackTrace))
            }
        })
    }

    fun pause(sessionId: String) {
        val id = running[sessionId]?.scriptId ?: return
        engine.pause(sessionId)
        watchdog?.setPaused(sessionId, true)
        saveState(id, ScriptState.PAUSED)
    }
    fun resume(sessionId: String) {
        val id = running[sessionId]?.scriptId ?: return
        engine.resume(sessionId)
        watchdog?.setPaused(sessionId, false)
        saveState(id, ScriptState.RUNNING)
    }
    fun stop(sessionId: String) {
        val id = running[sessionId]?.scriptId ?: return
        saveState(id, ScriptState.STOPPING)
        watchdog?.setPaused(sessionId, true)
        engine.stop(sessionId)
    }
    @Synchronized fun restart(id: String, sessionId: String) {
        val active = running.remove(sessionId)
        if (active != null) engine.stop(sessionId)
        run(id, sessionId)
    }
    fun closeSession(sessionId: String) { stop(sessionId); engine.close(sessionId); running.remove(sessionId) }

    private fun finishIfCurrent(sessionId: String, active: ActiveRun, state: ScriptState, message: String) {
        if (running[sessionId] != active) return
        saveState(active.scriptId, state)
        running.remove(sessionId, active)
        logger.log(LogEntry(level = LogLevel.INFO, component = "ScriptManager", message = message, scriptId = active.scriptId, sessionId = sessionId))
    }
    private fun saveState(id: String, state: ScriptState, error: String? = null) = update(id) { it.copy(state = state, lastError = error, updatedAt = System.currentTimeMillis()) }
    private fun update(id: String, change: (ScriptRecord) -> ScriptRecord) { repository.get(id)?.let { repository.save(change(it)) } }
    private fun required(id: String) = requireNotNull(repository.get(id)) { "Unknown script: $id" }
    private fun validateName(name: String): String {
        val clean = name.trim().removeSuffix(".lua")
        require(clean.matches(Regex("[A-Za-z0-9 _.-]{1,64}"))) { "Invalid script name" }
        return clean
    }

    private fun ScriptError.toJson(): String =
        "{\"script\":\"${script.escapeJson()}\",\"file\":\"${file.escapeJson()}\",\"line\":${line ?: "null"}," +
            "\"message\":\"${message.escapeJson()}\",\"stackTrace\":\"${stackTrace.escapeJson()}\",\"timestamp\":$timestamp,\"session\":\"${sessionId.escapeJson()}\"}"
    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
