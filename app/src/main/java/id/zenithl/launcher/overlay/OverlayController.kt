package id.zenithl.launcher.overlay

import id.zenithl.launcher.storage.AppDatabase
import java.util.concurrent.Executors

data class OverlayPreferences(val selectedScriptId: String?, val compact: Boolean)
class OverlayController(private val database: AppDatabase) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zenith-overlay-settings") }
    fun load(callback: (OverlayPreferences) -> Unit) = worker.execute {
        callback(OverlayPreferences(database.getSetting(KEY_SCRIPT), database.getSetting(KEY_COMPACT) == "1"))
    }
    fun selectScript(id: String?) = worker.execute { if (id == null) database.setSetting(KEY_SCRIPT, "") else database.setSetting(KEY_SCRIPT, id) }
    fun setCompact(enabled: Boolean) = worker.execute { database.setSetting(KEY_COMPACT, if (enabled) "1" else "0") }
    override fun close() = worker.shutdown()
    companion object { private const val KEY_SCRIPT = "overlay.selected_script"; private const val KEY_COMPACT = "overlay.compact" }
}
