package id.zenithl.launcher

import android.content.Context
import java.io.File

object ScriptFiles {
    fun directory(context: Context): File = File(context.filesDir, "scripts").apply { if (!exists()) mkdirs() }
    fun list(context: Context): List<File> = directory(context).listFiles()
        ?.filter { it.isFile && it.extension.equals("lua", ignoreCase = true) }
        ?.sortedBy { it.name.lowercase() } ?: emptyList()
    fun safeFile(context: Context, name: String): File? {
        val clean = name.trim()
        if (!clean.matches(Regex("[A-Za-z0-9 _.-]{1,64}\\.lua", RegexOption.IGNORE_CASE))) return null
        return File(directory(context), clean)
    }
}
