package dev.titan.probe

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** One test per screen, in the order of PLAN.md §9.3. Results are marked on the touchscreen. */
class GuidedActivity : ProbeActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var steps: List<Step>
    private var index = 0
    private var pendingNotes = ""

    private lateinit var header: TextView
    private lateinit var instruction: TextView
    private lateinit var learn: TextView
    private lateinit var input: ProbeInputView
    private lateinit var special: LinearLayout
    private lateinit var autoView: TextView
    private lateinit var logView: TextView
    private lateinit var resultRow: LinearLayout
    private val logLines = ArrayDeque<String>()
    private var updatePending = false

    // K16 symbol state
    private var symIdx = 0
    private var symKeys = 0
    private var symData = JSONArray()
    private val symWrong = mutableListOf<String>()
    private var symTarget: TextView? = null

    // B01/B03 timer
    private var overrideOn = false
    private var overrideSince = 0L
    private var timerView: TextView? = null
    private val tick = object : Runnable {
        override fun run() {
            timerView?.text = if (overrideOn) "Override ON for ${(SystemClock.uptimeMillis() - overrideSince) / 1000}s" else "Override OFF"
            handler.postDelayed(this, 1000)
        }
    }

    // B02 settings snapshot
    private var snapshot: Map<String, String> = emptyMap()

    private val listener: (EventLog.Entry) -> Unit = { e -> onEntry(e) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        steps = Catalogue.steps()
        index = Store.index(this).coerceIn(0, steps.size - 1)
        buildUi()
        EventLog.addListener(listener)
        show()
    }

    override fun onDestroy() {
        EventLog.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPause() {
        Store.setIndex(this, index)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::input.isInitialized && steps[index] is TestStep) input.requestFocus()
    }

    // ---- layout -----------------------------------------------------------------------------

    private fun buildUi() {
        val root = column()
        root.setBackgroundColor(0xFF000000.toInt())
        header = label("", 15f, bold = true)
        instruction = label("", 14f)
        learn = label("", 12f, color = 0xFF9AA0A6.toInt())
        input = ProbeInputView(this)
        special = column().apply { setPadding(0, 0, 0, 0) }
        autoView = label("", 11f, mono = true, color = 0xFFB5E2B5.toInt())
        logView = label("", 10f, mono = true, color = 0xFFBDBDBD.toInt())

        val scrollBody = column().apply { setPadding(0, 0, 0, 0) }
        scrollBody.add(autoView)
        scrollBody.add(label("── live log ──", 10f, color = 0xFF777777.toInt()))
        scrollBody.add(logView)
        val scroll = ScrollView(this).apply { isFocusable = false; addView(scrollBody) }

        resultRow = row(
            button("✓ Pass") { mark("PASS") },
            button("✗ Fail") { mark("FAIL") },
            button("? Unclear") { mark("UNCLEAR") },
            button("Notes") { editNotes() },
        )
        val nav = row(
            button("◀ Back") { go(index - 1) },
            button("Skip ▶") { go(index + 1) },
            button("Reset") { resetCurrent() },
            button("Exit") { Report.save(this) { _, _ -> }; finish() },
        )

        root.add(header)
        root.add(instruction)
        root.add(learn)
        root.add(input)
        root.add(special)
        root.add(scroll, 0, 1f)
        root.add(resultRow)
        root.add(nav)
        setContentView(root)
    }

    // ---- step display -----------------------------------------------------------------------

    private fun go(i: Int) {
        if (i < 0 || i >= steps.size) return
        leaveStep()
        index = i
        Store.setIndex(this, index)
        show()
    }

    private fun leaveStep() {
        if (input.hasPointerCapture()) input.releasePointerCapture()
        setOverride(false)
        input.onInsert = null
        symTarget = null
        timerView = null
        handler.removeCallbacks(tick)
    }

    private fun show() {
        val step = steps[index]
        EventLog.currentTest = step.key
        logLines.clear()
        logView.text = ""
        autoView.text = ""
        special.removeAllViews()
        pendingNotes = ""
        val progress = "[${index + 1}/${steps.size}]"

        when (step) {
            is SetupStep -> {
                header.text = "$progress ${step.title}"
                instruction.text = step.body
                learn.text = ""
                input.visibility = View.GONE
                resultRow.visibility = View.GONE
                special.add(button("Continue ▶") { go(index + 1) })
            }
            is TestStep -> {
                EventLog.clear(step.key)
                val prev = Store.status(this, step.key)
                header.text = "$progress ${step.def.id} · ${step.def.title}" + (if (prev != null) "   (was $prev)" else "")
                instruction.text = step.def.instruction + if (step.variantLabel.isNotEmpty()) "\n\n▸ ${step.variantLabel}" else ""
                learn.text = "We learn: ${step.def.learn}"
                input.visibility = View.VISIBLE
                resultRow.visibility = View.VISIBLE
                input.mode = step.inputMode
                input.clearText()
                input.requestFocus()
                setupSpecial(step)
                scheduleUpdate()
            }
        }
    }

    private fun resetCurrent() {
        val step = steps[index] as? TestStep ?: return
        leaveStep()
        EventLog.clear(step.key)
        show()
    }

    private fun setupSpecial(step: TestStep) {
        when (step.def.special) {
            Special.NONE -> {}
            Special.SYMBOLS -> {
                symIdx = 0; symKeys = 0; symData = JSONArray(); symWrong.clear()
                val t = label("", 40f, bold = true, mono = true).apply { gravity = Gravity.CENTER }
                symTarget = t
                special.add(t)
                special.add(button("Can't find it") { symbolSkip() })
                input.onInsert = { s -> symbolInsert(s) }
                updateSymbol()
            }
            Special.CAPTURE -> {
                special.add(row(
                    button("Request pointer capture") { input.requestFocus(); input.requestPointerCapture() },
                    button("Release") { input.releasePointerCapture() },
                ))
                handler.postDelayed({ if (steps[index] === step) { input.requestFocus(); input.requestPointerCapture() } }, 400)
            }
            Special.BACKLIGHT_WINDOW -> {
                val t = label("", 16f, bold = true)
                timerView = t
                special.add(t)
                special.add(row(button("Override ON") { setOverride(true) }, button("Override OFF") { setOverride(false) }))
                setOverride(true)
                handler.post(tick)
            }
            Special.BACKLIGHT_SETTING -> setupSettingsControls()
            Special.NETWORK -> {
                special.add(button("Open LeetCode (login + checks)") {
                    startActivity(Intent(this, LoginActivity::class.java))
                })
            }
        }
    }

    // ---- K16 --------------------------------------------------------------------------------

    private fun updateSymbol() {
        val t = symTarget ?: return
        t.text = if (symIdx < Catalogue.SYMBOLS.size)
            "${Catalogue.SYMBOLS[symIdx]}   (${symIdx + 1}/${Catalogue.SYMBOLS.size})"
        else "Done ✓"
    }

    private fun symbolRecord(found: Boolean) {
        symData.put(JSONObject()
            .put("sym", Catalogue.SYMBOLS[symIdx])
            .put("found", found)
            .put("keystrokes", symKeys)
            .put("wrong", JSONArray(symWrong.toList())))
        symIdx++
        symKeys = 0
        symWrong.clear()
        updateSymbol()
        scheduleUpdate()
    }

    private fun symbolInsert(s: String) {
        for (ch in s) {
            if (symIdx >= Catalogue.SYMBOLS.size) return
            if (ch.toString() == Catalogue.SYMBOLS[symIdx]) symbolRecord(true) else symWrong.add(ch.toString())
        }
    }

    private fun symbolSkip() {
        if (symIdx < Catalogue.SYMBOLS.size) symbolRecord(false)
        input.requestFocus()
    }

    // ---- backlight --------------------------------------------------------------------------

    private fun setOverride(on: Boolean) {
        val lp = window.attributes
        lp.buttonBrightness = if (on) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        if (on != overrideOn) EventLog.note("backlight", "buttonBrightness override ${if (on) "FULL" else "NONE"}")
        overrideOn = on
        overrideSince = SystemClock.uptimeMillis()
    }

    private fun takeSnapshot(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (table in listOf("system", "secure", "global")) {
            try {
                contentResolver.query(Uri.parse("content://settings/$table"), arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) out["$table/${c.getString(0)}"] = c.getString(1) ?: "null"
                }
            } catch (e: Exception) {
                EventLog.note("settings", "query $table failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return out
    }

    private fun setupSettingsControls() {
        val keyField = EditText(this).apply { hint = "setting key (system table)"; inputType = InputType.TYPE_CLASS_TEXT; textSize = 13f }
        val valueField = EditText(this).apply { hint = "value"; inputType = InputType.TYPE_CLASS_NUMBER; textSize = 13f; setText("1") }
        special.add(row(
            button("Snapshot") {
                snapshot = takeSnapshot()
                EventLog.note("settings", "snapshot: ${snapshot.size} keys readable")
            },
            button("Diff") {
                val now = takeSnapshot()
                val changed = (now.keys + snapshot.keys).filter { now[it] != snapshot[it] }
                EventLog.note("settings", if (changed.isEmpty()) "diff: no changes" else "diff: " + changed.joinToString("; ") { "$it: ${snapshot[it]} → ${now[it]}" })
                changed.firstOrNull { it.startsWith("system/") }?.let { keyField.setText(it.removePrefix("system/")) }
                snapshot = now
            },
            button("Grant write") {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            },
        ))
        special.add(row(keyField, valueField))
        special.add(row(
            button("Write") {
                val k = keyField.text.toString().trim()
                val v = valueField.text.toString().trim().toIntOrNull() ?: 0
                try {
                    val ok = Settings.System.putInt(contentResolver, k, v)
                    EventLog.note("settings", "write system/$k=$v -> $ok (canWrite=${Settings.System.canWrite(this)})")
                } catch (e: Exception) {
                    EventLog.note("settings", "write system/$k failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            },
            button("Read") {
                val k = keyField.text.toString().trim()
                try {
                    EventLog.note("settings", "read system/$k = ${Settings.System.getString(contentResolver, k)}")
                } catch (e: Exception) {
                    EventLog.note("settings", "read system/$k failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            },
        ))
    }

    // ---- events / auto ----------------------------------------------------------------------

    private fun onEntry(e: EventLog.Entry) {
        val step = steps.getOrNull(index) ?: return
        if (e.testId != step.key) return
        if (step is TestStep && step.def.special == Special.SYMBOLS && e.kind == "key" && e.stage == "preIme" &&
            e.json.optString("action") == "DOWN" && e.json.optInt("repeat") == 0 && e.json.optInt("deviceId") != -1
        ) symKeys++
        logLines.addLast(e.line)
        while (logLines.size > 30) logLines.removeFirst()
        logView.text = logLines.reversed().joinToString("\n")
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        if (updatePending) return
        updatePending = true
        handler.postDelayed({ updatePending = false; updateAuto() }, 300)
    }

    private fun extra(): JSONObject = JSONObject().put("symbols", symData)

    private fun currentCtx(step: TestStep): RunCtx =
        RunCtx(EventLog.forTest(step.key), input.editable.toString(), if (step.def.special == Special.SYMBOLS) extra() else JSONObject())

    private fun updateAuto() {
        val step = steps.getOrNull(index) as? TestStep ?: return
        val ctx = currentCtx(step)
        val auto = step.def.check?.invoke(ctx)
        autoView.text = (if (auto != null) "AUTO: ${auto.status} — ${auto.text}\n\n" else "") + Checks.summary(ctx)
    }

    // ---- marking ----------------------------------------------------------------------------

    private fun mark(status: String) {
        val step = steps[index] as? TestStep ?: return
        val ctx = currentCtx(step)
        val auto = step.def.check?.invoke(ctx)
        Store.saveResult(this, step, status, pendingNotes, auto, Checks.summary(ctx), ctx)
        Toast.makeText(this, "${step.key}: $status", Toast.LENGTH_SHORT).show()
        if (index + 1 < steps.size) go(index + 1)
        if (index == steps.size - 1) Report.save(this) { _, _ -> }
    }

    private fun editNotes() {
        val f = EditText(this).apply { setText(pendingNotes); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        AlertDialog.Builder(this)
            .setTitle("Notes for ${steps[index].key}")
            .setView(f)
            .setPositiveButton("Save") { _, _ -> pendingNotes = f.text.toString(); input.requestFocus() }
            .setNegativeButton("Cancel") { _, _ -> input.requestFocus() }
            .show()
    }
}
