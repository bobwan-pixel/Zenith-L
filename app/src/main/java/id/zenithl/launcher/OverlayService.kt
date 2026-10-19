package id.zenithl.launcher

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import id.zenithl.launcher.ui.Ui
import id.zenithl.launcher.modules.CapabilityStatus
import id.zenithl.launcher.modules.ModCategory
import id.zenithl.launcher.modules.ModModule
import id.zenithl.launcher.modules.ModModuleRegistry

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) createBubble()
        return START_STICKY
    }

    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 220 }

    private fun createBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = TextView(this).apply {
            text = "Z'"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.BLUE); setStroke(2, Ui.WHITE) }
            setOnTouchListener(object : View.OnTouchListener {
                var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false; var downAt = 0L
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    val lp = view.layoutParams as WindowManager.LayoutParams
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; downAt = System.currentTimeMillis(); moved = false; return true }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                            if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                            lp.x = (startX - dx).coerceAtLeast(0); lp.y = (startY + dy).coerceAtLeast(0)
                            wm.updateViewLayout(view, lp); return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                if (System.currentTimeMillis() - downAt >= 550) showQuickActions() else togglePanel()
                            } else {
                                val screenWidth = resources.displayMetrics.widthPixels
                                lp.x = if (lp.x < screenWidth / 2) 0 else (screenWidth - view.width).coerceAtLeast(0)
                                wm.updateViewLayout(view, lp)
                                (application as ZenithApp).container.overlayController.setPosition(lp.x, lp.y)
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }
        bubble = v
        val lp = params(Ui.dp(this, 54), Ui.dp(this, 54))
        bubbleParams = lp
        wm.addView(v, lp)
        (application as ZenithApp).container.overlayController.load { preferences ->
            if (bubble === v) runCatching {
                lp.x = preferences.x.coerceAtLeast(0); lp.y = preferences.y.coerceAtLeast(0)
                wm.updateViewLayout(v, lp)
            }
        }
    }

    private fun showQuickActions() {
        removePanel()
        val app = (application as ZenithApp).container
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply { setColor(0xF0101E30.toInt()); cornerRadius = 16f; setStroke(2, Ui.BLUE) }
        }
        box.addView(TextView(this).apply { text = "QUICK ACTIONS"; textSize = 16f; setTextColor(Ui.WHITE); setPadding(4, 2, 4, 12) })
        box.addView(action("OPEN MOD MENU") { removePanel(); togglePanel() })
        box.addView(action("PAUSE SCRIPTS") { app.scriptController.pause(AppContainer.MAIN_SESSION_ID) { } })
        box.addView(action("RESUME SCRIPTS") { app.scriptController.resume(AppContainer.MAIN_SESSION_ID) { } })
        box.addView(action("STOP SESSION") { app.emergencyStopSession(AppContainer.MAIN_SESSION_ID) })
        box.addView(action("SCRIPT CONSOLE / EDITOR") { startActivity(Intent(this, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        val emergency = action("HOLD 1.5s TO STOP ALL") { Toast.makeText(this, "Hold this action for 1.5 seconds to confirm global stop.", Toast.LENGTH_LONG).show() }
        var pressedAt = 0L
        emergency.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { pressedAt = System.currentTimeMillis(); true }
                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - pressedAt >= 1500) app.emergencyStopAll()
                    else Toast.makeText(this, "Hold for 1.5 seconds to stop all sessions", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> true
            }
        }
        box.addView(emergency)
        box.addView(action("CLOSE") { removePanel() })
        val scroll = ScrollView(this).apply { addView(box) }
        panel = scroll
        wm.addView(scroll, params(Ui.dp(this, 280), WindowManager.LayoutParams.WRAP_CONTENT).apply { x = 16; y = 285 })
    }

    private fun togglePanel() {
        if (panel != null) { removePanel(); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18)
            background = GradientDrawable().apply { setColor(0xF0101E30.toInt()); cornerRadius = 18f; setStroke(2, Ui.BLUE) }
        }
        box.addView(TextView(this).apply { text = "Zenith'L Workspace"; textSize = 18f; setTextColor(Ui.WHITE); setPadding(0,0,0,12) })
        val app = (application as ZenithApp).container
        box.addView(TextView(this).apply { text = "Session: ${AppContainer.MAIN_SESSION_ID}  •  Bridge: ${app.gameBridge.status}"; textSize = 12f; setTextColor(Ui.MUTED); setPadding(0, 0, 0, 8) })
        val moduleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val moduleDetail = TextView(this).apply { textSize = 12f; setTextColor(Ui.MUTED); setPadding(8, 8, 8, 12) }
        val categoryBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(categoryBar) }
        box.addView(categoryScroll)
        box.addView(moduleList)
        box.addView(moduleDetail)
        fun renderCategory(category: ModCategory) {
            moduleList.removeAllViews()
            moduleDetail.text = "Select a module to inspect status and requirements."
            category.modules.forEach { module ->
                moduleList.addView(action("${module.icon}  ${module.title}  •  ${status(module.status)}") {
                    moduleDetail.text = "${module.title}\n${module.description}\nStatus: ${status(module.status)}" +
                        if (module.requiredBridgeCapabilities.isEmpty()) "" else "\nRequires: ${module.requiredBridgeCapabilities.joinToString()}"
                })
            }
        }
        ModModuleRegistry.categories.forEach { category ->
            categoryBar.addView(action(category.title) { renderCategory(category) }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 6 })
        }
        ModModuleRegistry.category("home")?.let(::renderCategory)
        val selectedLabel = TextView(this).apply { text = "Loading scripts…"; setTextColor(Ui.MUTED); setPadding(0, 0, 0, 8) }
        box.addView(selectedLabel)
        var panelRoot: View? = null
        var selectedScriptId: String? = null
        var compact = false
        app.overlayController.load { preferences ->
            selectedScriptId = preferences.selectedScriptId?.takeIf { it.isNotBlank() }
            compact = preferences.compact
            app.scriptController.list { result ->
                if (panel !== panelRoot) return@list
                val scripts = result.getOrDefault(emptyList())
                selectedScriptId = selectedScriptId?.takeIf { id -> scripts.any { it.id == id } }
                selectedLabel.text = "Selected: ${scripts.firstOrNull { it.id == selectedScriptId }?.name ?: "none"}"
                if (scripts.isEmpty()) box.addView(TextView(this).apply { text = "No scripts yet"; setTextColor(Ui.MUTED); setPadding(0,8,0,8) })
                scripts.take(if (compact) 3 else scripts.size).forEach { script ->
                    box.addView(action("${if (script.id == selectedScriptId) "● " else "○ "}${script.name}  •  ${script.state}") {
                        selectedScriptId = script.id
                        app.overlayController.selectScript(script.id)
                        selectedLabel.text = "Selected: ${script.name}"
                    })
                    box.addView(CheckBox(this).apply {
                        text = "Auto-run on Bridge ready"
                        setTextColor(Ui.WHITE)
                        isChecked = script.autoRun
                        setOnCheckedChangeListener { _, checked -> app.scriptController.setAutoRun(script.id, checked) { change -> change.onFailure { Toast.makeText(this@OverlayService, it.message, Toast.LENGTH_LONG).show() } } }
                    })
                }
                val controls = listOf(
                    "▶  PLAY" to { runSelected(app, selectedScriptId) },
                    "Ⅱ  PAUSE" to { app.scriptController.pause(AppContainer.MAIN_SESSION_ID) { it.onFailure { error -> Toast.makeText(this@OverlayService, error.message, Toast.LENGTH_LONG).show() } } },
                    "▶  RESUME" to { app.scriptController.resume(AppContainer.MAIN_SESSION_ID) { it.onFailure { error -> Toast.makeText(this@OverlayService, error.message, Toast.LENGTH_LONG).show() } } },
                    "■  STOP" to { app.scriptController.stop(AppContainer.MAIN_SESSION_ID) { it.onFailure { error -> Toast.makeText(this@OverlayService, error.message, Toast.LENGTH_LONG).show() } } },
                )
                controls.forEach { (label, callback) -> box.addView(action(label, callback)) }
                box.addView(action(if (compact) "EXPAND" else "COMPACT") {
                    app.overlayController.setCompact(!compact)
                    removePanel(); togglePanel()
                })
            }
        }
        box.addView(action("OPEN LUA FILES") { removePanel(); startActivity(Intent(this, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        box.addView(action("HIDE PANEL") { removePanel() })
        box.addView(action("CLOSE FLOATING BUTTON") { removePanel(); stopSelf() })
        val scroll = ScrollView(this).apply { addView(box) }
        panelRoot = scroll
        panel = scroll
        wm.addView(scroll, params(Ui.dp(this, 320), Ui.dp(this, 520)).apply { x = 16; y = 285; flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL })
    }

    private fun runSelected(app: AppContainer, scriptId: String?) {
        if (scriptId == null) { Toast.makeText(this, "Select a script first", Toast.LENGTH_SHORT).show(); return }
        app.scriptController.run(scriptId, AppContainer.MAIN_SESSION_ID) { result ->
            result.onSuccess { Toast.makeText(this, "Lua execution started. Game APIs remain unavailable.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this, it.message ?: "Could not start script", Toast.LENGTH_LONG).show() }
        }
    }

    private fun action(text: String, run: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Ui.WHITE); gravity = Gravity.CENTER_VERTICAL
        setPadding(12, 14, 12, 14); setOnClickListener { run() }
        background = GradientDrawable().apply { setColor(Ui.PANEL); cornerRadius = 10f }
        val lp = LinearLayout.LayoutParams(-1, WindowManager.LayoutParams.WRAP_CONTENT); lp.bottomMargin = 8; layoutParams = lp
    }

    private fun status(status: CapabilityStatus) = when (status) {
        CapabilityStatus.IMPLEMENTED -> "● Ready"
        CapabilityStatus.PARTIAL -> "◐ Partial"
        CapabilityStatus.INTERFACE_ONLY -> "○ Interface only"
        CapabilityStatus.NOT_IMPLEMENTED -> "× Not implemented"
        CapabilityStatus.UNAVAILABLE -> "× Unavailable"
    }

    private fun removePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }
    override fun onDestroy() { removePanel(); bubble?.let { runCatching { wm.removeView(it) } }; bubble = null; bubbleParams = null; super.onDestroy() }
}
