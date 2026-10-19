package id.zenithl.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import id.zenithl.launcher.scripts.ScriptRecord
import id.zenithl.launcher.ui.Ui

/** Product shell. Technical diagnostics live behind Settings instead of occupying Home. */
class MainActivity : Activity() {
    private val container get() = (application as ZenithApp).container
    private var page = Page.HOME
    private var scripts: List<ScriptRecord> = emptyList()

    private enum class Page { HOME, ACCOUNTS, SCRIPTS, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = runCatching { Page.valueOf(intent.getStringExtra(EXTRA_PAGE) ?: "HOME") }.getOrDefault(Page.HOME)
        render()
    }

    override fun onResume() {
        super.onResume()
        container.prepareScripts { migration ->
            migration.onFailure { Toast.makeText(this, "Workspace migration error: ${it.message}", Toast.LENGTH_LONG).show() }
            if (migration.isSuccess) container.scriptController.list { result ->
                result.onSuccess { scripts = it; render() }
                    .onFailure { Toast.makeText(this, "Workspace error: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        val content = Ui.page(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        content.addView(Ui.label(this, "ZENITH'L  /  ${page.name}"))
        when (page) {
            Page.HOME -> home(content)
            Page.ACCOUNTS -> accounts(content)
            Page.SCRIPTS -> scriptLibrary(content)
            Page.SETTINGS -> settings(content)
        }
        root.addView(Ui.scroll(this, content), LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNavigation())
        setContentView(root)
    }

    private fun home(root: LinearLayout) {
        val game = container.gameDetector.detect().game
        root.addView(Ui.title(this, "Welcome to Zenith'L", 29f))
        root.addView(Ui.label(this, "Launcher and Lua workspace"))
        root.addView(Ui.card(this, "GROWTOPIA", "${game?.versionName?.let { "Version $it" } ?: "Not installed"}  •  Bridge unavailable"))
        root.addView(Ui.button(this, "LAUNCH GROWTOPIA") { launchGrowtopia() })
        val enabled = scripts.count { it.enabled }
        root.addView(Ui.section(this, "YOUR WORKSPACE"))
        root.addView(Ui.card(this, "SESSIONS", "Manage isolated runtime profiles in Accounts"))
        root.addView(Ui.card(this, "$enabled ENABLED SCRIPT${if (enabled == 1) "" else "S"}", scripts.firstOrNull { it.enabled }?.name ?: "No enabled scripts"))
        root.addView(Ui.card(this, "ITEM CATALOG", container.itemDatabase.version?.value?.let { "Catalog $it" } ?: "Catalog status available in Diagnostics"))
        root.addView(Ui.section(this, "RECENT SCRIPT"))
        val recent = scripts.maxByOrNull { it.lastRun ?: 0 }
        root.addView(Ui.card(this, recent?.name ?: "No recent script", when {
            recent == null -> "Create or import a Lua file in Scripts"
            recent.enabled -> "Enabled  •  ${recent.state}"
            else -> "Disabled"
        }))
        if (recent?.enabled == true) root.addView(Ui.button(this, "PLAY ${recent.name.uppercase()}") {
            container.scriptController.run(recent.id, AppContainer.MAIN_SESSION_ID) { result ->
                result.onSuccess { Toast.makeText(this, "Lua started; Growtopia APIs require Game Bridge.", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
            }
        })
    }

    private fun accounts(root: LinearLayout) {
        root.addView(Ui.title(this, "Accounts & Sessions", 27f))
        root.addView(Ui.label(this, "Profiles store account labels only. Authentication is unavailable until a trusted identity backend is configured."))
        val accountRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(accountRows)
        container.accountController.list { result -> result.onSuccess { accounts ->
            if (accounts.isEmpty()) accountRows.addView(Ui.label(this, "No account profiles yet"))
            accounts.forEach { account -> accountRows.addView(Ui.card(this, account.displayName, "${account.provider}  •  ${account.status}  •  Profile only")) }
        }.onFailure(::showError) }
        root.addView(Ui.button(this, "+ ADD ACCOUNT PROFILE") { addAccountProfile() })
        root.addView(Ui.button(this, "CONTINUE WITH GOOGLE") {
            container.googleCredentialSignIn.signIn(this) { result ->
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        })
        val sessions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(sessions)
        container.botController.list { result ->
            result.onSuccess { rows -> rows.forEach { session ->
                sessions.addView(Ui.card(this, session.profile.accountLabel ?: session.profile.name,
                    "Session ${session.id.take(8)}  •  ${session.state}  •  ${session.profile.world ?: "No world data"}  •  Bridge ${container.gameBridge.status}"))
            } }.onFailure(::showError)
        }
        root.addView(Ui.button(this, "MANAGE SESSION PROFILES") { startActivity(Intent(this, BotManagerActivity::class.java)) })
        root.addView(Ui.card(this, "Google sign-in", "Credential Manager client is wired. Account creation is NOT_CONFIGURED until a trusted ID-token validation backend is available."))
        root.addView(Ui.card(this, "Local development login", "NOT IMPLEMENTED  •  no embedded seed credentials"))
    }

    private fun addAccountProfile() {
        val field = EditText(this).apply { hint = "Account label"; setTextColor(Ui.WHITE); setPadding(36, 20, 36, 20) }
        AlertDialog.Builder(this).setTitle("Add account profile").setMessage("This stores a local label only; it does not sign in to Growtopia.")
            .setView(field).setPositiveButton("Save") { _, _ ->
                container.accountController.addProfile(field.text.toString(), id.zenithl.launcher.accounts.AccountProvider.LOCAL_DEVELOPMENT) { result ->
                    result.onSuccess { render() }.onFailure(::showError)
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun scriptLibrary(root: LinearLayout) {
        root.addView(Ui.title(this, "Script Library", 27f))
        root.addView(Ui.label(this, "Native Lua 5.4.9  •  scripts run in isolated session VMs"))
        root.addView(Ui.button(this, "OPEN SCRIPT EDITOR") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.button(this, "IMPORT LUA FILE") { startActivity(Intent(this, EditorActivity::class.java)) })
        if (scripts.isEmpty()) root.addView(Ui.card(this, "Workspace is empty", "Create a script or import a .lua file to get started"))
        scripts.forEach { script ->
            root.addView(Ui.card(this, "${if (script.favorite) "★  " else ""}${script.name}", "${if (script.enabled) "Enabled" else "Disabled"}  •  Auto-run ${if (script.autoRun) "on" else "off"}  •  ${script.state}"))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Ui.smallButton(this, if (script.enabled) "Disable" else "Enable") {
                container.scriptController.setEnabled(script.id, !script.enabled) { it.onSuccess { render() }.onFailure(::showError) }
            }, LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f))
            actions.addView(Ui.smallButton(this, if (script.autoRun) "Auto-run ✓" else "Auto-run") {
                container.scriptController.setAutoRun(script.id, !script.autoRun) { it.onSuccess { render() }.onFailure(::showError) }
            }, LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f))
            root.addView(actions)
        }
    }

    private fun settings(root: LinearLayout) {
        root.addView(Ui.title(this, "Settings", 27f))
        root.addView(Ui.card(this, "Floating Mod Menu", if (container.permissionManager.canDisplayOverlay()) "Display over other apps: Ready" else "Display over other apps permission required"))
        root.addView(Ui.button(this, if (container.permissionManager.canDisplayOverlay()) "START FLOATING MENU" else "ENABLE DISPLAY OVER OTHER APPS") {
            if (!container.permissionManager.canDisplayOverlay()) startActivity(container.permissionManager.overlaySettingsIntent())
            else { startService(Intent(this, OverlayService::class.java)); Toast.makeText(this, "Floating menu started. Bridge: unavailable.", Toast.LENGTH_LONG).show() }
        })
        root.addView(Ui.button(this, "BOT / SESSION MANAGER") { startActivity(Intent(this, BotManagerActivity::class.java)) })
        root.addView(Ui.button(this, "MOD MENU MODULES") { startActivity(Intent(this, ModMenuActivity::class.java)) })
        root.addView(Ui.button(this, "API EXPLORER") { startActivity(Intent(this, ApiBrowserActivity::class.java)) })
        root.addView(Ui.button(this, "DIAGNOSTICS & SAFETY STATUS") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        root.addView(Ui.card(this, "Zenith Safety Engine", "PARTIAL  •  script and session watchdog foundations are being added; no game action monitoring without Bridge"))
    }

    private fun bottomNavigation() = LinearLayout(this).apply {
        setBackgroundColor(Ui.PANEL)
        Page.values().forEach { destination ->
            val selected = destination == page
            addView(TextView(this@MainActivity).apply {
                text = destination.name.lowercase().replaceFirstChar { it.uppercase() }
                textSize = 12f; gravity = Gravity.CENTER
                setTextColor(if (selected) Ui.BLUE else Ui.MUTED)
                setPadding(2, Ui.dp(this@MainActivity, 14), 2, Ui.dp(this@MainActivity, 14))
                setOnClickListener { if (page != destination) { page = destination; render() } }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun launchGrowtopia() {
        val intent = container.gameDetector.detect().game?.launchIntent
        if (intent == null) Toast.makeText(this, "Growtopia is not installed or has no launch activity.", Toast.LENGTH_LONG).show()
        else {
            container.gameDetector.markLaunchRequested()
            if (container.permissionManager.canDisplayOverlay()) startService(Intent(this, OverlayService::class.java))
            startActivity(intent)
        }
    }

    private fun showError(error: Throwable) = Toast.makeText(this, error.message ?: "Operation failed", Toast.LENGTH_LONG).show()

    companion object { const val EXTRA_PAGE = "page" }
}
