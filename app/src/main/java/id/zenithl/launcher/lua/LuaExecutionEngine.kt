package id.zenithl.launcher.lua

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

interface LuaExecutionEngine {
    fun execute(sessionId: String, scriptId: String, source: String, chunkName: String, listener: Listener): Future<*>
    fun pause(sessionId: String)
    fun resume(sessionId: String)
    fun stop(sessionId: String)
    fun close(sessionId: String)

    interface Listener {
        fun onStarted()
        fun onCompleted()
        fun onError(trace: String)
        fun onStopped()
    }
}

/** Lua 5.4.9 native engine. Scripts run away from Android's UI thread, one isolated state per session. */
class NativeLuaExecutionEngine : LuaExecutionEngine {
    private data class SessionRuntime(val executor: ExecutorService, val created: AtomicBoolean = AtomicBoolean())
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionRuntime>()
    private val sessionLock = Any()

    override fun execute(sessionId: String, scriptId: String, source: String, chunkName: String, listener: LuaExecutionEngine.Listener): Future<*> {
        val session = synchronized(sessionLock) {
            sessions[sessionId] ?: run {
                require(sessions.size < MAX_SESSIONS) { "Lua runtime supports at most $MAX_SESSIONS concurrent sessions" }
                SessionRuntime(Executors.newSingleThreadExecutor { r -> Thread(r, "lua-$sessionId") }).also { sessions[sessionId] = it }
            }
        }
        return session.executor.submit {
            try {
                if (!session.created.get()) {
                    check(NativeLua.nativeCreate(sessionId)) { "Could not initialize Lua 5.4.9 state for session $sessionId" }
                    session.created.set(true)
                }
                NativeLua.nativeControl(sessionId, 0)
                listener.onStarted()
                val error = NativeLua.nativeExecute(sessionId, source, "@$chunkName")
                when {
                    error.isEmpty() -> listener.onCompleted()
                    error.contains("Execution stopped by user") -> listener.onStopped()
                    else -> listener.onError(error)
                }
            } catch (error: Throwable) {
                listener.onError("${error::class.java.simpleName}: ${error.message ?: "Lua runtime failure"}")
            }
        }
    }

    override fun pause(sessionId: String) { if (sessions[sessionId]?.created?.get() == true) NativeLua.nativeControl(sessionId, 1) }
    override fun resume(sessionId: String) { if (sessions[sessionId]?.created?.get() == true) NativeLua.nativeControl(sessionId, 0) }
    override fun stop(sessionId: String) { if (sessions[sessionId]?.created?.get() == true) NativeLua.nativeControl(sessionId, 2) }
    override fun close(sessionId: String) {
        val runtime = sessions.remove(sessionId) ?: return
        if (runtime.created.get()) NativeLua.nativeClose(sessionId)
        runtime.executor.shutdown()
    }

    companion object { const val MAX_SESSIONS = 3 }
}
