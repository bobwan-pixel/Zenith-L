package id.zenithl.launcher

import id.zenithl.launcher.api.ApiRegistry
import id.zenithl.launcher.api.ApiStatus
import id.zenithl.launcher.hooks.EventBus
import id.zenithl.launcher.hooks.HookRegistry
import id.zenithl.launcher.inventory.InventoryItem
import id.zenithl.launcher.inventory.InventoryState
import id.zenithl.launcher.items.ItemDatabaseLoader
import id.zenithl.launcher.items.ItemDatabase
import id.zenithl.launcher.items.ItemDatabaseUpdater
import id.zenithl.launcher.items.ItemInfo
import id.zenithl.launcher.diagnostics.*
import id.zenithl.launcher.networking.BridgeStatus
import id.zenithl.launcher.networking.UnavailableGameBridge
import id.zenithl.launcher.logging.MemoryLogger
import id.zenithl.launcher.lua.LuaExecutionEngine
import id.zenithl.launcher.player.InterfaceOnlyPlayerStateProvider
import id.zenithl.launcher.scripts.ScriptManager
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.scripts.ScriptRepository
import id.zenithl.launcher.scripts.ScriptState
import id.zenithl.launcher.sessions.BotManager
import id.zenithl.launcher.sessions.BotProfile
import id.zenithl.launcher.accounts.AccountManager
import id.zenithl.launcher.accounts.AccountProfile
import id.zenithl.launcher.accounts.AccountProvider
import id.zenithl.launcher.accounts.AccountRepository
import id.zenithl.launcher.safety.SafetyEngine
import id.zenithl.launcher.safety.SafetySeverity
import id.zenithl.launcher.safety.SafetyMonitor
import id.zenithl.launcher.safety.WatchdogLimits
import id.zenithl.launcher.safety.WatchdogState
import id.zenithl.launcher.modules.ModModuleRegistry
import id.zenithl.launcher.world.StateResult
import id.zenithl.launcher.world.WorldSnapshot
import id.zenithl.launcher.world.WorldState
import id.zenithl.launcher.world.WorldStateProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Future

class CoreIsolationTest {
    @Test fun eachBotOwnsWorldAndInventoryStateProviders() {
        val bots = BotManager()
        val first = bots.create(BotProfile(name = "A"))
        val second = bots.create(BotProfile(name = "B"))
        assertNotEquals(first.id, second.id)
        val provider = object : WorldStateProvider {
            override fun snapshot(sessionId: String) = StateResult.Available(WorldSnapshot(sessionId, 1, 1, emptyList(), true))
        }
        val a = WorldState(first.id, provider)
        val b = WorldState(second.id, provider)
        assertEquals(first.id, (a.getTiles() as StateResult.Available).value.let { provider.snapshot(first.id) }.let { (it as StateResult.Available).value.name })
        assertEquals(second.id, (provider.snapshot(second.id) as StateResult.Available).value.name)
        assertNotSame(first.inventoryState, second.inventoryState)
        assertTrue(first.playerState.getLocal() is StateResult.Unavailable)
    }

    @Test fun sessionEventsNeverCrossSessionBoundaryAndHooksCleanUp() {
        val busA = EventBus("a")
        val busB = EventBus("b")
        var observedA = 0
        var observedB = 0
        val hooksA = HookRegistry(busA)
        val hooksB = HookRegistry(busB)
        hooksA.addHook("script-a", "OnPacket") { observedA++ }
        hooksB.addHook("script-b", "OnPacket") { observedB++ }
        busA.post(id.zenithl.launcher.hooks.SessionEvent("a", "OnPacket", "packet"))
        assertEquals(1, observedA)
        assertEquals(0, observedB)
        hooksA.removeScriptHooks("script-a")
        assertEquals(0, busA.subscriptionCount())
        try {
            busB.post(id.zenithl.launcher.hooks.SessionEvent("a", "OnPacket", "wrong session"))
            fail("cross-session event must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun scriptManagerRefusesDisabledScriptAndPersistsMetadataThroughRepository() {
        val temp = createTempDir(prefix = "zenith-script-test")
        val repository = InMemoryScripts()
        val engine = NoopEngine()
        val manager = ScriptManager(repository, temp, engine, MemoryLogger())
        val script = manager.create("Sample", "print('ok')")
        assertFalse(script.enabled)
        try { manager.run(script.id, "session-a"); fail("disabled script must not run") } catch (_: IllegalStateException) { }
        assertEquals(0, engine.executions)
        manager.setEnabled(script.id, true)
        manager.setAutoRun(script.id, true)
        manager.setFavorite(script.id, true)
        val saved = requireNotNull(repository.get(script.id))
        assertTrue(saved.enabled && saved.autoRun && saved.favorite)
        assertEquals(ScriptState.ENABLED, saved.state)
        temp.deleteRecursively()
    }

    @Test fun itemDatabaseLoaderValidatesAndLooksUpRows() {
        val items = ItemDatabaseLoader.parse(listOf("1\tDirt\t0\t1\t1\t30", "2\tRock\t0\t2\t1\t0"))
        assertEquals(listOf(1, 2), items.map(ItemInfo::id))
        assertEquals("Rock", items[1].name)
        try { ItemDatabaseLoader.parse(listOf("1\tDirt\t0\t1\t1\t30", "1\tRock\t0\t2\t1\t0")); fail("duplicate IDs must be rejected") } catch (_: IllegalArgumentException) { }
        try { ItemDatabaseLoader.parse(emptyList()); fail("empty data must be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun itemDatabaseUpdateKeepsBackupAndSupportsRollback() {
        val directory = createTempDir(prefix = "zenith-items-test")
        val file = File(directory, "items.tsv")
        val updater = ItemDatabaseUpdater(file)
        assertTrue(updater.update("v1", "1\tDirt\t0\t1\t1\t30").isSuccess)
        assertTrue(updater.update("v2", "2\tRock\t0\t2\t1\t0").isSuccess)
        assertEquals(2, ItemDatabase(file).apply { assertTrue(load().isSuccess) }.findItemID("rock"))
        assertTrue(updater.rollback().isSuccess)
        val rolledBack = ItemDatabase(file)
        assertTrue(rolledBack.load().isSuccess)
        assertEquals("v1", rolledBack.version?.value)
        assertEquals(1, rolledBack.findItemID("dirt"))
        directory.deleteRecursively()
    }

    @Test fun apiRegistryDistinguishesWorkingFromBridgeInterfaces() {
        assertEquals(ApiStatus.IMPLEMENTED, ApiRegistry.find("Zenith.getVersion")?.status)
        assertEquals(ApiStatus.INTERFACE_ONLY, ApiRegistry.find("getTile")?.status)
        assertEquals(ApiStatus.NOT_IMPLEMENTED, ApiRegistry.find("Sleep")?.status)
        assertTrue(ApiRegistry.search("getT").any { it.name == "getTile" })
        assertEquals(ApiStatus.INTERFACE_ONLY, ApiRegistry.find("Zenith.getBridgeStatus")?.status)
    }

    @Test fun packageDetectionDoesNotClaimForegroundOrCompatibility() {
        val installed = InstalledGame("com.rtsoft.growtopia", "5.13", 513, null)
        val detector = GameDetector(InstalledGameProvider { installed })
        val snapshot = detector.detect()
        assertEquals(GameLifecycleState.INSTALLED, snapshot.state)
        assertEquals("5.13", snapshot.compatibility.gameVersion)
        assertFalse(snapshot.compatibility.supported)
        assertEquals(GameLifecycleState.LAUNCH_REQUESTED, detector.run { markLaunchRequested(); detect().state })
    }

    @Test fun defaultGameBridgeReportsUnavailableInsteadOfConnecting() {
        val bridge = UnavailableGameBridge()
        assertEquals(BridgeStatus.UNAVAILABLE, bridge.status)
        assertTrue(bridge.attach("s1").isFailure)
    }

    @Test fun safetyEmergencyStopIsSessionScopedAndRecordsIncident() {
        val stopped = mutableListOf<String>()
        val safety = SafetyEngine(stopSession = stopped::add)
        safety.record("a", "script-1", SafetySeverity.MEDIUM, "LUA_ERROR", "runtime failed")
        safety.emergencyStop("a")
        assertTrue(safety.isStopped("a"))
        assertFalse(safety.isStopped("b"))
        assertEquals(listOf("a"), stopped)
        assertEquals("EMERGENCY_STOP", safety.incidents("a").first().type)
        assertEquals(0, safety.incidents(sessionId = "b").size)
    }

    @Test fun watchdogEscalatesAndStopsOnlyTheTimedOutSession() {
        val engine = SafetyEngine()
        val stopped = java.util.concurrent.CountDownLatch(1)
        val actions = mutableListOf<String>()
        val monitor = SafetyMonitor(
            engine,
            WatchdogLimits(warningAfterMs = 5, throttleAfterMs = 15, pauseAfterMs = 30, stopAfterMs = 50, pollIntervalMs = 2),
            pause = { actions += "pause:$it" },
            resume = { actions += "resume:$it" },
            stop = { actions += "stop:$it"; stopped.countDown() },
        )
        try {
            monitor.start("session-a", "script-a")
            assertTrue(stopped.await(1, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(WatchdogState.TIMEOUT, monitor.state("session-a"))
            assertTrue(engine.incidents("session-a").any { it.type == "SCRIPT_RUNTIME_WARNING" })
            assertTrue(engine.incidents("session-a").any { it.type == "SCRIPT_THROTTLED" })
            assertTrue(engine.incidents("session-a").any { it.type == "SCRIPT_PAUSED_BY_WATCHDOG" })
            assertTrue(engine.incidents("session-a").any { it.type == "SCRIPT_TIMEOUT" })
            assertFalse(actions.any { it.endsWith("session-b") })
        } finally { monitor.close() }
    }

    @Test fun accountProfilesAreSeparateFromSessionProfilesAndNeverContainSecrets() {
        val repository = object : AccountRepository {
            private val rows = linkedMapOf<String, AccountProfile>()
            override fun all() = rows.values.toList()
            override fun get(id: String) = rows[id]
            override fun save(account: AccountProfile) { rows[account.id] = account }
            override fun delete(id: String) { rows.remove(id) }
        }
        val manager = AccountManager(repository)
        val profile = manager.addProfile("Test account", AccountProvider.LOCAL_DEVELOPMENT)
        assertEquals("Test account", manager.accounts().single().displayName)
        assertNull(profile.subject)
    }

    @Test fun moduleRegistryReportsBridgeDependentFeaturesAsInterfaces() {
        assertTrue(ModModuleRegistry.categories.size >= 20)
        assertEquals(id.zenithl.launcher.modules.CapabilityStatus.INTERFACE_ONLY, ModModuleRegistry.module("world-scan")?.status)
        assertEquals(id.zenithl.launcher.modules.CapabilityStatus.IMPLEMENTED, ModModuleRegistry.module("library")?.status)
        assertEquals(0, ModModuleRegistry.search("no module like this").size)
    }

    private class InMemoryScripts : ScriptRepository {
        private val data = linkedMapOf<String, ScriptRecord>()
        override fun all() = data.values.toList()
        override fun get(id: String) = data[id]
        override fun save(record: ScriptRecord) { data[record.id] = record }
        override fun delete(id: String) { data.remove(id) }
    }

    private class NoopEngine : LuaExecutionEngine {
        var executions = 0
        override fun execute(sessionId: String, scriptId: String, source: String, chunkName: String, listener: LuaExecutionEngine.Listener): Future<*> {
            executions++
            return java.util.concurrent.CompletableFuture.completedFuture(null)
        }
        override fun pause(sessionId: String) = Unit
        override fun resume(sessionId: String) = Unit
        override fun stop(sessionId: String) = Unit
        override fun close(sessionId: String) = Unit
    }
}
