package id.zenithl.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.ByteArrayOutputStream
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.ui.Ui

class EditorActivity : Activity() {
    private val container get() = (application as ZenithApp).container
    private var currentId: String? = null
    private lateinit var nameField: EditText
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); container.prepareScripts { result -> result.onSuccess { showFiles() }.onFailure(::showError) } }

    private fun showFiles() {
        container.scriptController.list { result -> result.onSuccess { scripts ->
            val root = Ui.page(this)
            root.addView(Ui.title(this, "Script Workspace"))
            root.addView(Ui.label(this, "Persistent metadata • ${scripts.size} scripts"))
            root.addView(Ui.button(this, "+ NEW SCRIPT") { showEditor(null, null) })
            root.addView(Ui.button(this, "IMPORT LUA FILE") { openLuaPicker() })
            scripts.forEach { script ->
                root.addView(Ui.button(this, "${if (script.favorite) "★ " else ""}${script.name}.lua  •  ${script.state}") { open(script.id) })
            }
            root.addView(Ui.button(this, "API REFERENCE") { startActivity(android.content.Intent(this, ApiBrowserActivity::class.java)) })
            root.addView(Ui.button(this, "BACK TO ZENITH'L") { finish() })
            setContentView(Ui.scroll(this, root))
        }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() } }
    }

    private fun open(id: String) {
        container.scriptController.open(id) { result -> result.onSuccess { (script, source) -> showEditor(script, source) }
            .onFailure { Toast.makeText(this, "Cannot open script: ${it.message}", Toast.LENGTH_LONG).show() } }
    }

    private fun showEditor(script: ScriptRecord?, source: String?) {
        currentId = script?.id
        val root = Ui.page(this)
        root.addView(Ui.title(this, script?.name ?: "New Lua Script"))
        root.addView(Ui.label(this, "Lua 5.4.9 • disabled I/O, OS, package loading • GL bridge APIs remain unavailable"))
        nameField = Ui.edit(this, "example.lua").apply { setText(script?.name?.plus(".lua") ?: "") }
        root.addView(nameField)
        editor = Ui.edit(this, "-- Lua 5.4 script", true).apply { setText(source ?: "-- Zenith'L Lua 5.4.9\nprint('Hello from Zenith\\'L')\n") }
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Ui.button(this, "SAVE SCRIPT") { saveScript() })
        if (script != null) {
            root.addView(Ui.button(this, if (script.enabled) "DISABLE SCRIPT" else "ENABLE SCRIPT") {
                container.scriptController.setEnabled(script.id, !script.enabled) { result -> result.onSuccess { open(script.id) }.onFailure { showError(it) } }
            })
            root.addView(Ui.button(this, if (script.favorite) "UNFAVORITE" else "ADD FAVORITE") {
                container.scriptController.setFavorite(script.id, !script.favorite) { result -> result.onSuccess { open(script.id) }.onFailure { showError(it) } }
            })
            root.addView(Ui.button(this, "DUPLICATE SCRIPT") {
                container.scriptController.duplicate(script.id, "${script.name} copy") { result -> result.onSuccess { showFiles() }.onFailure { showError(it) } }
            })
            root.addView(Ui.button(this, "▶ PLAY") { runScript(script.id) })
            root.addView(Ui.button(this, "Ⅱ PAUSE") { container.scriptController.pause(SESSION_ID) { result -> result.onFailure { showError(it) } } })
            root.addView(Ui.button(this, "▶ RESUME") { container.scriptController.resume(SESSION_ID) { result -> result.onFailure { showError(it) } } })
            root.addView(Ui.button(this, "■ STOP") { container.scriptController.stop(SESSION_ID) { result -> result.onSuccess { Toast.makeText(this, "Stop requested", Toast.LENGTH_SHORT).show() }.onFailure { showError(it) } } })
            root.addView(Ui.button(this, "DELETE SCRIPT") {
                container.scriptController.delete(script.id) { result -> result.onSuccess { Toast.makeText(this, "Script deleted", Toast.LENGTH_SHORT).show(); showFiles() }.onFailure { showError(it) } }
            })
        }
        root.addView(Ui.button(this, "BACK TO FILE LIST") { showFiles() })
        setContentView(root)
    }

    private fun saveScript() {
        val name = nameField.text.toString().trim().removeSuffix(".lua")
        container.scriptController.save(currentId, name, editor.text.toString()) { result ->
            result.onSuccess { saved -> currentId = saved.id; Toast.makeText(this, "Saved: ${saved.name}.lua", Toast.LENGTH_SHORT).show() }
                .onFailure { showError(it) }
        }
    }

    private fun runScript(id: String) {
        container.scriptController.run(id, SESSION_ID) { result -> result.onSuccess { Toast.makeText(this, "Lua script started", Toast.LENGTH_SHORT).show() }.onFailure { showError(it) } }
    }

    private fun showError(error: Throwable) = Toast.makeText(this, error.message ?: "Operation failed", Toast.LENGTH_LONG).show()

    private fun openLuaPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-lua", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_LUA)
    }

    @Deprecated("Legacy activity result API is supported on the project's minSdk and keeps import self-contained.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_LUA || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        Thread({
            val imported = runCatching {
                val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "imported.lua"
                require(displayName.endsWith(".lua", ignoreCase = true)) { "Choose a .lua file" }
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_IMPORT_BYTES) { "Lua file exceeds 1 MiB" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Could not read selected file")
                displayName.substringBeforeLast('.') to bytes.toString(Charsets.UTF_8)
            }
            runOnUiThread {
                imported.onSuccess { (name, source) ->
                    container.scriptController.save(null, name, source) { result ->
                        result.onSuccess { Toast.makeText(this, "Imported ${it.name}.lua", Toast.LENGTH_SHORT).show(); showFiles() }
                            .onFailure(::showError)
                    }
                }.onFailure(::showError)
            }
        }, "zenith-lua-import").start()
    }

    private companion object { const val SESSION_ID = AppContainer.MAIN_SESSION_ID; const val REQUEST_IMPORT_LUA = 431; const val MAX_IMPORT_BYTES = 1024 * 1024 }
}
