package id.zenithl.launcher.overlay

import id.zenithl.launcher.storage.AppDatabase
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

data class OverlayPreferences(val selectedScriptId: String?, val compact: Boolean, val x: Int, val y: Int)
class OverlayController(private val database: AppDatabase) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zenith-overlay-settings") }
    private val main = Handler(Looper.getMainLooper())
    fun load(callback: (OverlayPreferences) -> Unit) = worker.execute {
        val preferences = OverlayPreferences(database.getSetting(KEY_SCRIPT), database.getSetting(KEY_COMPACT) == "1",
            database.getSetting(KEY_X)?.toIntOrNull() ?: 16, database.getSetting(KEY_Y)?.toIntOrNull() ?: 220)
        main.post { callback(preferences) }
    }
    fun selectScript(id: String?) = worker.execute { if (id == null) database.setSetting(KEY_SCRIPT, "") else database.setSetting(KEY_SCRIPT, id) }
    fun setCompact(enabled: Boolean) = worker.execute { database.setSetting(KEY_COMPACT, if (enabled) "1" else "0") }
    fun setPosition(x: Int, y: Int) = worker.execute { database.setSetting(KEY_X, x.toString()); database.setSetting(KEY_Y, y.toString()) }
    override fun close() = worker.shutdown()
    companion object { private const val KEY_SCRIPT = "overlay.selected_script"; private const val KEY_COMPACT = "overlay.compact"; private const val KEY_X = "overlay.x"; private const val KEY_Y = "overlay.y" }
}
