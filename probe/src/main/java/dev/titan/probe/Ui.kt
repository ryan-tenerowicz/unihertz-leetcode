package dev.titan.probe

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.label(
    s: CharSequence,
    size: Float = 14f,
    bold: Boolean = false,
    mono: Boolean = false,
    color: Int = 0xFFE6E6E6.toInt(),
): TextView = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
    typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

/** Buttons are touch-only: never focusable, so the keyboard always stays on the field under test. */
fun Context.button(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    isAllCaps = false
    isFocusable = false
    isFocusableInTouchMode = false
    textSize = 13f
    minHeight = dp(40)
    minimumHeight = dp(40)
    setOnClickListener { onClick() }
}

fun Context.row(vararg views: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    for (v in views) addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
}

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(8), dp(6), dp(8), dp(6))
}

fun LinearLayout.add(v: View, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT, weight: Float = 0f): View {
    addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height, weight))
    return v
}
