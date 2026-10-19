package id.zenithl.launcher.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings

class PermissionManager(private val context: Context) {
    fun canDisplayOverlay(): Boolean = Settings.canDrawOverlays(context)
    fun overlaySettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
}
