package id.zenithl.launcher

import android.app.Application
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
import java.io.File

class ZenithApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val database = AppDatabase(app)
    val logger = FileLogger(File(app.filesDir, "logs/zenith.log"))
    private val repository = SQLiteScriptRepository(database)
    val luaEngine = NativeLuaExecutionEngine()
    val scriptManager = ScriptManager(repository, File(app.filesDir, "scripts"), luaEngine, logger)
    val scriptController = ScriptController(scriptManager)
    val overlayController = OverlayController(database)
    val permissionManager = PermissionManager(app)
    val scheduler = ScriptScheduler(scriptManager, SQLiteScheduleRuleStore(database))
    val botManager = BotManager(id.zenithl.launcher.sessions.SessionManager(SQLiteBotProfileRepository(database))).also { manager ->
        if (manager.getBot(MAIN_SESSION_ID) == null) manager.create(BotProfile(id = MAIN_SESSION_ID, name = "Growtopia session"))
    }
    val botController = BotController(botManager)
    val zenithBotsApi = BotManagerFacade(botManager)
    val gameDetector = GameDetector(GamePackageManager(app))
    val gameBridge: GameBridge = UnavailableGameBridge()
    /** One app-wide catalog shared by every account/session. No account login triggers data loading. */
    val itemDatabase = ItemDatabase(File(app.filesDir, "database/items.tsv"))
    val itemDatabaseUpdater = id.zenithl.launcher.items.ItemDatabaseUpdater(File(app.filesDir, "database/items.tsv"))

    init { runCatching { itemDatabase.load() } }

    init { migratePhase1Scripts(app) }

    private fun migratePhase1Scripts(context: Application) {
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
