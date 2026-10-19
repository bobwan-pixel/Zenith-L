package id.zenithl.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.FrameLayout

internal object Ui {
    const val BG = 0xFF07111F.toInt()
    const val PANEL = 0xFF101E30.toInt()
    const val BLUE = 0xFF52B6FF.toInt()
    const val WHITE = 0xFFEAF2FF.toInt()
    const val MUTED = 0xFF9BAAC0.toInt()
    const val SURFACE = 0xFF14243A.toInt()
    const val BORDER = 0xFF223853.toInt()

    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 18))
        setBackgroundColor(BG)
    }
    fun title(context: Context, value: String, size: Float = 25f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(WHITE); typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 8))
    }
    fun section(context: Context, value: String) = TextView(context).apply {
        text = value; textSize = 12f; setTextColor(BLUE); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(context, 18), 0, dp(context, 8))
    }
    fun card(context: Context, heading: String, detail: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 12))
        background = GradientDrawable().apply { setColor(SURFACE); cornerRadius = dp(context, 14).toFloat(); setStroke(dp(context, 1), BORDER) }
        addView(TextView(context).apply { text = heading; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WHITE) })
        addView(TextView(context).apply { text = detail; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(context, 5), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
    }
    fun smallButton(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 12f; setTextColor(WHITE)
        background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(context, 10).toFloat(); setStroke(dp(context, 1), BORDER) }
        setOnClickListener { action() }
    }
    fun label(context: Context, value: String) = TextView(context).apply {
        text = value; textSize = 14f; setTextColor(MUTED); setPadding(0, dp(context, 3), 0, dp(context, 12))
    }
    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; setTextColor(WHITE); setBackgroundColor(PANEL)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 48)).apply { bottomMargin = dp(context, 9) }
    }
    fun edit(context: Context, hintText: String, multi: Boolean = false) = EditText(context).apply {
        hint = hintText; setHintTextColor(MUTED); setTextColor(WHITE); textSize = 14f
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(context, 8).toFloat() }
        if (multi) { gravity = Gravity.TOP or Gravity.START; minLines = 12; maxLines = 100; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        layoutParams = LinearLayout.LayoutParams(-1, if (multi) 0 else dp(context, 48)).apply { if (multi) weight = 1f else bottomMargin = dp(context, 10) }
    }
    fun scroll(context: Context, child: View) = ScrollView(context).apply { addView(child) }
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
