package id.zenithl.launcher

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class EditorActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var editor: android.widget.EditText
    private lateinit var fileName: android.widget.EditText
    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showFiles()
    }

    private fun showFiles() {
        root = Ui.page(this)
        root.addView(Ui.title(this, "Lua Workspace"))
        root.addView(Ui.label(this, "FILE EXPLORER  /  ${ScriptFiles.list(this).size} FILE(S)"))
        root.addView(Ui.button(this, "+ NEW SCRIPT") { showEditor(null) })
        val files = ScriptFiles.list(this)
        if (files.isEmpty()) root.addView(Ui.label(this, "Belum ada file. Tekan + New Script untuk membuat file pertama."))
        files.forEach { file ->
            root.addView(Ui.button(this, "${file.name}   ·   ${file.length()} B") { showEditor(file) })
        }
        root.addView(Ui.button(this, "BACK TO ZENITH'L") { finish() })
        setContentView(Ui.scroll(this, root))
    }

    private fun showEditor(file: File?) {
        currentFile = file
        root = Ui.page(this)
        root.addView(Ui.title(this, if (file == null) "New Lua Script" else file.name))
        root.addView(Ui.label(this, "Local editor • Lua engine not connected yet"))
        fileName = Ui.edit(this, "example.lua").apply { setText(file?.name ?: "") }
        root.addView(fileName)
        editor = Ui.edit(this, "-- Write Lua code here\nprint('Hello from Zenith\'L')", true)
        if (file != null) editor.setText(runCatching { file.readText() }.getOrDefault(""))
        else editor.setText("-- ${"Zenith'L"}\nprint('Hello from Zenith\\'L')\n")
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Ui.button(this, "SAVE SCRIPT") { saveScript() })
        root.addView(Ui.button(this, "DELETE SCRIPT") {
            val target = currentFile
            if (target != null && target.exists() && target.delete()) {
                Toast.makeText(this, "Script deleted", Toast.LENGTH_SHORT).show(); showFiles()
            } else Toast.makeText(this, "Save the script first, or file not found.", Toast.LENGTH_SHORT).show()
        })
        root.addView(Ui.button(this, "BACK TO FILE LIST") { showFiles() })
        setContentView(root)
    }

    private fun saveScript() {
        val name = fileName.text.toString().trim().let { if (it.endsWith(".lua", true)) it else "$it.lua" }
        val target = ScriptFiles.safeFile(this, name)
        if (target == null) {
            Toast.makeText(this, "Filename tidak valid. Gunakan huruf, angka, spasi, titik, _ atau - dan akhiri .lua", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { target.writeText(editor.text.toString()); currentFile = target }
            .onSuccess { Toast.makeText(this, "Saved: ${target.name}", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Save failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
