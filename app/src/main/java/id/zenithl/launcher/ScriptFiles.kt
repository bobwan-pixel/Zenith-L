package id.zenithl.launcher

import android.content.Context
import java.io.File

object ScriptFiles {
    private const val PREFS = "script_settings"
    fun directory(context: Context): File = File(context.filesDir, "scripts").apply { if (!exists()) mkdirs() }
    fun list(context: Context): List<File> = directory(context).listFiles()
        ?.filter { it.isFile && it.extension.equals("lua", ignoreCase = true) }
        ?.sortedBy { it.name.lowercase() } ?: emptyList()
    fun safeFile(context: Context, name: String): File? {
        val clean = name.trim()
        if (!clean.matches(Regex("[A-Za-z0-9 _.-]{1,64}\\.lua", RegexOption.IGNORE_CASE))) return null
        return File(directory(context), clean)
    }

    fun isAutoRun(context: Context, file: File): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(file.name, false)

    fun setAutoRun(context: Context, file: File, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(file.name, enabled).apply()
    }
}
