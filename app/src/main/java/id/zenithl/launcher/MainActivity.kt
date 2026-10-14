package id.zenithl.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = Ui.page(this)
        root.addView(Ui.title(this, "Zenith'L", 30f))
        root.addView(Ui.label(this, "GROWTOPIA TOOLKIT  •  VERSION 0.1.0"))
        root.addView(Ui.button(this, "LAUNCH GROWTOPIA") { launchGrowtopia() })
        root.addView(Ui.button(this, "OPEN LUA FILE MANAGER") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.button(this, "ENABLE FLOATING MENU") { enableOverlay() })
        root.addView(Ui.button(this, "OPEN FLOATING MENU") {
            if (Settings.canDrawOverlays(this)) startService(Intent(this, OverlayService::class.java)) else enableOverlay()
        })
        root.addView(Ui.button(this, "ABOUT / LIMITATIONS") {
            Toast.makeText(this, "Lua editor and file storage are available. In-game Lua execution/API integration is not included yet.", Toast.LENGTH_LONG).show()
        })
        root.addView(Ui.label(this, "Lua files are stored privately inside Zenith'L. This starter does not inject code into Growtopia."))
        setContentView(root)
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Allow display over other apps, then return to Zenith'L.", Toast.LENGTH_LONG).show()
        } else {
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Floating button enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGrowtopia() {
        val intent = packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (intent == null) Toast.makeText(this, "Growtopia is not found. Install the official game first.", Toast.LENGTH_LONG).show()
        else startActivity(intent)
    }
}
