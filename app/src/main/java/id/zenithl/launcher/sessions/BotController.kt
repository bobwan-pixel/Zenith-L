package id.zenithl.launcher.sessions

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class BotController(private val managerProvider: () -> BotManager) : AutoCloseable {
    constructor(manager: BotManager) : this({ manager })
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zenith-bot-controller") }
    private val main = Handler(Looper.getMainLooper())
    fun list(callback: (Result<List<BotSession>>) -> Unit) = submit(callback) { managerProvider().getBots() }
    fun create(name: String, accountLabel: String?, callback: (Result<BotSession>) -> Unit) = submit(callback) { managerProvider().create(BotProfile(name = name, accountLabel = accountLabel)) }
    fun attach(id: String, scriptId: String?, callback: (Result<Unit>) -> Unit) = submit(callback) { managerProvider().attachScript(id, scriptId) }
    fun start(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { managerProvider().startBot(id) }
    fun pause(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { managerProvider().pauseBot(id) }
    fun stop(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { managerProvider().stopBot(id) }
    fun restart(id: String, callback: (Result<Boolean>) -> Unit) = submit(callback) { managerProvider().restartBot(id) }
    fun delete(id: String, callback: (Result<BotSession?>) -> Unit) = submit(callback) { managerProvider().removeBot(id) }
    fun stopAll(callback: (Result<Int>) -> Unit) = submit(callback) { managerProvider().stopAll() }
    private fun <T> submit(callback: (Result<T>) -> Unit, action: () -> T) { worker.execute { val result = runCatching(action); main.post { callback(result) } } }
    override fun close() { worker.shutdown() }
}
