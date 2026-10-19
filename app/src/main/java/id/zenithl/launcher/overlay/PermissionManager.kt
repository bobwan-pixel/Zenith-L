package id.zenithl.launcher.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

class PermissionManager(private val context: Context) {
    fun canDisplayOverlay(): Boolean = Settings.canDrawOverlays(context)
    fun overlaySettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun isWatcherEnabled(serviceClassName: String): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val expected = "${context.packageName}/$serviceClassName"
        return TextUtils.SimpleStringSplitter(':').run { setString(enabled); any { it.equals(expected, true) } }
    }
}
