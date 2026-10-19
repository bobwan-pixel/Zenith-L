package id.zenithl.launcher

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import id.zenithl.launcher.api.ApiDefinition
import id.zenithl.launcher.api.ApiRegistry
import id.zenithl.launcher.ui.Ui

class ApiBrowserActivity : Activity() {
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.page(this)
        root.addView(Ui.title(this, "API REFERENCE"))
        root.addView(Ui.label(this, "API ${ApiRegistry.API_VERSION}  •  GL reference ${ApiRegistry.GL_COMPATIBILITY_VERSION}"))
        root.addView(EditText(this).apply {
            hint = "Search API names"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        })
        root.addView(Ui.button(this, "BACK") { finish() })
        setContentView(Ui.scroll(this, root))
        render("")
    }

    private fun render(query: String) {
        while (root.childCount > 4) root.removeViewAt(4)
        ApiRegistry.definitions.filter { it.name.contains(query, true) }.forEach { definition -> root.addView(apiCard(definition)) }
    }

    private fun apiCard(api: ApiDefinition) = TextView(this).apply {
        text = "${api.name}\n${api.status} • ${api.compatibility}\n${api.description}\nParameters: ${api.parameters.joinToString { "${it.name}: ${it.type}" }.ifBlank { "none" }}\nReturns: ${api.returnType}\nExample: ${api.example}"
        textSize = 14f; setTextColor(Ui.WHITE); setPadding(12, 12, 12, 12)
        setBackgroundColor(Ui.PANEL)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@ApiBrowserActivity, 10) }
    }
}
