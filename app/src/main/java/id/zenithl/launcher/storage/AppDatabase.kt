package id.zenithl.launcher.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.zenithl.launcher.scripts.ScriptProfile
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.scripts.ScriptRepository
import id.zenithl.launcher.scripts.ScriptState
import id.zenithl.launcher.sessions.BotProfile
import id.zenithl.launcher.sessions.BotProfileRepository
import id.zenithl.launcher.scripts.ScheduleRule
import id.zenithl.launcher.scripts.ScheduleRuleStore
import id.zenithl.launcher.scripts.SchedulerTrigger
import org.json.JSONObject

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "zenith.db", null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scripts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE, path TEXT NOT NULL, enabled INTEGER NOT NULL, auto_run INTEGER NOT NULL, favorite INTEGER NOT NULL, state TEXT NOT NULL, last_run INTEGER, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, profile_json TEXT NOT NULL DEFAULT '{}')")
        db.execSQL("CREATE TABLE session_profiles (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, account_label TEXT, world TEXT, attached_script_id TEXT, state TEXT NOT NULL, auto_restart INTEGER NOT NULL DEFAULT 0, restart_delay_ms INTEGER NOT NULL DEFAULT 5000, max_restart_count INTEGER NOT NULL DEFAULT 3, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE item_database_meta (id INTEGER PRIMARY KEY CHECK(id=1), version TEXT NOT NULL, updated_at INTEGER NOT NULL, checksum TEXT NOT NULL)")
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, type INTEGER NOT NULL, rarity INTEGER NOT NULL, collision_type INTEGER NOT NULL, grow_time INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, component TEXT NOT NULL, severity TEXT NOT NULL, session_id TEXT, script_id TEXT, message TEXT NOT NULL, details TEXT)")
        db.execSQL("PRAGMA user_version=$VERSION")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE scripts ADD COLUMN profile_json TEXT NOT NULL DEFAULT '{}' ")
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS session_profiles (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, account_label TEXT, world TEXT, attached_script_id TEXT, state TEXT NOT NULL, auto_restart INTEGER NOT NULL DEFAULT 0, restart_delay_ms INTEGER NOT NULL DEFAULT 5000, max_restart_count INTEGER NOT NULL DEFAULT 3, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS item_database_meta (id INTEGER PRIMARY KEY CHECK(id=1), version TEXT NOT NULL, updated_at INTEGER NOT NULL, checksum TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, type INTEGER NOT NULL, rarity INTEGER NOT NULL, collision_type INTEGER NOT NULL, grow_time INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, component TEXT NOT NULL, severity TEXT NOT NULL, session_id TEXT, script_id TEXT, message TEXT NOT NULL, details TEXT)")
        }
    }

    fun getSetting(key: String): String? = readableDatabase.rawQuery("SELECT value FROM app_settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun setSetting(key: String, value: String) {
        val values = ContentValues().apply { put("key", key); put("value", value); put("updated_at", System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object { const val VERSION = 3 }
}

class SQLiteScriptRepository(private val database: AppDatabase) : ScriptRepository {
    override fun all(): List<ScriptRecord> = database.readableDatabase.rawQuery("SELECT * FROM scripts ORDER BY name COLLATE NOCASE", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toRecord()) }
    }
    override fun get(id: String): ScriptRecord? = database.readableDatabase.rawQuery("SELECT * FROM scripts WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.toRecord() else null }
    override fun save(record: ScriptRecord) {
        val values = ContentValues().apply {
            put("id", record.id); put("name", record.name); put("path", record.path)
            put("enabled", record.enabled); put("auto_run", record.autoRun); put("favorite", record.favorite)
            put("state", record.state.name); put("last_run", record.lastRun); put("last_error", record.lastError)
            put("created_at", record.createdAt); put("updated_at", record.updatedAt)
            put("profile_json", JSONObject(record.profile.values).toString())
        }
        database.writableDatabase.insertWithOnConflict("scripts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun delete(id: String) { database.writableDatabase.delete("scripts", "id=?", arrayOf(id)) }

    private fun android.database.Cursor.toRecord(): ScriptRecord {
        val profileJson = JSONObject(getString(getColumnIndexOrThrow("profile_json")))
        val profile = buildMap { profileJson.keys().forEach { key -> put(key, profileJson.optString(key)) } }
        return ScriptRecord(
            id = getString(getColumnIndexOrThrow("id")), name = getString(getColumnIndexOrThrow("name")),
            path = getString(getColumnIndexOrThrow("path")), enabled = getInt(getColumnIndexOrThrow("enabled")) != 0,
            autoRun = getInt(getColumnIndexOrThrow("auto_run")) != 0, favorite = getInt(getColumnIndexOrThrow("favorite")) != 0,
            state = ScriptState.valueOf(getString(getColumnIndexOrThrow("state"))),
            lastRun = if (isNull(getColumnIndexOrThrow("last_run"))) null else getLong(getColumnIndexOrThrow("last_run")),
            lastError = if (isNull(getColumnIndexOrThrow("last_error"))) null else getString(getColumnIndexOrThrow("last_error")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")), updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            profile = ScriptProfile(profile),
        )
    }
}

class SQLiteBotProfileRepository(private val database: AppDatabase) : BotProfileRepository {
    override fun all(): List<BotProfile> = database.readableDatabase.rawQuery("SELECT * FROM session_profiles ORDER BY created_at", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(BotProfile(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                world = cursor.getString(cursor.getColumnIndexOrThrow("world")),
                accountLabel = cursor.getString(cursor.getColumnIndexOrThrow("account_label")),
                scriptId = cursor.getString(cursor.getColumnIndexOrThrow("attached_script_id")),
                autoRestart = cursor.getInt(cursor.getColumnIndexOrThrow("auto_restart")) != 0,
                restartDelayMs = cursor.getLong(cursor.getColumnIndexOrThrow("restart_delay_ms")),
                maxRestartCount = cursor.getInt(cursor.getColumnIndexOrThrow("max_restart_count")),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            ))
        }
    }
    override fun save(profile: BotProfile) {
        val values = ContentValues().apply {
            put("id", profile.id); put("name", profile.name); put("world", profile.world)
            put("account_label", profile.accountLabel); put("attached_script_id", profile.scriptId)
            put("state", "CREATED"); put("auto_restart", profile.autoRestart)
            put("restart_delay_ms", profile.restartDelayMs); put("max_restart_count", profile.maxRestartCount)
            put("created_at", profile.createdAt)
        }
        database.writableDatabase.insertWithOnConflict("session_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun delete(id: String) { database.writableDatabase.delete("session_profiles", "id=?", arrayOf(id)) }
}

class SQLiteScheduleRuleStore(private val database: AppDatabase) : ScheduleRuleStore {
    override fun load(): List<ScheduleRule> = runCatching {
        val data = JSONObject(database.getSetting("scheduler.rules") ?: return emptyList())
        val array = data.optJSONArray("rules") ?: return emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                add(ScheduleRule(
                    scriptId = row.getString("scriptId"),
                    trigger = SchedulerTrigger.valueOf(row.getString("trigger")),
                    delayMs = row.optLong("delayMs", 0),
                    afterScriptId = row.optString("afterScriptId").takeIf { it.isNotBlank() },
                    enabled = row.optBoolean("enabled", true),
                ))
            }
        }
    }.getOrDefault(emptyList())

    override fun save(rules: List<ScheduleRule>) {
        val array = org.json.JSONArray()
        rules.forEach { rule -> array.put(JSONObject().apply {
            put("scriptId", rule.scriptId); put("trigger", rule.trigger.name); put("delayMs", rule.delayMs)
            put("afterScriptId", rule.afterScriptId); put("enabled", rule.enabled)
        }) }
        database.setSetting("scheduler.rules", JSONObject().put("rules", array).toString())
    }
}
