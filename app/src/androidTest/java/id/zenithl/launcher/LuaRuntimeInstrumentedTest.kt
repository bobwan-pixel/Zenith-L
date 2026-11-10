package id.zenithl.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.zenithl.launcher.lua.LuaExecutionEngine
import id.zenithl.launcher.lua.NativeLuaExecutionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class LuaRuntimeInstrumentedTest {
    @Test fun zenithVersionMatchesInstalledBuild() {
        val engine = NativeLuaExecutionEngine()
        try {
            assertEquals("", execute(engine, "version", "assert(Zenith.getVersion() == \"" + BuildConfig.VERSION_NAME + "\")"))
        } finally { engine.close("version") }
    }

    @Test fun runtimeAndSessionIntrospectionApisReturnRealValues() {
        val engine = NativeLuaExecutionEngine()
        try {
            val source = """
                local runtime = Zenith.getRuntimeInfo()
                local session = Zenith.getSessionInfo()
                assert(runtime.luaVersion == "Lua 5.4.9")
                assert(runtime.memoryLimitBytes == 32 * 1024 * 1024)
                assert(runtime.maxSessions == 3)
                assert(session.id == "introspection")
                assert(session.runtimeStatus == "RUNNING")
                assert(Zenith.getBridgeStatus() == "UNAVAILABLE")
                assert(Zenith.gameBridgeStatus == "UNAVAILABLE")
            """.trimIndent()
            assertEquals("", execute(engine, "introspection", source))
        } finally { engine.close("introspection") }
    }

    @Test fun runtimeExposesOnlyTheDocumentedSafeStandardLibraries() {
        val engine = NativeLuaExecutionEngine()
        try {
            assertEquals("", execute(engine, "sandbox", "assert(load == nil and dofile == nil and loadfile == nil and package == nil and io == nil and os == nil and debug == nil)"))
        } finally { engine.close("sandbox") }
    }

    @Test fun unavailableGameApisFailExplicitlyInsteadOfPretendingToWork() {
        val engine = NativeLuaExecutionEngine()
        try {
            val error = execute(engine, "bridge-api", """assert(Zenith.apiVersion == "0.4"); getTile(1, 1)""")
            assertTrue(error.contains("INTERFACE_ONLY"))
            assertTrue(error.contains("Game Bridge is unavailable"))
        } finally { engine.close("bridge-api") }
    }

    @Test fun nativePrintOutputIsForwardedToListenerWithTabSeparatedArguments() {
        val engine = NativeLuaExecutionEngine()
        val completed = CountDownLatch(1)
        val output = CopyOnWriteArrayList<String>()
        var error: String? = null
        try {
            engine.execute("print-output", "output", """print("hello", 42, true); print("café 😀", "a\0b"); print(string.char(255))""", "Print.lua", object : LuaExecutionEngine.Listener {
                override fun onStarted() = Unit
                override fun onOutput(message: String) { output += message }
                override fun onCompleted() { completed.countDown() }
                override fun onError(trace: String) { error = trace; completed.countDown() }
                override fun onStopped() { error = "STOPPED"; completed.countDown() }
            })
            assertTrue("Lua print script timed out", completed.await(10, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals(listOf("hello\t42\ttrue", "café 😀\ta\u0000b", "\uFFFD"), output)
        } finally { engine.close("print-output") }
    }

    @Test fun outputListenerExceptionDoesNotPoisonNativeExecution() {
        val engine = NativeLuaExecutionEngine()
        val completed = CountDownLatch(1)
        var error: String? = null
        try {
            engine.execute("output-exception", "output", "print('callback failure'); assert(6 * 7 == 42)", "Output.lua", object : LuaExecutionEngine.Listener {
                override fun onStarted() = Unit
                override fun onOutput(message: String) { throw IllegalStateException("listener failure") }
                override fun onCompleted() { completed.countDown() }
                override fun onError(trace: String) { error = trace; completed.countDown() }
                override fun onStopped() { error = "STOPPED"; completed.countDown() }
            })
            assertTrue("Lua execution did not finish after listener exception", completed.await(10, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals("", execute(engine, "output-exception", "assert(1 + 1 == 2)"))
        } finally {
            engine.close("output-exception")
        }
    }

    @Test fun lifecycleListenerExceptionDoesNotInterruptLuaExecution() {
        val engine = NativeLuaExecutionEngine()
        val completed = CountDownLatch(1)
        var error: String? = null
        try {
            engine.execute("listener-lifecycle", "callback", "assert(20 + 22 == 42)", "Callback.lua", object : LuaExecutionEngine.Listener {
                override fun onStarted() { throw IllegalStateException("start callback failure") }
                override fun onCompleted() { completed.countDown() }
                override fun onError(trace: String) { error = trace; completed.countDown() }
                override fun onStopped() { error = "STOPPED"; completed.countDown() }
            })
            assertTrue("Lua execution was interrupted by a lifecycle listener exception", completed.await(10, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals("", execute(engine, "listener-lifecycle", "assert(2 * 3 == 6)"))
        } finally {
            engine.close("listener-lifecycle")
        }
    }

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

    @Test fun closingSessionStopsRuntimeAndDiscardsQueuedScripts() {
        val engine = NativeLuaExecutionEngine()
        val started = CountDownLatch(1)
        val queuedStarted = AtomicBoolean(false)
        engine.execute("close", "long", "while true do end", "Long.lua", listener(started, CountDownLatch(1), null))
        assertTrue("Long-running script did not start", started.await(5, TimeUnit.SECONDS))

        engine.execute("close", "queued", "queuedStarted = true", "Queued.lua", object : LuaExecutionEngine.Listener {
            override fun onStarted() { queuedStarted.set(true) }
            override fun onCompleted() = Unit
            override fun onError(trace: String) = Unit
            override fun onStopped() = Unit
        })
        engine.close("close")
        assertFalse("A queued script ran after its session was closed", queuedStarted.get())
    }

    @Test fun concurrentPauseAndResumeCannotOverrideSessionClose() {
        val engine = NativeLuaExecutionEngine()
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val keepControlling = AtomicBoolean(true)
        val controller = Thread {
            while (keepControlling.get()) {
                engine.pause("control-close-race")
                engine.resume("control-close-race")
            }
        }
        engine.execute("control-close-race", "loop", "while true do end", "ControlCloseRace.lua", object : LuaExecutionEngine.Listener {
            override fun onStarted() { started.countDown() }
            override fun onCompleted() = Unit
            override fun onError(trace: String) { stopped.countDown() }
            override fun onStopped() { stopped.countDown() }
        })
        try {
            assertTrue("Lua script did not start", started.await(5, TimeUnit.SECONDS))
            controller.start()
            Thread.sleep(50)
            engine.close("control-close-race")
            keepControlling.set(false)
            controller.join(2_000)
            assertFalse("Control stress thread did not stop", controller.isAlive)
            assertTrue("A late pause/resume prevented native close from stopping Lua", stopped.await(5, TimeUnit.SECONDS))
        } finally {
            keepControlling.set(false)
            engine.close("control-close-race")
            controller.join(2_000)
        }
    }

    @Test fun closedSessionIdCanBeReusedWithoutStaleNativeRuntimeCollision() {
        val engine = NativeLuaExecutionEngine()
        val oldStarted = CountDownLatch(1)
        engine.execute("reused-session", "old", """print("old-runtime-entered"); while true do end""", "Old.lua", object : LuaExecutionEngine.Listener {
            override fun onStarted() = Unit
            override fun onOutput(message: String) {
                if (message == "old-runtime-entered") oldStarted.countDown()
            }
            override fun onCompleted() = Unit
            override fun onError(trace: String) = Unit
            override fun onStopped() = Unit
        })
        assertTrue("Old Lua runtime did not start", oldStarted.await(5, TimeUnit.SECONDS))

        engine.close("reused-session")

        val completed = CountDownLatch(1)
        var error: String? = null
        engine.execute("reused-session", "new", """assert(Zenith.getSessionInfo().id == "reused-session")""", "New.lua", object : LuaExecutionEngine.Listener {
            override fun onStarted() = Unit
            override fun onCompleted() { completed.countDown() }
            override fun onError(trace: String) { error = trace; completed.countDown() }
            override fun onStopped() { error = "STOPPED"; completed.countDown() }
        })
        try {
            assertTrue("New runtime collided with the closed runtime", completed.await(10, TimeUnit.SECONDS))
            assertNull(error)
        } finally {
            engine.close("reused-session")
        }
    }

    @Test fun pauseAndResumeAreHandledByNativeInstructionHook() {
        val engine = NativeLuaExecutionEngine()
        try {
            val started = CountDownLatch(1)
            val completed = CountDownLatch(1)
            engine.execute("pause", "finite", "local total=0; for i=1,5000000 do total=total+i end", "Pause.lua", listener(started, completed, null))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            engine.pause("pause")
            assertFalse("Lua continued while paused", completed.await(250, TimeUnit.MILLISECONDS))
            engine.resume("pause")
            assertTrue("Lua script did not resume and finish", completed.await(30, TimeUnit.SECONDS))
        } finally { engine.close("pause") }
    }

    @Test fun memoryLimitFailureReturnsAnErrorWithoutDestroyingTheRuntime() {
        val engine = NativeLuaExecutionEngine()
        try {
            val error = execute(
                engine,
                "memory-limit",
                """local chunks = {}; while true do chunks[#chunks + 1] = string.rep("x", 4096) end"""
            )
            assertTrue("Memory exhaustion should be reported as a Lua error", error.isNotBlank())
            assertEquals(
                "",
                execute(
                    engine,
                    "memory-limit",
                    "collectgarbage('collect'); local value = string.rep('y', 3 * 1024 * 1024); assert(#value == 3 * 1024 * 1024); value = nil; collectgarbage('collect')"
                )
            )
        } finally {
            engine.close("memory-limit")
        }
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
