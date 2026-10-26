package app.medicinely

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Colours from the Medicinely design. */
object C {
    val BG = 0xFFF5F6F3.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val INK = 0xFF1E2B2F.toInt()
    val MUTED = 0xFF55636A.toInt()
    val BORDER = 0xFFDDE2DF.toInt()
    val TEAL = 0xFF2E6B66.toInt()
    val TEAL_DARK = 0xFF1F4F4B.toInt()
    val TEAL_TINT = 0xFFE3EFED.toInt()
    val TEAL_LINE = 0xFFB9D6D1.toInt()
    val AMBER = 0xFF7A4B00.toInt()
    val AMBER_BG = 0xFFFBEFD9.toInt()
    val AMBER_LINE = 0xFFE8C98F.toInt()
    val NIGHT = 0xFF1F2647.toInt()
    val NIGHT_INK = 0xFF2F3A73.toInt()
    val NIGHT_TINT = 0xFFE9EBF7.toInt()
    val NIGHT_TEXT = 0xFFEEF0FF.toInt()
    val NIGHT_SOFT = 0xFFC9CEF2.toInt()
    val NIGHT_BTN = 0xFF2A3260.toInt()
    val NIGHT_LINE = 0xFF4A5590.toInt()
    val RED = 0xFFA1302A.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.label(s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

fun Context.rounded(fill: Int, stroke: Int = 0, radiusDp: Int = 16): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

fun Context.bigButton(
    s: String, fill: Int, fg: Int, stroke: Int = 0,
    sp: Float = 20f, bold: Boolean = false, onClick: () -> Unit
): Button =
    Button(this).apply {
        text = s
        isAllCaps = false
        textSize = sp
        setTextColor(fg)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        background = rounded(fill, stroke, 16)
        stateListAnimator = null
        setPadding(dp(16), 0, dp(16), 0)
        setOnClickListener { onClick() }
    }

/** Adds a child: in a column it fills the width with [gapDp] above; in a row it shares the width (weight) with [gapDp] before. */
fun LinearLayout.add(v: View, gapDp: Int = 0, heightDp: Int = 0, weight: Float = 1f): View {
    val h = if (heightDp > 0) context.dp(heightDp) else LinearLayout.LayoutParams.WRAP_CONTENT
    val lp: LinearLayout.LayoutParams
    if (orientation == LinearLayout.VERTICAL) {
        lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        lp.topMargin = context.dp(gapDp)
    } else {
        lp = if (weight > 0f) LinearLayout.LayoutParams(0, h, weight)
             else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h)
        lp.marginStart = context.dp(gapDp)
    }
    addView(v, lp)
    return v
}

fun Context.column(padDp: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
}

fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card(fill: Int, stroke: Int = 0, padDp: Int = 20): LinearLayout =
    column(padDp).apply { background = rounded(fill, stroke, 20) }
