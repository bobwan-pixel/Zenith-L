package id.zenithl.launcher.logging

import java.io.File
import android.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL }
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val component: String,
    val message: String,
    val scriptId: String? = null,
    val sessionId: String? = null,
    val throwable: String? = null,
)

interface Logger {
    fun log(entry: LogEntry)
    fun query(level: LogLevel? = null, scriptId: String? = null, sessionId: String? = null, limit: Int = 500): List<LogEntry>
}

class FileLogger(private val file: File, private val maxBytes: Long = 1_048_576L) : Logger {
    private val lock = Any()
    private val entries = CopyOnWriteArrayList<LogEntry>()
    init {
        listOf(File(file.parentFile, "${file.name}.1"), file).filter { it.isFile }.forEach { logFile ->
            runCatching { logFile.forEachLine { line -> decode(line)?.let(entries::add) } }
        }
        while (entries.size > 5000) entries.removeAt(0)
    }

    override fun log(entry: LogEntry) = synchronized(lock) {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > maxBytes) {
            val backup = File(file.parentFile, "${file.name}.1")
            backup.delete()
            file.renameTo(backup)
        }
        file.appendText(listOf(entry.timestamp.toString(), entry.level.name, entry.component, entry.sessionId.orEmpty(), entry.scriptId.orEmpty(), entry.message, entry.throwable.orEmpty()).joinToString("|") { encode(it) } + "\n")
        entries.add(entry)
        if (entries.size > 5000) entries.removeAt(0)
    }

    override fun query(level: LogLevel?, scriptId: String?, sessionId: String?, limit: Int): List<LogEntry> = entries.asSequence()
        .filter { level == null || it.level == level }
        .filter { scriptId == null || it.scriptId == scriptId }
        .filter { sessionId == null || it.sessionId == sessionId }
        .takeLast(limit.coerceIn(1, 5000)).toList()

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    private fun decode(line: String): LogEntry? = runCatching {
        val cells = line.split('|')
        require(cells.size == 7)
        fun value(index: Int) = String(Base64.decode(cells[index], Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        LogEntry(value(0).toLong(), LogLevel.valueOf(value(1)), value(2), value(5), value(4).ifEmpty { null }, value(3).ifEmpty { null }, value(6).ifEmpty { null })
    }.getOrNull()
}

class MemoryLogger : Logger {
    private val entries = CopyOnWriteArrayList<LogEntry>()
    override fun log(entry: LogEntry) { entries.add(entry) }
    override fun query(level: LogLevel?, scriptId: String?, sessionId: String?, limit: Int): List<LogEntry> = entries.asSequence()
        .filter { level == null || it.level == level }
        .filter { scriptId == null || it.scriptId == scriptId }
        .filter { sessionId == null || it.sessionId == sessionId }
        .takeLast(limit.coerceIn(1, 5000)).toList()
}
