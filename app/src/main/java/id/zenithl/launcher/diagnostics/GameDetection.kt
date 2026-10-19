package id.zenithl.launcher.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class InstalledGame(val packageName: String, val versionName: String?, val versionCode: Long, val launchIntent: Intent?)
enum class GameLifecycleState { NOT_INSTALLED, INSTALLED, LAUNCH_REQUESTED }
data class GameDetectionSnapshot(val packageName: String, val state: GameLifecycleState, val game: InstalledGame?, val compatibility: CompatibilityProfile)
data class CompatibilityProfile(val id: String, val gameVersion: String?, val supported: Boolean, val explanation: String)
fun interface InstalledGameProvider { fun installedGame(): InstalledGame? }

/** Package metadata only. Android does not expose another app's foreground lifecycle to ordinary apps. */
class GamePackageManager(private val context: Context, private val targetPackage: String = DEFAULT_GAME_PACKAGE) : InstalledGameProvider {
    override fun installedGame(): InstalledGame? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfoCompat(targetPackage)
        InstalledGame(targetPackage, info.versionName, info.longVersionCodeCompat(), pm.getLaunchIntentForPackage(targetPackage))
    }.getOrNull()

    companion object { const val DEFAULT_GAME_PACKAGE = "com.rtsoft.growtopia" }
}

class GameVersionDetector {
    fun detect(game: InstalledGame?): String? = game?.versionName?.takeIf { it.isNotBlank() }
}

class GameCompatibilityManager {
    fun profile(version: String?): CompatibilityProfile = CompatibilityProfile(
        id = "growtopia-unverified",
        gameVersion = version,
        supported = false,
        explanation = "No verified Game Bridge compatibility profile is bundled."
    )
}

class GameDetector(
    private val packages: InstalledGameProvider,
    private val versions: GameVersionDetector = GameVersionDetector(),
    private val compatibility: GameCompatibilityManager = GameCompatibilityManager(),
) {
    @Volatile private var launchRequested = false
    fun detect(): GameDetectionSnapshot {
        val game = packages.installedGame()
        return GameDetectionSnapshot(
            packageName = game?.packageName ?: GamePackageManager.DEFAULT_GAME_PACKAGE,
            state = when { game == null -> GameLifecycleState.NOT_INSTALLED; launchRequested -> GameLifecycleState.LAUNCH_REQUESTED; else -> GameLifecycleState.INSTALLED },
            game = game,
            compatibility = compatibility.profile(versions.detect(game)),
        )
    }
    fun markLaunchRequested() { launchRequested = true }
    fun clearLaunchRequest() { launchRequested = false }
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= 33) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)) else getPackageInfo(packageName, 0)
