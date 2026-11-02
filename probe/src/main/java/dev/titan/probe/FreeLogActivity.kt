package dev.titan.probe

import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen live log of everything received, for exploring. Events are saved under test "free". */
class FreeLogActivity : ProbeActivity() {
    private lateinit var input: ProbeInputView
    private lateinit var logView: TextView
    private lateinit var modeButton: android.widget.Button
    private val lines = ArrayDeque<String>()
    private var overrideOn = false
    private var overrideButton: android.widget.Button? = null
    private val listener: (EventLog.Entry) -> Unit = { e ->
        if (e.testId == "free") {
            lines.addLast(e.line)
            while (lines.size > 300) lines.removeFirst()
            logView.text = lines.reversed().joinToString("\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        EventLog.currentTest = "free"
        val root = column()
        root.setBackgroundColor(0xFF000000.toInt())
        input = ProbeInputView(this).apply { consumeBack = false }
        modeButton = button("Mode: TYPE_NULL") { cycleMode() }
        logView = label("", 10f, mono = true, color = 0xFFBDBDBD.toInt())
        root.add(label("Free log — Back exits. Everything is also in logcat (tag TitanProbe).", 12f, color = 0xFF9AA0A6.toInt()))
        root.add(input)
        root.add(row(
            modeButton,
            button("Capture") { input.requestFocus(); input.requestPointerCapture() },
            button("Release") { input.releasePointerCapture() },
        ))
        root.add(row(
            button("Backlight override: off") { toggleOverride() }.also { overrideButton = it },
            button("Clear text") { input.clearText() },
            button("Clear log") { lines.clear(); logView.text = ""; EventLog.clear("free") },
        ))
        root.add(ScrollView(this).apply { isFocusable = false; addView(logView) }, 0, 1f)
        setContentView(root)
        EventLog.addListener(listener)
        input.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        EventLog.currentTest = "free"
        input.requestFocus()
    }

    override fun onDestroy() {
        EventLog.removeListener(listener)
        super.onDestroy()
    }

    private fun cycleMode() {
        val all = InputMode.entries
        input.mode = all[(all.indexOf(input.mode) + 1) % all.size]
        modeButton.text = "Mode: ${input.mode.label.substringBefore(" ")}"
        input.requestFocus()
    }

    private fun toggleOverride() {
        overrideOn = !overrideOn
        val lp = window.attributes
        lp.buttonBrightness = if (overrideOn) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        EventLog.note("backlight", "buttonBrightness override ${if (overrideOn) "FULL" else "NONE"}")
        overrideButton?.text = "Backlight override: ${if (overrideOn) "FULL" else "off"}"
    }
}
