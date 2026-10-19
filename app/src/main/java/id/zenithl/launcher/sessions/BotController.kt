package id.zenithl.launcher.sessions

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class BotController(private val manager: BotManager) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zenith-bot-controller") }
    private val main = Handler(Looper.getMainLooper())
    fun list(callback: (Result<List<BotSession>>) -> Unit) = submit(callback) { manager.getBots() }
    fun create(name: String, accountLabel: String?, callback: (Result<BotSession>) -> Unit) = submit(callback) { manager.create(BotProfile(name = name, accountLabel = accountLabel)) }
    fun attach(id: String, scriptId: String?, callback: (Result<Unit>) -> Unit) = submit(callback) { manager.attachScript(id, scriptId) }
    fun start(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { manager.startBot(id) }
    fun pause(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { manager.pauseBot(id) }
    fun stop(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { manager.stopBot(id) }
    fun restart(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { manager.restartBot(id) }
    fun delete(id: String, callback: (Result<BotSession?>) -> Unit) = submit(callback) { manager.removeBot(id) }
    private fun <T> submit(callback: (Result<T>) -> Unit, action: () -> T) { worker.execute { val result = runCatching(action); main.post { callback(result) } } }
    override fun close() { worker.shutdown() }
}
