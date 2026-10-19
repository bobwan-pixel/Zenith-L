package id.zenithl.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.zenithl.launcher.lua.LuaExecutionEngine
import id.zenithl.launcher.lua.NativeLuaExecutionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LuaRuntimeInstrumentedTest {
    @Test fun nativeLua54ExecutesAndIsolatesSeparateSessions() {
        val engine = NativeLuaExecutionEngine()
        try {
            assertEquals("", execute(engine, "session-one", "shared_value = 17"))
            assertEquals("", execute(engine, "session-two", "assert(shared_value == nil)"))
            assertEquals("", execute(engine, "session-one", "assert(shared_value == 17)"))
        } finally {
            engine.close("session-one")
            engine.close("session-two")
        }
    }

    @Test fun runtimeReturnsSyntaxTraceWithFileAndLine() {
        val engine = NativeLuaExecutionEngine()
        try {
            val error = execute(engine, "syntax", "local =", "BadScript.lua")
            assertTrue(error.contains("BadScript.lua:1"))
        } finally { engine.close("syntax") }
    }

    @Test fun stopInterruptsLongRunningLuaLoop() {
        val engine = NativeLuaExecutionEngine()
        try {
            val started = CountDownLatch(1)
            val stopped = CountDownLatch(1)
            engine.execute("stop", "loop", "while true do end", "Loop.lua", listener(started, stopped, null))
            assertTrue("Lua script did not start", started.await(5, TimeUnit.SECONDS))
            engine.stop("stop")
            assertTrue("Lua instruction hook did not process Stop", stopped.await(5, TimeUnit.SECONDS))
        } finally { engine.close("stop") }
    }

    @Test fun pauseAndResumeAreHandledByNativeInstructionHook() {
        val engine = NativeLuaExecutionEngine()
        try {
            val started = CountDownLatch(1)
            val completed = CountDownLatch(1)
            engine.execute("pause", "finite", "local total=0; for i=1,20000000 do total=total+i end", "Pause.lua", listener(started, completed, null))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            engine.pause("pause")
            assertFalse("Lua continued while paused", completed.await(250, TimeUnit.MILLISECONDS))
            engine.resume("pause")
            assertTrue("Lua script did not resume and finish", completed.await(10, TimeUnit.SECONDS))
        } finally { engine.close("pause") }
    }

    private fun execute(engine: NativeLuaExecutionEngine, session: String, source: String, name: String = "Test.lua"): String {
        val completed = CountDownLatch(1)
        var result: String? = null
        engine.execute(session, "test-script", source, name, object : LuaExecutionEngine.Listener {
            override fun onStarted() = Unit
            override fun onCompleted() { result = ""; completed.countDown() }
            override fun onError(trace: String) { result = trace; completed.countDown() }
            override fun onStopped() { result = "STOPPED"; completed.countDown() }
        })
        assertTrue("Lua script timed out", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun listener(started: CountDownLatch, done: CountDownLatch, errorOutput: MutableList<String>?) = object : LuaExecutionEngine.Listener {
        override fun onStarted() { started.countDown() }
        override fun onCompleted() { done.countDown() }
        override fun onError(trace: String) { errorOutput?.add(trace); done.countDown() }
        override fun onStopped() { done.countDown() }
    }
}
