package id.zenithl.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class GameWatcherService : AccessibilityService() {
    private var overlayVisibleForGame = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val gameIsForeground = event?.packageName?.toString() == GAME_PACKAGE
        if (gameIsForeground && !overlayVisibleForGame && Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
            overlayVisibleForGame = true
        } else if (!gameIsForeground && overlayVisibleForGame) {
            stopService(Intent(this, OverlayService::class.java))
            overlayVisibleForGame = false
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val GAME_PACKAGE = "com.rtsoft.growtopia"
    }
}
