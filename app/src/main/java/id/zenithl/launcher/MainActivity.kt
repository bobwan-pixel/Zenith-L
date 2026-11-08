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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val content = Ui.page(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
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
        val enabled = scripts.count { it.enabled }
        val recent = scripts.maxByOrNull { it.lastRun ?: 0 }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 22))
            addView(TextView(this@MainActivity).apply {
                text = "Z"
                textSize = 20f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                setTextColor(0xFF0B1020.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Ui.BLUE)
                    cornerRadius = Ui.dp(this@MainActivity, 13).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 42), Ui.dp(this@MainActivity, 42))
            })
            val wordmark = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@MainActivity, 11), 0, 0, 0)
                addView(TextView(this@MainActivity).apply {
                    text = "ZENITH'L"
                    textSize = 16f
                    letterSpacing = 0.08f
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    setTextColor(Ui.WHITE)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "LAUNCHER WORKSPACE"
                    textSize = 9f
                    letterSpacing = 0.12f
                    setTextColor(Ui.MUTED)
                })
            }
            addView(wordmark)
        }
        root.addView(brand)
        root.addView(Ui.eyebrow(this, "YOUR CONTROL CENTER"))
        root.addView(Ui.title(this, "Welcome back.", 29f))
        root.addView(Ui.label(this, "Your scripts and runtime tools, in one place."))

        root.addView(Ui.heroCard(
            this,
            "Make room for better scripts.",
            "${game?.versionName?.let { "Growtopia $it detected" } ?: "Growtopia not detected"}  •  Game Bridge unavailable"
        ))
        root.addView(Ui.button(this, "LAUNCH GROWTOPIA  ↗") { launchGrowtopia() })

        root.addView(Ui.section(this, "WORKSPACE OVERVIEW"))
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.metric(this@MainActivity, scripts.size.toString(), "Total scripts"),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 8) })
            addView(Ui.metric(this@MainActivity, enabled.toString(), "Enabled"),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(this@MainActivity, 8) })
        }
        root.addView(metrics)
        root.addView(Ui.card(this, "Runtime profiles", "Manage isolated session profiles from the Accounts tab."))
        root.addView(Ui.card(this, "Item catalog", container.itemDatabase.version?.value?.let { "Catalog version $it" } ?: "Catalog status is available in Diagnostics."))

        root.addView(Ui.section(this, "RECENT ACTIVITY"))
        root.addView(Ui.card(
            this,
            recent?.name ?: "No recent script",
            when {
                recent == null -> "Your workspace is empty. Create or import a Lua script to begin."
                recent.enabled -> "Enabled  •  ${recent.state}"
                else -> "Disabled  •  Open Script Library to manage it"
            }
        ))
        if (recent?.enabled == true) {
            root.addView(Ui.button(this, "RUN ${recent.name.uppercase()}") {
                container.scriptController.run(recent.id, AppContainer.MAIN_SESSION_ID) { result ->
                    result.onSuccess { Toast.makeText(this, "Lua started; Growtopia APIs require Game Bridge.", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                }
            })
        }
    }

    private fun accounts(root: LinearLayout) {
        root.addView(Ui.eyebrow(this, "IDENTITY & RUNTIME"))
        root.addView(Ui.title(this, "Accounts", 28f))
        root.addView(Ui.label(this, "Profiles store account labels only. Authentication requires a trusted identity backend."))
        val accountRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(accountRows)
        container.accountController.list { result -> result.onSuccess { accounts ->
            if (accounts.isEmpty()) accountRows.addView(Ui.card(this, "No account profiles", "Add a local profile label to organize your workspace."))
            accounts.forEach { account -> accountRows.addView(Ui.card(this, account.displayName, "${account.provider}  •  ${account.status}  •  Profile only")) }
        }.onFailure(::showError) }
        root.addView(Ui.button(this, "+ ADD ACCOUNT PROFILE") { addAccountProfile() })
        root.addView(Ui.button(this, "CONTINUE WITH GOOGLE") {
            container.googleCredentialSignIn.signIn(this) { result ->
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Ui.section(this, "SESSION PROFILES"))
        val sessions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(sessions)
        container.botController.list { result ->
            result.onSuccess { rows -> rows.forEach { session ->
                sessions.addView(Ui.card(this, session.profile.accountLabel ?: session.profile.name,
                    "Session ${session.id.take(8)}  •  ${session.state}  •  ${session.profile.world ?: "No world data"}  •  Bridge ${container.gameBridge.status}"))
            } }.onFailure(::showError)
        }
        root.addView(Ui.button(this, "MANAGE SESSION PROFILES") { startActivity(Intent(this, BotManagerActivity::class.java)) })
        root.addView(Ui.card(this, "Google sign-in", "Credential Manager is wired. Account creation remains NOT_CONFIGURED until a trusted ID-token validation backend is available."))
        root.addView(Ui.card(this, "Local development login", "Not implemented. No embedded seed credentials."))
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
        root.addView(Ui.eyebrow(this, "LOCAL LUA ENVIRONMENT"))
        root.addView(Ui.title(this, "Script Library", 28f))
        root.addView(Ui.label(this, "Lua 5.4.9  •  isolated runtime states  •  stored on this device"))
        root.addView(Ui.button(this, "OPEN SCRIPT EDITOR") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.button(this, "IMPORT LUA FILE") { startActivity(Intent(this, EditorActivity::class.java)) })
        root.addView(Ui.section(this, "${scripts.size} SCRIPTS IN WORKSPACE"))
        if (scripts.isEmpty()) root.addView(Ui.card(this, "Nothing here yet", "Create a script or import a .lua file to start building your library."))
        scripts.forEach { script ->
            root.addView(Ui.card(this, "${if (script.favorite) "★  " else ""}${script.name}", "${if (script.enabled) "Enabled" else "Disabled"}  •  Auto-run ${if (script.autoRun) "on" else "off"}  •  ${script.state}"))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, Ui.dp(this@MainActivity, 10))
            }
            actions.addView(Ui.smallButton(this, if (script.enabled) "Disable" else "Enable") {
                container.scriptController.setEnabled(script.id, !script.enabled) { it.onSuccess { render() }.onFailure(::showError) }
            }, LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f).apply { marginEnd = Ui.dp(this@MainActivity, 5) })
            actions.addView(Ui.smallButton(this, if (script.autoRun) "Auto-run ✓" else "Auto-run") {
                container.scriptController.setAutoRun(script.id, !script.autoRun) { it.onSuccess { render() }.onFailure(::showError) }
            }, LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f).apply { marginStart = Ui.dp(this@MainActivity, 5) })
            root.addView(actions)
        }
    }

    private fun settings(root: LinearLayout) {
        root.addView(Ui.eyebrow(this, "PREFERENCES & TOOLS"))
        root.addView(Ui.title(this, "Settings", 28f))
        root.addView(Ui.card(this, "Floating Mod Menu", if (container.permissionManager.canDisplayOverlay()) "Display over other apps: Ready" else "Display over other apps permission required"))
        root.addView(Ui.button(this, if (container.permissionManager.canDisplayOverlay()) "START FLOATING MENU" else "ENABLE DISPLAY OVER OTHER APPS") {
            if (!container.permissionManager.canDisplayOverlay()) startActivity(container.permissionManager.overlaySettingsIntent())
            else { startService(Intent(this, OverlayService::class.java)); Toast.makeText(this, "Floating menu started. Bridge: unavailable.", Toast.LENGTH_LONG).show() }
        })
        root.addView(Ui.section(this, "DEVELOPER TOOLS"))
        root.addView(Ui.button(this, "BOT / SESSION MANAGER") { startActivity(Intent(this, BotManagerActivity::class.java)) })
        root.addView(Ui.button(this, "MOD MENU MODULES") { startActivity(Intent(this, ModMenuActivity::class.java)) })
        root.addView(Ui.button(this, "API EXPLORER") { startActivity(Intent(this, ApiBrowserActivity::class.java)) })
        root.addView(Ui.button(this, "DIAGNOSTICS & SAFETY STATUS") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        root.addView(Ui.section(this, "SYSTEM STATUS"))
        root.addView(Ui.card(this, "Zenith Safety Engine", "Partial implementation. Script and session watchdog foundations are available; game action monitoring requires a real Game Bridge."))
    }

    private fun bottomNavigation() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Ui.PANEL)
            cornerRadii = floatArrayOf(18f, 18f, 18f, 18f, 0f, 0f, 0f, 0f)
            setStroke(Ui.dp(this@MainActivity, 1), Ui.BORDER)
        }
        setPadding(Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8), 0)
        Page.values().forEach { destination ->
            val selected = destination == page
            val label = when (destination) {
                Page.HOME -> "Home"
                Page.ACCOUNTS -> "Accounts"
                Page.SCRIPTS -> "Scripts"
                Page.SETTINGS -> "Settings"
            }
            addView(Ui.navItem(this@MainActivity, label, selected) {
                if (page != destination) { page = destination; render() }
            }, LinearLayout.LayoutParams(0, Ui.dp(this@MainActivity, 58), 1f))
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
