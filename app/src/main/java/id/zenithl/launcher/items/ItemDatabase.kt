package id.zenithl.launcher.items

import java.io.File
import java.security.MessageDigest

data class ItemInfo(val id: Int, val name: String, val type: Int, val rarity: Int, val collisionType: Int, val growTime: Int)
data class ItemDatabaseVersion(val value: String, val updatedAt: Long, val sha256: String)

class ItemDatabase(private val file: File) {
    @Volatile private var records: Map<Int, ItemInfo> = emptyMap()
    @Volatile var version: ItemDatabaseVersion? = null
        private set

    @Synchronized fun load(): Result<Int> = runCatching {
        require(file.isFile) { "Item database file is not installed" }
        val lines = file.readLines()
        require(lines.firstOrNull()?.startsWith("# ZenithItemDb\t") == true) { "Missing ZenithItemDb header" }
        val versionName = lines.first().substringAfter('\t').trim()
        require(versionName.isNotBlank()) { "Item database version is empty" }
        val loaded = ItemDatabaseLoader.parse(lines.drop(1))
        records = loaded.associateBy { it.id }
        version = ItemDatabaseVersion(versionName, file.lastModified(), file.sha256())
        loaded.size
    }

    fun findItemID(name: String): Int? = records.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    fun getItemInfoByID(id: Int): ItemInfo? = records[id]
    fun all(): List<ItemInfo> = records.values.sortedBy { it.id }
}

object ItemDatabaseLoader {
    fun parse(lines: List<String>): List<ItemInfo> {
        val items = lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { index, line ->
            val cells = line.split('\t')
            require(cells.size == 6) { "Invalid item row ${index + 2}: expected 6 tab-separated columns" }
            ItemInfo(cells[0].toInt(), cells[1].trim(), cells[2].toInt(), cells[3].toInt(), cells[4].toInt(), cells[5].toInt()).also {
                require(it.id >= 0 && it.name.isNotBlank()) { "Invalid item row ${index + 2}: id/name" }
            }
        }
        require(items.isNotEmpty()) { "Item database contains no items" }
        require(items.map { it.id }.toSet().size == items.size) { "Duplicate item ID" }
        require(items.map { it.name.lowercase() }.toSet().size == items.size) { "Duplicate item name" }
        return items
    }
}

class ItemDatabaseUpdater(private val activeFile: File) {
    fun update(version: String, content: String): Result<ItemDatabaseVersion> = runCatching {
        require(version.matches(Regex("[A-Za-z0-9._-]{1,32}"))) { "Invalid item database version" }
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_DATABASE_BYTES) { "Item database exceeds the 10 MB limit" }
        val rows = content.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        ItemDatabaseLoader.parse(rows)
        val payload = "# ZenithItemDb\t$version\n${rows.joinToString("\n")}\n"
        val parent = activeFile.parentFile ?: error("Database destination has no parent")
        parent.mkdirs()
        val temp = File(parent, "${activeFile.name}.new")
        val backup = File(parent, "${activeFile.name}.bak")
        temp.writeText(payload)
        if (activeFile.exists()) {
            backup.delete()
            check(activeFile.copyTo(backup, overwrite = true).exists()) { "Could not create database backup" }
        }
        val previous = File(parent, "${activeFile.name}.previous")
        previous.delete()
        if (activeFile.exists() && !activeFile.renameTo(previous)) error("Could not stage current database for replacement")
        if (!temp.renameTo(activeFile)) {
            if (previous.exists()) previous.renameTo(activeFile)
            if (backup.exists()) backup.copyTo(activeFile, overwrite = true)
            error("Could not atomically replace item database; previous database restored")
        }
        previous.delete()
        ItemDatabaseVersion(version, activeFile.lastModified(), activeFile.sha256())
    }

    fun rollback(): Result<ItemDatabaseVersion> = runCatching {
        val backup = File(activeFile.parentFile, "${activeFile.name}.bak")
        require(backup.isFile) { "No item database backup is available" }
        val lines = backup.readLines()
        require(lines.firstOrNull()?.startsWith("# ZenithItemDb\t") == true) { "Backup header is invalid" }
        ItemDatabaseLoader.parse(lines.drop(1))
        val temp = File(activeFile.parentFile, "${activeFile.name}.rollback")
        backup.copyTo(temp, overwrite = true)
        val previous = File(activeFile.parentFile, "${activeFile.name}.rollback-current")
        previous.delete()
        if (activeFile.exists()) check(activeFile.renameTo(previous)) { "Could not stage current database for rollback" }
        if (!temp.renameTo(activeFile)) {
            if (previous.exists()) previous.renameTo(activeFile)
            error("Could not restore item database backup")
        }
        previous.delete()
        val version = activeFile.readLines().first().substringAfter('\t').trim()
        ItemDatabaseVersion(version, activeFile.lastModified(), activeFile.sha256())
    }

    companion object { const val MAX_DATABASE_BYTES = 10 * 1024 * 1024 }
}

private fun File.sha256(): String = MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }
