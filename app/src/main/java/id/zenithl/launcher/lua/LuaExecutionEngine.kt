package id.zenithl.launcher.lua

import id.zenithl.launcher.BuildConfig
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
    private data class SessionRuntime(
        val executor: ExecutorService,
        val created: AtomicBoolean = AtomicBoolean(),
        val closed: AtomicBoolean = AtomicBoolean(),
    )

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionRuntime>()
    private val sessionLock = Any()

    override fun execute(
        sessionId: String,
        scriptId: String,
        source: String,
        chunkName: String,
        listener: LuaExecutionEngine.Listener,
    ): Future<*> = synchronized(sessionLock) {
        val session = sessions[sessionId] ?: run {
            require(sessions.size < MAX_SESSIONS) { "Lua runtime supports at most $MAX_SESSIONS concurrent sessions" }
            SessionRuntime(Executors.newSingleThreadExecutor { r -> Thread(r, "lua-$sessionId") })
                .also { sessions[sessionId] = it }
        }

        session.executor.submit task@{
            try {
                if (session.closed.get()) {
                    listener.onStopped()
                    return@task
                }
                if (!session.created.get()) {
                    check(NativeLua.nativeCreate(sessionId, BuildConfig.VERSION_NAME)) {
                        "Could not initialize Lua 5.4.9 state for session $sessionId"
                    }
                    session.created.set(true)
                }
                // close() may race with nativeCreate(); close the just-created state instead of leaking it.
                if (session.closed.get()) {
                    NativeLua.nativeClose(sessionId)
                    session.created.set(false)
                    listener.onStopped()
                    return@task
                }
                NativeLua.nativeControl(sessionId, 0)
                listener.onStarted()
                val error = NativeLua.nativeExecute(sessionId, source, "@$chunkName")
                when {
                    session.closed.get() -> listener.onStopped()
                    error.isEmpty() -> listener.onCompleted()
                    error.contains("Execution stopped by user") -> listener.onStopped()
                    else -> listener.onError(error)
                }
            } catch (error: Throwable) {
                if (session.closed.get()) listener.onStopped()
                else listener.onError("\${error::class.java.simpleName}: \${error.message ?: "Lua runtime failure"}")
            }
        }
    }

    override fun pause(sessionId: String) {
        sessions[sessionId]?.takeIf { !it.closed.get() && it.created.get() }?.let { NativeLua.nativeControl(sessionId, 1) }
    }

    override fun resume(sessionId: String) {
        sessions[sessionId]?.takeIf { !it.closed.get() && it.created.get() }?.let { NativeLua.nativeControl(sessionId, 0) }
    }

    override fun stop(sessionId: String) {
        sessions[sessionId]?.takeIf { !it.closed.get() && it.created.get() }?.let { NativeLua.nativeControl(sessionId, 2) }
    }

    override fun close(sessionId: String) {
        val runtime = synchronized(sessionLock) {
            sessions.remove(sessionId)?.also { it.closed.set(true) }
        } ?: return

        // Stop native execution, discard queued tasks, and remove the native state.
        NativeLua.nativeControl(sessionId, 2)
        runtime.executor.shutdownNow()
        NativeLua.nativeClose(sessionId)
    }

    companion object { const val MAX_SESSIONS = 3 }
}
