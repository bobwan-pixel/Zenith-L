package id.zenithl.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import id.zenithl.launcher.modules.CapabilityStatus
import id.zenithl.launcher.modules.ModCategory
import id.zenithl.launcher.modules.ModModule
import id.zenithl.launcher.modules.ModModuleRegistry
import id.zenithl.launcher.ui.Ui

/** Responsive category → module → detail browser. Only registry-declared capabilities are shown. */
class ModMenuActivity : Activity() {
    private var categoryId = "home"
    private var moduleId = "runtime"
    private var query = ""
    private val favorites by lazy { getSharedPreferences("module-favorites", MODE_PRIVATE) }
    private val favoriteIds by lazy { favorites.getStringSet("ids", emptySet()).orEmpty().toMutableSet() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryId = savedInstanceState?.getString("category") ?: "home"
        moduleId = savedInstanceState?.getString("module") ?: "runtime"
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("category", categoryId); outState.putString("module", moduleId)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val root = Ui.page(this)
        root.addView(Ui.title(this, "Mod Menu", 26f))
        root.addView(Ui.label(this, "MODULE REGISTRY  •  ${ModModuleRegistry.categories.size} CATEGORIES  •  BRIDGE ${ (application as ZenithApp).container.gameBridge.status }"))
        val search = Ui.edit(this, "Search modules")
        search.setText(query)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); renderBody(body) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.addView(search)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        renderBody(body)
        root.addView(Ui.button(this, "CLOSE MOD MENU") { finish() })
        setContentView(Ui.scroll(this, root))
    }

    private fun renderBody(body: LinearLayout) {
        body.removeAllViews()
        val all = ModModuleRegistry.categories
        if (query.isNotBlank()) {
            body.addView(Ui.section(this, "SEARCH RESULTS"))
            ModModuleRegistry.search(query).forEach { (category, module) ->
                body.addView(moduleRow(module, category) { categoryId = category.id; moduleId = module.id; render() })
            }
            if (ModModuleRegistry.search(query).isEmpty()) body.addView(Ui.label(this, "No matching modules"))
            return
        }

        if (resources.configuration.screenWidthDp >= 760) {
            val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            columns.addView(categoryColumn(all), LinearLayout.LayoutParams(0, -1, 0.25f))
            columns.addView(moduleColumn(ModModuleRegistry.category(categoryId)), LinearLayout.LayoutParams(0, -1, 0.34f))
            columns.addView(detailColumn(ModModuleRegistry.module(moduleId)), LinearLayout.LayoutParams(0, -1, 0.41f))
            body.addView(columns, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            body.addView(Ui.section(this, "CATEGORY"))
            body.addView(horizontalItems(all.map { it.title }) { index -> categoryId = all[index].id; moduleId = all[index].modules.firstOrNull()?.id.orEmpty(); render() })
            val category = ModModuleRegistry.category(categoryId)
            body.addView(Ui.section(this, "${category?.title ?: "MODULES"}  /  SELECT A MODULE"))
            category?.modules.orEmpty().forEach { module -> body.addView(moduleRow(module, category) { moduleId = module.id; render() }) }
            val selected = ModModuleRegistry.module(moduleId)
            if (selected != null) {
                body.addView(Ui.section(this, "DETAILS"))
                body.addView(detailCard(selected))
            }
        }
    }

    private fun categoryColumn(categories: List<ModCategory>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.section(this@ModMenuActivity, "CATEGORIES"))
        categories.forEach { category ->
            addView(Ui.smallButton(this@ModMenuActivity, category.title) {
                categoryId = category.id; moduleId = category.modules.firstOrNull()?.id.orEmpty(); render()
            }, LinearLayout.LayoutParams(-1, Ui.dp(this@ModMenuActivity, 44)))
        }
    }

    private fun moduleColumn(category: ModCategory?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.section(this@ModMenuActivity, category?.title ?: "MODULES"))
        category?.modules.orEmpty().forEach { module -> addView(moduleRow(module, category) { moduleId = module.id; render() }) }
    }

    private fun detailColumn(module: ModModule?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.section(this@ModMenuActivity, "MODULE DETAILS"))
        if (module == null) addView(Ui.label(this@ModMenuActivity, "Select a module")) else addView(detailCard(module))
    }

    private fun moduleRow(module: ModModule, category: ModCategory?, selected: (() -> Unit)?) = Ui.card(
        this, "${if (module.id in favoriteIds) "★ " else ""}${module.icon}  ${module.title}",
        "${statusLabel(module.status)}  •  ${category?.title ?: "Module"}"
    ).apply { setOnClickListener { selected?.invoke() } }

    private fun detailCard(module: ModModule): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(this@ModMenuActivity, 16), Ui.dp(this@ModMenuActivity, 14), Ui.dp(this@ModMenuActivity, 16), Ui.dp(this@ModMenuActivity, 14))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Ui.SURFACE); cornerRadius = Ui.dp(this@ModMenuActivity, 14).toFloat(); setStroke(Ui.dp(this@ModMenuActivity, 1), Ui.BORDER) }
        addView(Ui.title(this@ModMenuActivity, module.title, 20f))
        addView(Ui.label(this@ModMenuActivity, module.description))
        addView(Ui.card(this@ModMenuActivity, "IMPLEMENTATION", statusLabel(module.status)))
        addView(Ui.label(this@ModMenuActivity, "Version ${module.version}"))
        if (module.requiredBridgeCapabilities.isNotEmpty()) addView(Ui.label(this@ModMenuActivity, "Requires: ${module.requiredBridgeCapabilities.joinToString()}"))
        if (module.permissions.isNotEmpty()) addView(Ui.label(this@ModMenuActivity, "Permission: ${module.permissions.joinToString()}"))
        addView(Ui.smallButton(this@ModMenuActivity, if (module.id in favoriteIds) "★ Remove favorite" else "☆ Add favorite") {
            if (!favoriteIds.add(module.id)) favoriteIds.remove(module.id)
            favorites.edit().putStringSet("ids", favoriteIds.toSet()).apply()
            render()
        })
        when (module.status) {
            CapabilityStatus.IMPLEMENTED -> when (module.id) {
                "library", "runner" -> addView(Ui.button(this@ModMenuActivity, "OPEN SCRIPT WORKSPACE") { startActivity(Intent(this@ModMenuActivity, EditorActivity::class.java)) })
                "api" -> addView(Ui.button(this@ModMenuActivity, "OPEN API EXPLORER") { startActivity(Intent(this@ModMenuActivity, ApiBrowserActivity::class.java)) })
                "diagnostics" -> addView(Ui.button(this@ModMenuActivity, "OPEN DIAGNOSTICS") { startActivity(Intent(this@ModMenuActivity, DiagnosticsActivity::class.java)) })
            }
            CapabilityStatus.PARTIAL, CapabilityStatus.INTERFACE_ONLY, CapabilityStatus.NOT_IMPLEMENTED, CapabilityStatus.UNAVAILABLE -> Unit
        }
    }

    private fun horizontalItems(labels: List<String>, select: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        labels.forEachIndexed { index, label -> addView(Ui.smallButton(this@ModMenuActivity, label) { select(index) }) }
    }

    private fun statusLabel(status: CapabilityStatus) = when (status) {
        CapabilityStatus.IMPLEMENTED -> "● Ready"
        CapabilityStatus.PARTIAL -> "◐ Partial"
        CapabilityStatus.INTERFACE_ONLY -> "○ Interface only"
        CapabilityStatus.NOT_IMPLEMENTED -> "× Not implemented"
        CapabilityStatus.UNAVAILABLE -> "× Unavailable"
    }
}
