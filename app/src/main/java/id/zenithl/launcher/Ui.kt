package id.zenithl.launcher

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

internal object Ui {
    const val BG = 0xFF07111F.toInt()
    const val PANEL = 0xFF101E30.toInt()
    const val BLUE = 0xFF52B6FF.toInt()
    const val WHITE = 0xFFEAF2FF.toInt()
    const val MUTED = 0xFF9BAAC0.toInt()

    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 18))
        setBackgroundColor(BG)
    }
    fun title(context: Context, value: String, size: Float = 25f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(WHITE); typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 8))
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
