package dev.titan.probe

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : ProbeActivity() {
    override val logInput: Boolean get() = false

    private lateinit var status: TextView
    private lateinit var resume: android.widget.Button

    private val MODES = arrayOf(
        "Scroll Assistant", "Cursor Assistant", "Mouse mode", "Flick typing",
        "Per-letter shortcuts", "Universal shortcuts",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.setBackgroundColor(0xFF000000.toInt())
        root.add(label("Titan Probe", 22f, bold = true))
        root.add(label("Phase 0 keyboard & gesture tests (PLAN.md §9)", 13f, color = 0xFF9AA0A6.toInt()))
        status = label("", 13f, mono = true)
        root.add(status)

        resume = button("Guided run") { startActivity(Intent(this, GuidedActivity::class.java)) }
        root.add(resume)
        root.add(button("Restart guided run from the beginning") {
            AlertDialog.Builder(this)
                .setMessage("Delete all saved results and start again?")
                .setPositiveButton("Delete & restart") { _, _ ->
                    Store.resetAll(this)
                    startActivity(Intent(this, GuidedActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        root.add(button("Free log (explore any input)") { startActivity(Intent(this, FreeLogActivity::class.java)) })
        root.add(button("Keyboard modes currently ON…") { declareModes() })
        root.add(button("Show key character map") { showKeyMap() })
        root.add(row(
            button("Save report") { saveReport(share = false) },
            button("Share report") { saveReport(share = true) },
        ))
        root.add(label(
            "\nReports go to Download/titan-probe/. On the Mac: scripts/pull-report.sh\n" +
                "Live log on the Mac: scripts/logcat.sh",
            12f, color = 0xFF9AA0A6.toInt(),
        ))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val steps = Catalogue.steps()
        val tests = steps.filterIsInstance<TestStep>()
        val statuses = tests.mapNotNull { Store.status(this, it.key) }
        val counts = statuses.groupingBy { it }.eachCount()
        val i = Store.index(this).coerceIn(0, steps.size - 1)
        val kb = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }.map { it.name }
        status.text = buildString {
            append("Done ${statuses.size}/${tests.size} tests")
            if (counts.isNotEmpty()) append("  (" + counts.entries.joinToString(" ") { "${it.key} ${it.value}" } + ")")
            append("\nNext: ${steps[i].key}")
            append("\nKeyboards: ${kb.joinToString(", ").ifEmpty { "none found" }}")
            append("\nModes ON: ${Store.declaredModes(this@MainActivity).joinToString(", ").ifEmpty { "(not set — tap below)" }}")
        }
        resume.text = if (statuses.isEmpty()) "▶ Start guided run" else "▶ Resume guided run (${steps[i].key})"
    }

    private fun declareModes() {
        val current = Store.declaredModes(this)
        val checked = MODES.map { it in current }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Which Unihertz keyboard features are ON right now?")
            .setMultiChoiceItems(MODES, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Save") { _, _ ->
                Store.setDeclaredModes(this, MODES.filterIndexed { i, _ -> checked[i] }.toSet())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKeyMap() {
        val sb = StringBuilder()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (d.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC) continue
            sb.append("${d.name} (#$id)\nkey  plain shift alt sym\n")
            val rows = Report.keyMap(d.keyCharacterMap)
            for (k in 0 until rows.length()) {
                val r = rows.getJSONObject(k)
                sb.append("%-12s %-5s %-5s %-3s %s\n".format(
                    r.optString("key"), Names.printable(r.optString("plain")), Names.printable(r.optString("shift")),
                    Names.printable(r.optString("alt")), Names.printable(r.optString("sym")),
                ))
            }
            sb.append("\n")
        }
        val tv = label(sb.ifEmpty { "No alphabetic keyboard found." }, 12f, mono = true).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        AlertDialog.Builder(this)
            .setTitle("KeyCharacterMap (what apps get from getUnicodeChar)")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun saveReport(share: Boolean) {
        Toast.makeText(this, "Saving report…", Toast.LENGTH_SHORT).show()
        Report.save(this) { uris, err ->
            if (err != null) {
                Toast.makeText(this, "Save failed: $err", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Saved ${uris.size} files to Download/titan-probe/", Toast.LENGTH_LONG).show()
                if (share) Report.share(this, uris)
            }
        }
    }
}
