package id.zenithl.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GameWatcherService : AccessibilityService() {
    private var gameForeground = false
    private var overlayVisibleForGame = false
    private val container get() = (application as ZenithApp).container

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val gameIsForeground = event?.packageName?.toString() == GAME_PACKAGE
        if (gameIsForeground && !gameForeground) {
            gameForeground = true
            container.growtopiaDetector.onForegroundEvent(GAME_PACKAGE)
            container.onGrowtopiaDetected()
        }
        if (gameIsForeground && !overlayVisibleForGame && container.permissionManager.canDisplayOverlay()) {
            startService(Intent(this, OverlayService::class.java))
            overlayVisibleForGame = true
        } else if (!gameIsForeground && gameForeground) {
            if (overlayVisibleForGame) stopService(Intent(this, OverlayService::class.java))
            overlayVisibleForGame = false
            gameForeground = false
            container.growtopiaDetector.onForegroundEvent(event?.packageName?.toString())
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (overlayVisibleForGame) stopService(Intent(this, OverlayService::class.java))
        overlayVisibleForGame = false
        gameForeground = false
        container.growtopiaDetector.onGameProcessStopped()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val GAME_PACKAGE = "com.rtsoft.growtopia"
    }
}
