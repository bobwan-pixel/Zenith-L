package id.zenithl.launcher

import android.app.Application
import android.content.Context
import id.zenithl.launcher.diagnostics.GameDetector
import id.zenithl.launcher.diagnostics.GamePackageManager
import id.zenithl.launcher.bots.BotManagerFacade
import id.zenithl.launcher.logging.FileLogger
import id.zenithl.launcher.overlay.OverlayController
import id.zenithl.launcher.overlay.PermissionManager
import id.zenithl.launcher.lua.NativeLuaExecutionEngine
import id.zenithl.launcher.scripts.ScriptController
import id.zenithl.launcher.scripts.ScriptManager
import id.zenithl.launcher.scripts.ScriptScheduler
import id.zenithl.launcher.sessions.BotManager
import id.zenithl.launcher.sessions.BotController
import id.zenithl.launcher.sessions.BotProfile
import id.zenithl.launcher.items.ItemDatabase
import id.zenithl.launcher.networking.GameBridge
import id.zenithl.launcher.networking.UnavailableGameBridge
import id.zenithl.launcher.storage.AppDatabase
import id.zenithl.launcher.storage.SQLiteScriptRepository
import id.zenithl.launcher.storage.SQLiteBotProfileRepository
import id.zenithl.launcher.storage.SQLiteScheduleRuleStore
import id.zenithl.launcher.storage.SQLiteAccountRepository
import id.zenithl.launcher.storage.SQLiteSafetyIncidentRepository
import id.zenithl.launcher.accounts.AccountManager
import id.zenithl.launcher.accounts.AccountController
import id.zenithl.launcher.accounts.GoogleCredentialSignIn
import androidx.credentials.CredentialManager
import id.zenithl.launcher.safety.SafetyEngine
import id.zenithl.launcher.safety.SafetyMonitor
import id.zenithl.launcher.safety.SafetySeverity
import java.io.File
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

class ZenithApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val context = app.applicationContext
    private val io = Executors.newSingleThreadExecutor { task -> Thread(task, "zenith-bootstrap") }
    private val safetyIo = Executors.newSingleThreadExecutor { task -> Thread(task, "zenith-safety-store") }
    private val mainHandler = Handler(Looper.getMainLooper())
    val database = AppDatabase(app)
    val accountManager = AccountManager(SQLiteAccountRepository(database))
    val accountController: AccountController by lazy { AccountController(accountManager) }
    val googleCredentialSignIn: GoogleCredentialSignIn by lazy {
        GoogleCredentialSignIn(CredentialManager.create(context), BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
    val safetyIncidentRepository = SQLiteSafetyIncidentRepository(database)
    val logger: FileLogger by lazy { FileLogger(File(app.filesDir, "logs/zenith.log")) }
    private val repository = SQLiteScriptRepository(database)
    val luaEngine = NativeLuaExecutionEngine()
    val safetyEngine: SafetyEngine by lazy { SafetyEngine(
        stopSession = ::stopScriptSession,
        persistIncident = { incident -> safetyIo.execute { runCatching { safetyIncidentRepository.save(incident) } } },
    ) }
    val safetyMonitor: SafetyMonitor by lazy { SafetyMonitor(
        safetyEngine,
        pause = luaEngine::pause,
        resume = luaEngine::resume,
        stop = luaEngine::stop,
    ) }
    val scriptManager: ScriptManager by lazy { ScriptManager(repository, File(app.filesDir, "scripts"), luaEngine, logger, safetyMonitor) }
    val scriptController: ScriptController by lazy { ScriptController(scriptManager) }
    val overlayController = OverlayController(database)
    val permissionManager = PermissionManager(app)
    val scheduler: ScriptScheduler by lazy { ScriptScheduler(scriptManager, SQLiteScheduleRuleStore(database)) }
    val botManager: BotManager by lazy { BotManager(id.zenithl.launcher.sessions.SessionManager(SQLiteBotProfileRepository(database))).also { manager ->
        if (manager.getBot(MAIN_SESSION_ID) == null) manager.create(BotProfile(id = MAIN_SESSION_ID, name = "Growtopia session"))
    } }
    val botController: BotController by lazy { BotController { botManager } }
    val zenithBotsApi: BotManagerFacade by lazy { BotManagerFacade(botManager) }
    val gameDetector = GameDetector(GamePackageManager(app))
    val gameBridge: GameBridge = UnavailableGameBridge()
    /** One app-wide catalog shared by every account/session. No account login triggers data loading. */
    private val catalogFile = File(app.filesDir, "database/items.tsv")
    val itemDatabase: ItemDatabase by lazy { ItemDatabase(catalogFile) }
    val itemDatabaseUpdater: id.zenithl.launcher.items.ItemDatabaseUpdater by lazy { id.zenithl.launcher.items.ItemDatabaseUpdater(catalogFile) }

    fun emergencyStopAll(callback: (Result<Unit>) -> Unit = {}) {
        scheduler.cancelAll()
        safetyEngine.record(null, null, SafetySeverity.CRITICAL, "GLOBAL_EMERGENCY_STOP", "Global stop requested")
        scriptController.stopAll { scripts ->
            if (scripts.isFailure) callback(scripts)
            else botController.stopAll { bots -> callback(bots.map { Unit }) }
        }
    }

    fun emergencyStopSession(sessionId: String, callback: (Result<Unit>) -> Unit = {}) {
        scheduler.cancelSession(sessionId)
        safetyEngine.emergencyStop(sessionId)
        botController.stop(sessionId) { callback(it.map { Unit }) }
    }

    private fun stopScriptSession(sessionId: String) { scriptController.stop(sessionId) { } }

    @Volatile private var migrationDone = false

    /** One-time legacy file migration is deferred until workspace use and kept off the main thread. */
    fun prepareScripts(callback: (Result<Unit>) -> Unit) {
        io.execute {
            val result = runCatching {
                synchronized(this) {
                    if (!migrationDone) { migratePhase1Scripts(context); migrationDone = true }
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadItemCatalog(callback: (Result<Int>) -> Unit) {
        io.execute { val result = itemDatabase.load(); mainHandler.post { callback(result) } }
    }

    private fun migratePhase1Scripts(context: Context) {
        if (database.getSetting("phase1_scripts_migrated") == "1") return
        ScriptFiles.list(context).forEach { file ->
            if (repository.all().none { it.name.equals(file.nameWithoutExtension, true) }) {
                val record = runCatching { scriptManager.create(file.nameWithoutExtension, file.readText()) }.getOrNull() ?: return@forEach
                val selected = ScriptFiles.isAutoRun(context, file)
                scriptManager.setEnabled(record.id, selected)
                scriptManager.setAutoRun(record.id, selected)
            }
        }
        database.setSetting("phase1_scripts_migrated", "1")
    }

    companion object { const val MAIN_SESSION_ID = "growtopia-primary" }
}
