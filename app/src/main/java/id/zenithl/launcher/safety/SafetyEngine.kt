package id.zenithl.launcher.safety

import java.util.concurrent.ConcurrentLinkedDeque

enum class SafetySeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
data class SafetyIncident(
    val id: Long,
    val sessionId: String?,
    val scriptId: String?,
    val severity: SafetySeverity,
    val type: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val accountId: String? = null,
    val source: String = "SafetyEngine",
    val stackTrace: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val resolved: Boolean = false,
    val resolution: String? = null,
)
data class SafetySnapshot(val stoppedSessions: Set<String>, val incidentCount: Int, val latestIncident: SafetyIncident?)

/** Runtime safety controls. It does not inspect or alter game traffic and does not claim game-policy detection. */
class SafetyEngine(
    private val maxIncidents: Int = 256,
    private val stopSession: (String) -> Unit = {},
    private val persistIncident: (SafetyIncident) -> Unit = {},
) {
    private val sequence = java.util.concurrent.atomic.AtomicLong()
    private val incidents = ConcurrentLinkedDeque<SafetyIncident>()
    private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init { require(maxIncidents > 0) }

    fun record(sessionId: String?, scriptId: String?, severity: SafetySeverity, code: String, message: String): SafetyIncident {
        require(code.isNotBlank() && message.isNotBlank())
        return SafetyIncident(sequence.incrementAndGet(), sessionId, scriptId, severity, code, message).also {
            incidents.addFirst(it)
            persistIncident(it)
            while (incidents.size > maxIncidents) incidents.pollLast()
        }
    }

    /** Stops the selected session only. Other sessions are not affected. */
    fun emergencyStop(sessionId: String, reason: String = "Manual emergency stop") {
        require(sessionId.isNotBlank())
        stopped.add(sessionId)
        record(sessionId, null, SafetySeverity.CRITICAL, "EMERGENCY_STOP", reason)
        stopSession(sessionId)
    }

    fun resetEmergencyStop(sessionId: String) { stopped.remove(sessionId) }
    fun isStopped(sessionId: String) = sessionId in stopped
    fun incidents(sessionId: String? = null, scriptId: String? = null): List<SafetyIncident> = incidents.filter {
        (sessionId == null || it.sessionId == sessionId) && (scriptId == null || it.scriptId == scriptId)
    }
    fun snapshot() = SafetySnapshot(stopped.toSet(), incidents.size, incidents.peekFirst())
}

enum class WatchdogState { RUNNING, PAUSED, STOPPING, STOPPED, ERROR, TIMEOUT, CRASHED }
enum class WatchdogAction { NONE, WARNING, THROTTLE, PAUSE, STOP }
data class WatchdogLimits(
    val warningAfterMs: Long = 30_000,
    val throttleAfterMs: Long = 60_000,
    val pauseAfterMs: Long = 90_000,
    val stopAfterMs: Long = 120_000,
    val maxApiCalls: Long = 100_000,
    val pollIntervalMs: Long = 500,
) {
    init {
        require(warningAfterMs >= 0 && throttleAfterMs >= warningAfterMs)
        require(pauseAfterMs >= throttleAfterMs && stopAfterMs >= pauseAfterMs)
        require(maxApiCalls > 0 && pollIntervalMs > 0)
    }
}
data class ScriptWatchdogSnapshot(
    val sessionId: String,
    val scriptId: String,
    val state: WatchdogState,
    val startedAt: Long,
    val elapsedMs: Long,
    val apiCalls: Long,
    val lastHeartbeatAt: Long?,
    val lastAction: WatchdogAction,
)
interface ScriptWatchdog {
    fun start(sessionId: String, scriptId: String)
    fun setPaused(sessionId: String, paused: Boolean)
    fun state(sessionId: String): WatchdogState?
    fun complete(sessionId: String, state: WatchdogState = WatchdogState.STOPPED)
}

/** Wall-clock watchdog with explicit escalation. Lua instruction/API telemetry is reported only when supplied by runtime adapters. */
class SafetyMonitor(
    private val engine: SafetyEngine,
    private val limits: WatchdogLimits = WatchdogLimits(),
    private val pause: (String) -> Unit,
    private val resume: (String) -> Unit,
    private val stop: (String) -> Unit,
) : AutoCloseable, ScriptWatchdog {
    private data class Run(
        val sessionId: String,
        val scriptId: String,
        val startedAt: Long = System.currentTimeMillis(),
        @Volatile var state: WatchdogState = WatchdogState.RUNNING,
        @Volatile var apiCalls: Long = 0,
        @Volatile var lastHeartbeatAt: Long? = null,
        @Volatile var lastAction: WatchdogAction = WatchdogAction.NONE,
    )
    private val runs = java.util.concurrent.ConcurrentHashMap<String, Run>()
    private val worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "zenith-safety-monitor").apply { isDaemon = true } }

    init { worker.scheduleWithFixedDelay(::checkRuns, limits.pollIntervalMs, limits.pollIntervalMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    override fun start(sessionId: String, scriptId: String) {
        require(sessionId.isNotBlank() && scriptId.isNotBlank())
        val run = Run(sessionId, scriptId)
        check(runs.putIfAbsent(sessionId, run) == null) { "A watchdog already exists for session $sessionId" }
        engine.record(sessionId, scriptId, SafetySeverity.INFO, "SCRIPT_STARTED", "Script watchdog started")
    }
    fun heartbeat(sessionId: String) { runs[sessionId]?.lastHeartbeatAt = System.currentTimeMillis() }
    fun recordApiCall(sessionId: String, count: Long = 1) {
        require(count > 0)
        runs[sessionId]?.let { run ->
            run.apiCalls += count
            if (run.apiCalls > limits.maxApiCalls && run.lastAction.ordinal < WatchdogAction.STOP.ordinal) {
                run.lastAction = WatchdogAction.STOP; run.state = WatchdogState.TIMEOUT
                engine.record(sessionId, run.scriptId, SafetySeverity.CRITICAL, "API_CALL_LIMIT", "Configured script API-call limit exceeded")
                stop(sessionId)
            }
        }
    }
    override fun setPaused(sessionId: String, paused: Boolean) { runs[sessionId]?.state = if (paused) WatchdogState.PAUSED else WatchdogState.RUNNING }
    override fun state(sessionId: String): WatchdogState? = runs[sessionId]?.state
    override fun complete(sessionId: String, state: WatchdogState) { runs.remove(sessionId)?.state = state }
    fun snapshots(): List<ScriptWatchdogSnapshot> = runs.values.map { it.snapshot() }.sortedBy { it.startedAt }

    private fun checkRuns() {
        val now = System.currentTimeMillis()
        runs.values.forEach { run ->
            if (run.state !in setOf(WatchdogState.RUNNING, WatchdogState.PAUSED)) return@forEach
            val elapsed = now - run.startedAt
            if (elapsed >= limits.warningAfterMs && run.lastAction.ordinal < WatchdogAction.WARNING.ordinal) {
                run.lastAction = WatchdogAction.WARNING
                engine.record(run.sessionId, run.scriptId, SafetySeverity.LOW, "SCRIPT_RUNTIME_WARNING", "Script runtime exceeded the warning threshold")
            }
            if (elapsed >= limits.throttleAfterMs && run.lastAction.ordinal < WatchdogAction.THROTTLE.ordinal) {
                run.lastAction = WatchdogAction.THROTTLE
                engine.record(run.sessionId, run.scriptId, SafetySeverity.MEDIUM, "SCRIPT_THROTTLED", "Script was briefly throttled by the watchdog")
                pause(run.sessionId)
                worker.schedule({ if (runs[run.sessionId] === run && run.state == WatchdogState.RUNNING) resume(run.sessionId) }, 250, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            if (elapsed >= limits.pauseAfterMs && run.lastAction.ordinal < WatchdogAction.PAUSE.ordinal) {
                run.lastAction = WatchdogAction.PAUSE; run.state = WatchdogState.PAUSED
                engine.record(run.sessionId, run.scriptId, SafetySeverity.HIGH, "SCRIPT_PAUSED_BY_WATCHDOG", "Script paused after the runtime warning window")
                pause(run.sessionId)
            }
            if (elapsed >= limits.stopAfterMs && run.lastAction.ordinal < WatchdogAction.STOP.ordinal) {
                run.lastAction = WatchdogAction.STOP; run.state = WatchdogState.TIMEOUT
                engine.record(run.sessionId, run.scriptId, SafetySeverity.CRITICAL, "SCRIPT_TIMEOUT", "Script exceeded its configured wall-clock runtime")
                stop(run.sessionId)
            }
        }
    }
    private fun Run.snapshot() = ScriptWatchdogSnapshot(sessionId, scriptId, state, startedAt,
        System.currentTimeMillis() - startedAt, apiCalls, lastHeartbeatAt, lastAction)
    override fun close() { worker.shutdownNow(); runs.clear() }
}
