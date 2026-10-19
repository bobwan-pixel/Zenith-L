package id.zenithl.launcher

import android.app.Activity
import android.os.Bundle
import id.zenithl.launcher.ui.Ui

class DiagnosticsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as ZenithApp).container
        val detection = app.gameDetector.detect()
        val root = Ui.page(this)
        root.addView(Ui.title(this, "RUNTIME DIAGNOSTICS"))
        root.addView(Ui.label(this, "Game package: ${detection.packageName}"))
        root.addView(Ui.label(this, "Detection: ${detection.state}"))
        root.addView(Ui.label(this, "Version: ${detection.game?.versionName ?: "unknown"} • code ${detection.game?.versionCode ?: "unknown"}"))
        root.addView(Ui.label(this, "Compatibility: ${detection.compatibility.id} • ${if (detection.compatibility.supported) "verified" else "unverified"}"))
        root.addView(Ui.label(this, "Compatibility detail: ${detection.compatibility.explanation}"))
        root.addView(Ui.label(this, "Game Bridge: ${app.gameBridge.status}"))
        root.addView(Ui.label(this, app.gameBridge.diagnostic))
        root.addView(Ui.label(this, "Overlay permission: ${if (app.permissionManager.canDisplayOverlay()) "READY" else "MISSING"}"))
        root.addView(Ui.label(this, "Global item catalog: ${app.itemDatabase.version?.value ?: "not installed"}"))
        root.addView(Ui.label(this, "Sessions: ${app.botManager.getBots().size} • Lua execution uses isolated native states"))
        root.addView(Ui.button(this, "BACK") { finish() })
        setContentView(Ui.scroll(this, root))
    }
}
