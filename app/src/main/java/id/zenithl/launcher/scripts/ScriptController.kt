package id.zenithl.launcher.scripts

import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/** UI-facing use-case layer. Disk, database and native work stay off the Activity thread. */
class ScriptController(private val manager: ScriptManager) : AutoCloseable {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "zenith-script-controller") }
    private val main = Handler(Looper.getMainLooper())
    fun list(callback: (Result<List<ScriptRecord>>) -> Unit) = submit(callback) { manager.sort(favoritesFirst = true) }
    fun open(id: String, callback: (Result<Pair<ScriptRecord, String>>) -> Unit) = submit(callback) { manager.openDocument(id) }
    fun save(id: String?, name: String, source: String, callback: (Result<ScriptRecord>) -> Unit) = submit(callback) { manager.saveSource(id, name, source) }
    fun setEnabled(id: String, enabled: Boolean, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.setEnabled(id, enabled) }
    fun setAutoRun(id: String, enabled: Boolean, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.setAutoRun(id, enabled) }
    fun setFavorite(id: String, favorite: Boolean, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.setFavorite(id, favorite) }
    fun duplicate(id: String, name: String, callback: (Result<ScriptRecord>) -> Unit) = submit(callback) { manager.duplicate(id, name) }
    fun rename(id: String, name: String, callback: (Result<ScriptRecord>) -> Unit) = submit(callback) { manager.rename(id, name) }
    fun delete(id: String, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.delete(id) }
    fun import(file: File, callback: (Result<ScriptRecord>) -> Unit) = submit(callback) { manager.importScript(file) }
    fun export(id: String, file: File, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.exportScript(id, file) }
    fun run(id: String, sessionId: String, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.run(id, sessionId) }
    fun pause(sessionId: String, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.pause(sessionId) }
    fun resume(sessionId: String, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.resume(sessionId) }
    fun stop(sessionId: String, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.stop(sessionId) }
    fun stopAll(callback: (Result<Unit>) -> Unit) = submit(callback) { manager.stopAll() }
    private fun <T> submit(callback: (Result<T>) -> Unit, action: () -> T) { io.execute { val result = runCatching(action); main.post { callback(result) } } }
    override fun close() { io.shutdown() }
}
