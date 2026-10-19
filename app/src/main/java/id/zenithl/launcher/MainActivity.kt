package id.zenithl.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<android.view.View>(android.R.id.content) != null) render()
    }

    private fun render() {
        val root = Ui.page(this)
        root.addView(Ui.title(this, "Zenith'L", 30f))
        root.addView(Ui.label(this, "GROWTOPIA TOOLKIT  •  VERSION 0.2.0"))
        root.addView(Ui.label(this, "Pilih script yang akan disiapkan saat Growtopia dibuka."))
        val scripts = ScriptFiles.list(this)
        if (scripts.isEmpty()) root.addView(Ui.label(this, "Belum ada script Lua. Buat dulu di Lua File Manager."))
        scripts.forEach { file ->
            root.addView(CheckBox(this).apply {
                text = "Auto-run: ${file.name}"
                setTextColor(Ui.WHITE)
                isChecked = ScriptFiles.isAutoRun(this@MainActivity, file)
                setOnCheckedChangeListener { _, checked -> ScriptFiles.setAutoRun(this@MainActivity, file, checked) }
            })
        }
        root.addView(Ui.button(this, "LAUNCH GROWTOPIA") { launchGrowtopia() })
        root.addView(Ui.button(this, "OPEN LUA FILE MANAGER") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.button(this, "SET UP AUTO FLOATING MENU") { setupAutoOverlay() })
        root.addView(Ui.label(this, "Pilihan auto-run tersimpan, tetapi runtime Lua dan integrasi in-game belum tersedia."))
        root.addView(Ui.button(this, "ABOUT / LIMITATIONS") {
            Toast.makeText(this, "Overlay tampil saat game terbuka. Eksekusi Lua belum didukung.", Toast.LENGTH_LONG).show()
        })
        setContentView(Ui.scroll(this, root))
    }

    private fun setupAutoOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain, lalu tekan setup lagi.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Aktifkan Zenith'L App Watcher di Accessibility agar overlay muncul saat Growtopia dibuka.", Toast.LENGTH_LONG).show()
    }

    private fun launchGrowtopia() {
        val intent = packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (intent == null) Toast.makeText(this, "Growtopia tidak ditemukan. Instal game resmi terlebih dahulu.", Toast.LENGTH_LONG).show()
        else {
            if (Settings.canDrawOverlays(this)) startService(Intent(this, OverlayService::class.java))
            startActivity(intent)
        }
    }
}
