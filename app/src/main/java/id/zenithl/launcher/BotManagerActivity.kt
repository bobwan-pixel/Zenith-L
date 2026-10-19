package id.zenithl.launcher

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import id.zenithl.launcher.sessions.BotSession
import id.zenithl.launcher.ui.Ui

class BotManagerActivity : Activity() {
    private val container get() = (application as ZenithApp).container
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); refresh() }

    private fun refresh() {
        container.botController.list { result -> result.onSuccess { sessions ->
            val root = Ui.page(this)
            root.addView(Ui.title(this, "BOT MANAGER"))
            root.addView(Ui.label(this, "${sessions.size}/3 session profiles • account credentials and game bridge are not connected"))
            sessions.forEach { session -> root.addView(sessionCard(session)) }
            root.addView(Ui.button(this, "+ ADD SESSION PROFILE") { createSession(sessions.size) })
            root.addView(Ui.button(this, "BACK") { finish() })
            setContentView(Ui.scroll(this, root))
        }.onFailure { showError(it) } }
    }

    private fun sessionCard(session: BotSession): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(this@BotManagerActivity, 12), Ui.dp(this@BotManagerActivity, 10), Ui.dp(this@BotManagerActivity, 12), Ui.dp(this@BotManagerActivity, 10))
        setBackgroundColor(Ui.PANEL)
        addView(Ui.title(this@BotManagerActivity, session.profile.accountLabel ?: session.profile.name, 18f))
        addView(Ui.label(this@BotManagerActivity, "World: ${session.profile.world ?: "—"}  •  Script: ${session.profile.scriptId ?: "none"}"))
        addView(Ui.label(this@BotManagerActivity, "${session.state}  •  Game Bridge: NOT_IMPLEMENTED"))
        addView(Ui.button(this@BotManagerActivity, "ATTACH SCRIPT") { chooseScript(session.id) })
        addView(Ui.button(this@BotManagerActivity, "DETACH SCRIPT") { container.botController.attach(session.id, null) { result -> result.onSuccess { refresh() }.onFailure { showError(it) } } })
        addView(Ui.button(this@BotManagerActivity, "START") { container.botController.start(session.id) { result -> result.onSuccess { Toast.makeText(this@BotManagerActivity, "Session not started: Game Bridge is not implemented", Toast.LENGTH_LONG).show() }.onFailure { showError(it) } } })
        addView(Ui.button(this@BotManagerActivity, "PAUSE") { container.botController.pause(session.id) { result -> result.onFailure { showError(it) } } })
        addView(Ui.button(this@BotManagerActivity, "STOP") { container.botController.stop(session.id) { result -> result.onSuccess { refresh() }.onFailure { showError(it) } } })
        addView(Ui.button(this@BotManagerActivity, "RESTART") { container.botController.restart(session.id) { result -> result.onFailure { showError(it) } } })
        if (session.id != AppContainer.MAIN_SESSION_ID) addView(Ui.button(this@BotManagerActivity, "REMOVE PROFILE") {
            container.botController.delete(session.id) { result -> result.onSuccess { refresh() }.onFailure { showError(it) } }
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@BotManagerActivity, 12) }
    }

    private fun createSession(existing: Int) {
        val number = existing + 1
        val name = EditText(this).apply { hint = "Account $number" }
        AlertDialog.Builder(this).setTitle("New session profile").setView(name)
            .setPositiveButton("Create") { _, _ ->
                val label = name.text.toString().trim().ifBlank { "Account $number" }
                container.botController.create(label, label) { result -> result.onSuccess { refresh() }.onFailure { showError(it) } }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseScript(sessionId: String) {
        container.scriptController.list { result -> result.onSuccess { scripts ->
            if (scripts.isEmpty()) { Toast.makeText(this, "No scripts available", Toast.LENGTH_SHORT).show(); return@onSuccess }
            AlertDialog.Builder(this).setTitle("Attach script")
                .setItems(scripts.map { "${it.name}${if (it.enabled) "" else " (disabled)"}" }.toTypedArray()) { _, index ->
                    if (!scripts[index].enabled) { Toast.makeText(this, "Enable this script before running it", Toast.LENGTH_LONG).show(); return@setItems }
                    container.botController.attach(sessionId, scripts[index].id) { attached -> attached.onSuccess { refresh() }.onFailure { showError(it) } }
                }.setNegativeButton("Cancel", null).show()
        }.onFailure { showError(it) } }
    }

    private fun showError(error: Throwable) = Toast.makeText(this, error.message ?: "Operation failed", Toast.LENGTH_LONG).show()
}
