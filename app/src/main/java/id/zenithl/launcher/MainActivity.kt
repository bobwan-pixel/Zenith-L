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
        root.addView(Ui.label(this, "SCRIPT WORKSPACE  •  CORE 0.3.0"))
        root.addView(Ui.label(this, "Pilih script aktif dan script yang dijadwalkan saat Growtopia terdeteksi."))
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
                text = "Auto-run when Growtopia opens"
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
        root.addView(Ui.button(this, "SET UP AUTO FLOATING MENU") { setupAutoOverlay() })
        root.addView(Ui.label(this, "Lua 5.4 runtime is real and isolated per session. Game APIs need a Game Bridge and remain INTERFACE_ONLY."))
        root.addView(Ui.button(this, "ABOUT / API STATUS") {
            Toast.makeText(this, "Game Bridge: INTERFACE_ONLY • GL state/network APIs are unavailable.", Toast.LENGTH_LONG).show()
        })
        setContentView(Ui.scroll(this, root))
    }

    private fun setupAutoOverlay() {
        if (!container.permissionManager.canDisplayOverlay()) {
            startActivity(container.permissionManager.overlaySettingsIntent())
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain, lalu tekan setup lagi.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(container.permissionManager.accessibilitySettingsIntent())
        Toast.makeText(this, "Aktifkan Zenith'L App Watcher di Accessibility.", Toast.LENGTH_LONG).show()
    }

    private fun launchGrowtopia() {
        val intent = packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (intent == null) Toast.makeText(this, "Growtopia tidak ditemukan. Instal game resmi terlebih dahulu.", Toast.LENGTH_LONG).show()
        else {
            container.growtopiaDetector.markStarting()
            if (container.permissionManager.canDisplayOverlay()) startService(Intent(this, OverlayService::class.java))
            startActivity(intent)
        }
    }
}
