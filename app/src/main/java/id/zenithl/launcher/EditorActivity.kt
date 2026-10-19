package id.zenithl.launcher

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.ui.Ui

class EditorActivity : Activity() {
    private val container get() = (application as ZenithApp).container
    private var currentId: String? = null
    private lateinit var nameField: EditText
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showFiles() }

    private fun showFiles() {
        container.scriptController.list { result -> result.onSuccess { scripts ->
            val root = Ui.page(this)
            root.addView(Ui.title(this, "Script Workspace"))
            root.addView(Ui.label(this, "Persistent metadata • ${scripts.size} scripts"))
            root.addView(Ui.button(this, "+ NEW SCRIPT") { showEditor(null, null) })
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

    private companion object { const val SESSION_ID = AppContainer.MAIN_SESSION_ID }
}
