package id.zenithl.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import id.zenithl.launcher.diagnostics.GameDetectionSnapshot
import id.zenithl.launcher.sessions.BotSession
import id.zenithl.launcher.ui.Ui
import java.util.concurrent.Executors

class DiagnosticsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "zenith-diagnostics") }
    private val main = Handler(Looper.getMainLooper())
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as ZenithApp).container
        val root = Ui.page(this)
        root.addView(Ui.title(this, "DIAGNOSTICS"))
        root.addView(Ui.label(this, "Technical runtime information. Game data is never synthesized."))
        root.addView(Ui.label(this, "Loading diagnostics…"))
        setContentView(Ui.scroll(this, root))

        var detection: GameDetectionSnapshot? = null
        var sessions: List<BotSession>? = null
        var catalog: String = "loading"
        fun update() {
            if (destroyed || detection == null || sessions == null || catalog == "loading") return
            root.removeAllViews()
            root.addView(Ui.title(this, "DIAGNOSTICS"))
            root.addView(Ui.section(this, "GAME"))
            root.addView(Ui.label(this, "Package: ${detection!!.packageName}"))
            root.addView(Ui.label(this, "Detection: ${detection!!.state}"))
            root.addView(Ui.label(this, "Version: ${detection!!.game?.versionName ?: "unknown"} • code ${detection!!.game?.versionCode ?: "unknown"}"))
            root.addView(Ui.label(this, "Compatibility: ${detection!!.compatibility.id} • ${if (detection!!.compatibility.supported) "verified" else "unverified"}"))
            root.addView(Ui.label(this, detection!!.compatibility.explanation))
            root.addView(Ui.section(this, "BRIDGE & RUNTIME"))
            root.addView(Ui.card(this, "Game Bridge", "${app.gameBridge.status} • ${app.gameBridge.diagnostic}"))
            root.addView(Ui.label(this, "Overlay permission: ${if (app.permissionManager.canDisplayOverlay()) "READY" else "MISSING"}"))
            root.addView(Ui.label(this, "Global item catalog: $catalog"))
            root.addView(Ui.label(this, "Sessions: ${sessions!!.size} • Lua states are isolated per script execution"))
            root.addView(Ui.section(this, "ZENITH SAFETY ENGINE"))
            val safety = app.safetyEngine.snapshot()
            root.addView(Ui.card(this, "Runtime Safety", if (safety.stoppedSessions.isEmpty()) "Monitoring • session-scoped stop controls" else "Stopped sessions: ${safety.stoppedSessions.size}"))
            val watches = app.safetyMonitor.snapshots()
            root.addView(Ui.card(this, "Script Watchdog", if (watches.isEmpty()) "Active • no running script to watch" else "Active • ${watches.size} running script(s)"))
            watches.forEach { watch ->
                root.addView(Ui.card(this, watch.scriptId, "${watch.sessionId} • ${watch.state} • ${watch.elapsedMs / 1000}s"))
                root.addView(Ui.button(this, "STOP SESSION ${watch.sessionId.take(8)}") {
                    app.emergencyStopSession(watch.sessionId) { result ->
                        result.onSuccess { recreate() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                    }
                })
            }
            root.addView(Ui.button(this, "STOP ALL SCRIPTS, SESSIONS & SCHEDULED TASKS") {
                app.emergencyStopAll { result -> result.onSuccess { recreate() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() } }
            })
            root.addView(Ui.section(this, "RECENT SAFETY INCIDENTS"))
            val incidents = app.safetyEngine.incidents().take(10)
            if (incidents.isEmpty()) root.addView(Ui.label(this, "No recorded safety incidents"))
            incidents.forEach { incident -> root.addView(Ui.card(this, "${incident.severity} • ${incident.type}", "${incident.message} • ${incident.timestamp}")) }
            root.addView(Ui.label(this, "Unavailable telemetry: native instruction counts, CPU pressure, API-call totals, and Game Bridge state monitoring require runtime/provider support. Safety does not assess ban or anti-cheat risk."))
            root.addView(Ui.button(this, "BACK") { finish() })
            setContentView(Ui.scroll(this, root))
        }
        worker.execute { val found = runCatching { app.gameDetector.detect() }.getOrNull(); main.post { detection = found ?: return@post; update() } }
        app.botController.list { result -> sessions = result.getOrDefault(emptyList()); update() }
        app.loadItemCatalog { result -> catalog = result.fold({ app.itemDatabase.version?.value?.toString() ?: "not installed" }, { "unavailable: ${it.message}" }); update() }
    }

    override fun onDestroy() { destroyed = true; worker.shutdownNow(); super.onDestroy() }
}
