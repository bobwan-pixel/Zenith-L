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

/**
 * Lightweight visual system for Zenith'L.
 * Built with platform views so the launcher stays fast on older Android devices.
 */
internal object Ui {
    const val BG = 0xFF080D18.toInt()
    const val PANEL = 0xFF111A2B.toInt()
    const val BLUE = 0xFF8B83FF.toInt()
    const val WHITE = 0xFFF2F4FF.toInt()
    const val MUTED = 0xFF929DB4.toInt()
    const val SURFACE = 0xFF151F33.toInt()
    const val BORDER = 0xFF26334B.toInt()

    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 24))
        setBackgroundColor(BG)
    }

    fun title(context: Context, value: String, size: Float = 25f) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(WHITE)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLetterSpacing(-0.025f)
        setPadding(0, 0, 0, dp(context, 7))
    }

    fun eyebrow(context: Context, value: String) = TextView(context).apply {
        text = value.uppercase()
        textSize = 10f
        setLetterSpacing(0.14f)
        setTextColor(0xFF63D8D0.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 8))
    }

    fun section(context: Context, value: String) = TextView(context).apply {
        text = value.uppercase()
        textSize = 11f
        setLetterSpacing(0.12f)
        setTextColor(MUTED)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(0, dp(context, 22), 0, dp(context, 11))
    }

    fun card(context: Context, heading: String, detail: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15))
        setBackground(rounded(context, SURFACE, BORDER, 18))
        addView(TextView(context).apply {
            text = heading
            textSize = 15f
            setLetterSpacing(-0.01f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(WHITE)
        })
        addView(TextView(context).apply {
            text = detail
            textSize = 12.5f
            lineSpacingMultiplier = 1.12f
            setTextColor(MUTED)
            setPadding(0, dp(context, 6), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
    }

    fun heroCard(context: Context, heading: String, detail: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 18), dp(context, 19), dp(context, 18), dp(context, 18))
        setBackground(GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF20264A.toInt(), 0xFF142C3B.toInt(), 0xFF121C30.toInt())
        )).apply {
            cornerRadius = dp(context, 22).toFloat()
            setStroke(dp(context, 1), 0xFF39446A.toInt())
        }
        addView(eyebrow(context, "ZENITH'L • SCRIPTING ENVIRONMENT"))
        addView(TextView(context).apply {
            text = heading
            textSize = 24f
            setLetterSpacing(-0.035f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
        })
        addView(TextView(context).apply {
            text = detail
            textSize = 13f
            lineSpacingMultiplier = 1.15f
            setTextColor(0xFFB6C3D8.toInt())
            setPadding(0, dp(context, 7), 0, dp(context, 2))
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) }
    }

    fun metric(context: Context, value: String, caption: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 14), dp(context, 14), dp(context, 12), dp(context, 13))
        setBackground(rounded(context, PANEL, BORDER, 16))
        addView(TextView(context).apply {
            text = value
            textSize = 22f
            setLetterSpacing(-0.03f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
        })
        addView(TextView(context).apply {
            text = caption.uppercase()
            textSize = 9.5f
            setLetterSpacing(0.09f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MUTED)
            setPadding(0, dp(context, 4), 0, 0)
        })
    }

    fun smallButton(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value
        isAllCaps = false
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(WHITE)
        setBackground(rounded(context, PANEL, BORDER, 12))
        minHeight = dp(context, 42)
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        setOnClickListener { action() }
    }

    fun label(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 13f
        lineSpacingMultiplier = 1.15f
        setTextColor(MUTED)
        setPadding(0, dp(context, 3), 0, dp(context, 12))
    }

    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value
        isAllCaps = false
        textSize = 13f
        setLetterSpacing(0.015f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(0xFF0B1020.toInt())
        setBackground(GradientDrawable().apply {
            setColor(BLUE)
            cornerRadius = dp(context, 14).toFloat()
        })
        minHeight = dp(context, 50)
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        stateListAnimator = null
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 50)).apply { bottomMargin = dp(context, 10) }
    }

    fun edit(context: Context, hintText: String, multi: Boolean = false) = EditText(context).apply {
        hint = hintText
        setHintTextColor(MUTED)
        setTextColor(WHITE)
        textSize = 14f
        setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        setBackground(rounded(context, PANEL, BORDER, 13))
        if (multi) {
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            maxLines = 100
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            textSize = 13f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layoutParams = LinearLayout.LayoutParams(-1, if (multi) 0 else dp(context, 50)).apply {
            if (multi) weight = 1f else bottomMargin = dp(context, 10)
        }
    }

    fun navItem(context: Context, value: String, selected: Boolean, action: () -> Unit): TextView = TextView(context).apply {
        text = value
        textSize = 11f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (selected) BLUE else MUTED)
        setPadding(dp(context, 2), dp(context, 15), dp(context, 2), dp(context, 15))
        setOnClickListener { action() }
    }

    fun scroll(context: Context, child: View) = ScrollView(context).apply {
        isFillViewport = true
        clipToPadding = false
        addView(child)
    }

    fun rounded(context: Context, color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radius).toFloat()
        setStroke(dp(context, 1), stroke)
    }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
