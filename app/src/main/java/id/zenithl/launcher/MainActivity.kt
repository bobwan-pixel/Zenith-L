package id.zenithl.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.ui.Ui

class MainActivity : Activity() {
    private val container get() = (application as ZenithApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(Ui.scroll(this, Ui.page(this).apply { addView(Ui.title(this@MainActivity, "Zenith'L")); addView(Ui.label(this@MainActivity, "Loading workspace…")) }))
    }

    override fun onResume() {
        super.onResume()
        container.scriptController.list { result ->
            result.onSuccess(::render).onFailure { Toast.makeText(this, "Workspace error: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun render(scripts: List<ScriptRecord>) {
        val root = Ui.page(this)
        root.addView(Ui.title(this, "Zenith'L", 30f))
        root.addView(Ui.label(this, "RUNTIME PLATFORM  •  CORE 0.4.0"))
        val game = container.gameDetector.detect()
        root.addView(Ui.label(this, "GAME PACKAGE  •  ${game.state}  •  ${game.packageName}"))
        root.addView(Ui.label(this, "GAME VERSION  •  ${game.game?.versionName ?: "unknown"} (${game.game?.versionCode ?: "—"})"))
        root.addView(Ui.label(this, "BRIDGE  •  ${container.gameBridge.status}  •  ${container.gameBridge.diagnostic}"))
        root.addView(Ui.label(this, "OVERLAY  •  ${if (container.permissionManager.canDisplayOverlay()) "READY" else "PERMISSION REQUIRED"}"))
        root.addView(Ui.label(this, "Item database is app-global and shared by all sessions. Installed: ${container.itemDatabase.version?.value ?: "no compatible catalog"}."))
        root.addView(Ui.label(this, "Enabled scripts can run from the workspace. Auto-run waits for a supported Game Bridge lifecycle signal."))
        if (scripts.isEmpty()) root.addView(Ui.label(this, "Belum ada script. Buat file pertama di Script Workspace."))
        scripts.forEach { script ->
            root.addView(Ui.label(this, "${script.name}.lua  •  ${script.state}"))
            root.addView(CheckBox(this).apply {
                text = "Enabled"
                setTextColor(Ui.WHITE)
                isChecked = script.enabled
                setOnCheckedChangeListener { _, checked -> container.scriptController.setEnabled(script.id, checked) { result ->
                    result.onFailure { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                } }
            })
            root.addView(CheckBox(this).apply {
                text = "Auto-run when Game Bridge signals ready"
                setTextColor(Ui.WHITE)
                isChecked = script.autoRun
                setOnCheckedChangeListener { _, checked -> container.scriptController.setAutoRun(script.id, checked) { result ->
                    result.onFailure { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                } }
            })
        }
        root.addView(Ui.button(this, "LAUNCH GROWTOPIA") { launchGrowtopia() })
        root.addView(Ui.button(this, "OPEN SCRIPT WORKSPACE") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.button(this, "BOT MANAGER") { startActivity(Intent(this, BotManagerActivity::class.java)) })
        root.addView(Ui.button(this, "RUNTIME DIAGNOSTICS") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        root.addView(Ui.button(this, "ENABLE FLOATING OVERLAY") { setupAutoOverlay() })
        root.addView(Ui.label(this, "Lua 5.4 runtime is native and isolated per session. Game data and GL game APIs require a supported Game Bridge."))
        root.addView(Ui.button(this, "ABOUT / API STATUS") {
            startActivity(Intent(this, ApiBrowserActivity::class.java))
        })
        setContentView(Ui.scroll(this, root))
    }

    private fun setupAutoOverlay() {
        if (!container.permissionManager.canDisplayOverlay()) {
            startActivity(container.permissionManager.overlaySettingsIntent())
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain, lalu tekan setup lagi.", Toast.LENGTH_LONG).show()
            return
        }
        startService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay siap. Deteksi foreground game dan Game Bridge belum tersedia.", Toast.LENGTH_LONG).show()
    }

    private fun launchGrowtopia() {
        val game = container.gameDetector.detect().game
        val intent = game?.launchIntent
        if (intent == null) Toast.makeText(this, "Growtopia tidak ditemukan atau tidak memiliki launch intent.", Toast.LENGTH_LONG).show()
        else {
            container.gameDetector.markLaunchRequested()
            if (container.permissionManager.canDisplayOverlay()) startService(Intent(this, OverlayService::class.java))
            startActivity(intent)
        }
    }
}
