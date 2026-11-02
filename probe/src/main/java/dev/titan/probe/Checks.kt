package dev.titan.probe

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.json.JSONArray

/** Automatic checks (AUTO line) and the generic per-test summary. */
object Checks {
    private val LETTERS = KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
    private val DPAD = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
    )
    private val SHIFT = setOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT)
    private val ALT = setOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT)
    private val SYM = setOf(KeyEvent.KEYCODE_SYM)

    // ---- helpers ----------------------------------------------------------------------------

    private fun EventLog.Entry.i(n: String): Int = json.optInt(n)
    private fun EventLog.Entry.s(n: String): String = json.optString(n)
    private fun EventLog.Entry.isDown(): Boolean = kind == "key" && s("action") == "DOWN"
    private fun EventLog.Entry.physical(): Boolean = i("deviceId") != KeyCharacterMap.VIRTUAL_KEYBOARD

    /** Key DOWN events, any stage. */
    private fun RunCtx.downs() = events.filter { it.isDown() }

    /** One entry per physical key press (first stage that saw it; repeats excluded). */
    private fun RunCtx.presses(): List<EventLog.Entry> =
        downs().filter { it.i("repeat") == 0 }
            .distinctBy { "${it.json.optLong("eventTime")}-${it.i("keyCode")}" }

    private fun RunCtx.notesText(filter: (EventLog.Entry) -> Boolean = { true }): List<String> =
        events.filter { it.kind == "note" && filter(it) }.map { "[${it.stage}] ${it.s("msg")}" }

    private fun RunCtx.focusLost(): Boolean =
        events.any { it.kind == "note" && it.stage == "focus" && it.s("msg").contains("LOST") }

    /** For every entry, whether one of [mods] was physically held at that moment. */
    private fun RunCtx.heldMap(mods: Set<Int>): Map<Long, Boolean> {
        val seen = HashSet<String>()
        var held = false
        val out = HashMap<Long, Boolean>()
        for (e in events) {
            if (e.kind == "key" && e.i("keyCode") in mods) {
                val id = "${e.json.optLong("eventTime")}-${e.i("keyCode")}-${e.s("action")}"
                if (seen.add(id)) {
                    if (e.s("action") == "DOWN") held = true
                    if (e.s("action") == "UP") held = false
                }
            }
            out[e.seq] = held
        }
        return out
    }

    private fun letters(codes: Collection<Int>): String =
        codes.joinToString(" ") { Names.key(it) }

    // ---- key tests --------------------------------------------------------------------------

    fun k01(c: RunCtx): Auto {
        val seen = c.downs().map { it.i("keyCode") }.toSet()
        val missing = LETTERS.filter { it !in seen }
        return when {
            missing.isEmpty() -> Auto("PASS", "all 26 letter keys arrived")
            seen.isEmpty() -> Auto("INFO", "waiting for input")
            else -> Auto("FAIL", "missing: ${letters(missing)}")
        }
    }

    fun k02(c: RunCtx): Auto {
        val ups = c.buffer.filter { it in 'A'..'Z' }.toSet()
        val missing = ('A'..'Z').filter { it !in ups }
        val held = c.heldMap(SHIFT)
        val shifted = c.presses().filter { it.i("keyCode") in LETTERS && (it.i("meta") and KeyEvent.META_SHIFT_ON) != 0 }
        val whileHeld = shifted.count { held[it.seq] == true }
        val afterRelease = shifted.size - whileHeld
        val shiftPresses = c.presses().count { it.i("keyCode") in SHIFT }
        val info = "capitals typed ${ups.size}/26; Shift presses $shiftPresses; " +
            "shifted letters while Shift held: $whileHeld, after Shift released (one-shot/locked): $afterRelease"
        return if (missing.isEmpty()) Auto("PASS", info) else Auto("INFO", "$info; missing: ${missing.joinToString("")}")
    }

    fun k03(c: RunCtx): Auto {
        val alt = c.downs().filter { (it.i("meta") and KeyEvent.META_ALT_ON) != 0 && it.i("keyCode") !in ALT }
        val map = LinkedHashMap<String, MutableSet<String>>()
        for (e in alt) map.getOrPut(e.s("key")) { linkedSetOf() }.add(e.s("char").ifEmpty { "∅" })
        val commits = c.events.filter { it.kind == "ic" && it.stage == "commitText" }
        val pairs = map.entries.joinToString("  ") { "${it.key}→${it.value.joinToString("/") { v -> Names.printable(v) }}" }
        val noChar = map.count { it.value == setOf("∅") }
        return Auto(
            "INFO",
            "${map.size} keys pressed with Alt; $noChar gave no character from the key map; " +
                "IME commits: ${commits.size}\n$pairs",
        )
    }

    fun k04(c: RunCtx): Auto {
        val held = c.heldMap(ALT)
        val altLetters = c.presses().filter { it.i("keyCode") in LETTERS && (it.i("meta") and KeyEvent.META_ALT_ON) != 0 }
        val notHeld = altLetters.count { held[it.seq] != true }
        val altPresses = c.presses().count { it.i("keyCode") in ALT }
        return Auto(
            "INFO",
            "Alt presses $altPresses; letters with ALT meta ${altLetters.size}, of which $notHeld while Alt was NOT held " +
                "(>0 means the system has one-shot/locking Alt)",
        )
    }

    fun k05(c: RunCtx): Auto {
        val sym = c.events.filter { it.kind == "key" && it.i("keyCode") == KeyEvent.KEYCODE_SYM }
        if (sym.isEmpty()) {
            return if (c.focusLost()) Auto("FAIL", "sym never reached the app; window focus was lost (a picker/popup opened?)")
            else Auto("INFO", "no sym events yet")
        }
        val stages = sym.map { it.stage }.distinct().joinToString(",")
        return Auto(
            if (c.focusLost()) "INFO" else "PASS",
            "sym reached the app (stages: $stages); window focus lost: ${if (c.focusLost()) "YES" else "no"}",
        )
    }

    fun k06(c: RunCtx): Auto {
        val symLetters = c.downs().filter { it.i("keyCode") in LETTERS && (it.i("meta") and KeyEvent.META_SYM_ON) != 0 }
        val seen = symLetters.map { it.i("keyCode") }.toSet()
        val missing = LETTERS.filter { it !in seen }
        val held = c.heldMap(SYM)
        val heldNoMeta = c.presses().count {
            it.i("keyCode") in LETTERS && held[it.seq] == true && (it.i("meta") and KeyEvent.META_SYM_ON) == 0
        }
        val viewReached = symLetters.filter { it.stage == "view" }.map { it.i("keyCode") }.toSet().size
        val info = "letters with SYM meta: ${seen.size}/26 (reached the view un-consumed: $viewReached); " +
            "letters pressed while sym held but WITHOUT sym meta: $heldNoMeta"
        return when {
            missing.isEmpty() -> Auto("PASS", info)
            seen.isEmpty() && heldNoMeta == 0 -> Auto("INFO", "waiting for input")
            else -> Auto("FAIL", "$info; missing: ${letters(missing)}")
        }
    }

    fun k07(c: RunCtx): Auto {
        val both = KeyEvent.META_SYM_ON or KeyEvent.META_SHIFT_ON
        val hits = c.downs().filter { it.i("keyCode") in LETTERS && (it.i("meta") and both) == both }
        val anyLetter = c.presses().any { it.i("keyCode") in LETTERS }
        return when {
            hits.isNotEmpty() -> Auto("PASS", "sym+Shift+letter arrived: ${hits.map { it.s("key") }.distinct().joinToString(" ")}")
            anyLetter -> Auto("FAIL", "letters arrived but never with both SYM and SHIFT meta")
            else -> Auto("INFO", "waiting for input")
        }
    }

    fun k08(c: RunCtx): Auto {
        val special = setOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL)
        val p = c.presses().filter { it.i("keyCode") in special }
        val combos = p.map { "${it.s("key")}${if (it.s("metaNames").isNotEmpty()) "+" + it.s("metaNames") else ""}" }.distinct()
        return Auto("INFO", "combinations seen: ${combos.joinToString(", ")}")
    }

    fun k09(c: RunCtx): Auto {
        val keys = c.presses().map { it.s("key") }.distinct()
        return Auto("INFO", "distinct keys: ${keys.joinToString(" ")}")
    }

    fun k10(c: RunCtx): Auto {
        val letterDowns = c.downs().filter { it.i("keyCode") in LETTERS }
        val maxRep = letterDowns.maxOfOrNull { it.i("repeat") } ?: 0
        val longPress = letterDowns.any { (it.json.optInt("flags") and KeyEvent.FLAG_LONG_PRESS) != 0 }
        return Auto(
            "INFO",
            "max repeatCount $maxRep; FLAG_LONG_PRESS seen: $longPress; window focus lost: ${if (c.focusLost()) "YES (a shortcut fired?)" else "no"}; " +
                "inserted text: '${Names.printable(c.buffer.take(60))}'",
        )
    }

    fun k12(c: RunCtx): Auto {
        val p = c.events.filter { it.kind == "key" && it.i("keyCode") !in LETTERS }
        if (p.isEmpty()) {
            return Auto(if (c.focusLost()) "FAIL" else "INFO",
                if (c.focusLost()) "no key event; focus lost (system action?)" else "waiting for input")
        }
        val groups = p.groupBy { it.s("key") }.entries.joinToString("; ") { (k, l) ->
            "$k sc=${l.first().i("scanCode")} stages=${l.map { it.stage }.distinct().joinToString(",")}"
        }
        return Auto("INFO", "$groups; focus lost: ${if (c.focusLost()) "YES" else "no"}")
    }

    fun k13(c: RunCtx): Auto {
        val target = "for i in range(len(nums)):"
        val lines = c.buffer.split("\n").filter { it.isNotEmpty() }
        val ok = lines.count { it == target }
        val bad = lines.filter { it != target }
        return when {
            lines.isEmpty() -> Auto("INFO", "waiting for input")
            ok == 5 && bad.isEmpty() -> Auto("PASS", "5/5 lines exact")
            else -> Auto(if (lines.size >= 5) "FAIL" else "INFO",
                "$ok exact so far; differing: ${bad.joinToString(" | ") { "'$it'" }}")
        }
    }

    fun k14(c: RunCtx): Auto {
        val expected = "teh i  ok"
        val b = c.buffer
        val corrections = c.events.count { it.kind == "ic" && (it.stage == "commitCorrection" || it.stage == "setComposingText") }
        return when {
            b.isEmpty() -> Auto("INFO", "waiting for input")
            b == expected -> Auto("PASS", "text exactly as typed; composing/correction calls: $corrections")
            b.length >= expected.length -> Auto("FAIL", "got '${Names.printable(b)}' expected '${Names.printable(expected)}'; composing/correction calls: $corrections")
            else -> Auto("INFO", "so far '${Names.printable(b)}'")
        }
    }

    fun k15(c: RunCtx): Auto {
        val firstStage = c.presses().filter { it.physical() }
        if (firstStage.isEmpty()) return Auto("INFO", "waiting for input")
        val notPre = firstStage.filter { it.stage != "preIme" }
        val viewMissing = firstStage.count { p ->
            c.events.none { it.isDown() && it.stage == "view" && it.json.optLong("eventTime") == p.json.optLong("eventTime") && it.i("keyCode") == p.i("keyCode") }
        }
        val info = "${firstStage.size} presses; first seen at preIme: ${firstStage.size - notPre.size}; " +
            "never reached the view (consumed by IME/system): $viewMissing"
        return if (notPre.isEmpty()) Auto("PASS", info) else Auto("FAIL", "$info; first stage elsewhere for: ${notPre.joinToString(" ") { "${it.s("key")}@${it.stage}" }}")
    }

    fun k16(c: RunCtx): Auto {
        val arr = c.extra.optJSONArray("symbols") ?: JSONArray()
        val total = Catalogue.SYMBOLS.size
        val rows = (0 until arr.length()).map { arr.getJSONObject(it) }
        val missing = rows.filter { !it.optBoolean("found") }.map { it.optString("sym") }
        val found = rows.filter { it.optBoolean("found") }
        val avg = if (found.isEmpty()) 0.0 else found.sumOf { it.optInt("keystrokes") } / found.size.toDouble()
        val over2 = found.filter { it.optInt("keystrokes") > 2 }.map { "${it.optString("sym")}(${it.optInt("keystrokes")})" }
        val table = rows.joinToString("  ") { r ->
            if (r.optBoolean("found")) "${r.optString("sym")}=${r.optInt("keystrokes")}" else "${r.optString("sym")}=✗"
        }
        val info = "${rows.size}/$total done; avg %.1f keys; >2 keys: %s; missing: %s\n%s".format(
            avg, over2.joinToString(" ").ifEmpty { "none" }, missing.joinToString(" ").ifEmpty { "none" }, table,
        )
        return when {
            rows.size < total -> Auto("INFO", info)
            missing.isEmpty() -> Auto("PASS", info)
            else -> Auto("FAIL", info)
        }
    }

    // ---- gesture tests ----------------------------------------------------------------------

    private fun RunCtx.movement(): Map<String, List<EventLog.Entry>> {
        val dpad = downs().filter { it.i("keyCode") in DPAD }
        val generic = events.filter { it.kind == "motion" && it.stage == "generic" }
        val captured = events.filter { it.kind == "motion" && it.stage == "captured" }
        val touch = events.filter { it.kind == "motion" && it.stage == "touch" }
        return linkedMapOf("dpad" to dpad, "generic" to generic, "captured" to captured, "touch" to touch)
    }

    private fun describeMovement(m: Map<String, List<EventLog.Entry>>): String {
        val dpad = m["dpad"].orEmpty().groupingBy { it.s("key") }.eachCount()
        val g = m["generic"].orEmpty()
        val vs = g.sumOf { it.json.optDouble("vscroll", 0.0) }
        val hs = g.sumOf { it.json.optDouble("hscroll", 0.0) }
        val cap = m["captured"].orEmpty()
        val crx = cap.sumOf { it.json.optDouble("rxSum", 0.0) }
        val cry = cap.sumOf { it.json.optDouble("rySum", 0.0) }
        val touchDevices = m["touch"].orEmpty().map { it.s("device") }.distinct()
        return "DPAD ${if (dpad.isEmpty()) "none" else dpad.entries.joinToString(" ") { "${it.key}×${it.value}" }}; " +
            "generic ${g.size} (Σh %.1f Σv %.1f); captured ${cap.size} (Σrel %.1f, %.1f); touch ${m["touch"].orEmpty().size} from %s"
                .format(hs, vs, crx, cry, touchDevices.joinToString(",").ifEmpty { "-" })
    }

    fun gesture(c: RunCtx): Auto = Auto("INFO", describeMovement(c.movement()))

    private fun heldGesture(c: RunCtx, mods: Set<Int>, name: String): Auto {
        val held = c.heldMap(mods)
        val all = c.movement()
        val whileHeld = all.mapValues { (_, l) -> l.filter { held[it.seq] == true } }
        val strong = whileHeld["dpad"].orEmpty().size + whileHeld["generic"].orEmpty().size + whileHeld["captured"].orEmpty().size
        val touch = whileHeld["touch"].orEmpty().size
        val modPressed = c.presses().any { it.i("keyCode") in mods }
        val info = "while $name held → ${describeMovement(whileHeld)}"
        return when {
            !modPressed -> Auto("INFO", "waiting for $name to be held; overall: ${describeMovement(all)}")
            strong > 0 -> Auto("PASS", info)
            touch > 0 -> Auto("INFO", "$info (only touch-type events — check which device)")
            else -> Auto("FAIL", info)
        }
    }

    fun g05(c: RunCtx) = heldGesture(c, SHIFT, "Shift")
    fun g06(c: RunCtx) = heldGesture(c, ALT, "Alt")
    fun g07(c: RunCtx) = heldGesture(c, SYM, "sym")

    fun notes(c: RunCtx): Auto {
        val n = c.notesText { it.stage != "mode" && it.stage != "ic" }
        return Auto("INFO", if (n.isEmpty()) "no notes yet" else n.takeLast(12).joinToString("\n"))
    }

    // ---- generic summary --------------------------------------------------------------------

    fun summary(c: RunCtx): String {
        val sb = StringBuilder()
        val downs = c.downs()
        if (downs.isNotEmpty()) {
            sb.append("KEY DOWNS\n")
            val groups = LinkedHashMap<String, MutableList<EventLog.Entry>>()
            for (e in downs) {
                val meta = e.s("metaNames")
                val id = "${e.s("key")}${if (meta.isNotEmpty()) " [$meta]" else ""} '${Names.printable(e.s("char"))}'"
                groups.getOrPut(id) { mutableListOf() }.add(e)
            }
            for ((id, l) in groups.entries.take(40)) {
                val presses = l.filter { it.i("repeat") == 0 }.groupingBy { it.stage }.eachCount().values.maxOrNull() ?: 0
                val reps = l.count { it.i("repeat") > 0 }
                val stages = l.map { it.stage }.distinct().joinToString(",")
                sb.append("  $id ×$presses stages=$stages${if (reps > 0) " repeats=$reps" else ""}\n")
            }
            if (groups.size > 40) sb.append("  … ${groups.size - 40} more\n")
        }
        val ic = c.events.filter { it.kind == "ic" }
        if (ic.isNotEmpty()) {
            sb.append("IME → INPUTCONNECTION\n")
            for ((m, l) in ic.groupBy { it.stage }) sb.append("  $m ×${l.size}  last: ${l.last().s("detail").take(50)}\n")
        }
        val mo = c.events.filter { it.kind == "motion" }
        if (mo.isNotEmpty()) {
            sb.append("MOTION\n")
            for ((g, l) in mo.groupBy { "${it.stage} · ${it.s("device")} · ${it.s("sourceNames")}" }) {
                val acts = l.groupingBy { it.s("action").removePrefix("ACTION_") }.eachCount().entries.joinToString(" ") { "${it.key}×${it.value}" }
                val rx = l.sumOf { it.json.optDouble("rxSum", 0.0) }
                val ry = l.sumOf { it.json.optDouble("rySum", 0.0) }
                val hs = l.sumOf { it.json.optDouble("hscroll", 0.0) }
                val vs = l.sumOf { it.json.optDouble("vscroll", 0.0) }
                val xs = l.map { it.json.optDouble("x", 0.0) }
                val ys = l.map { it.json.optDouble("y", 0.0) }
                val metas = l.map { it.s("metaNames") }.filter { it.isNotEmpty() }.distinct()
                sb.append("  $g\n    $acts\n")
                sb.append("    Σrel=(%.1f, %.1f) Σscroll=(h %.1f, v %.1f) x=%.0f..%.0f y=%.0f..%.0f%s\n".format(
                    rx, ry, hs, vs, xs.minOrNull() ?: 0.0, xs.maxOrNull() ?: 0.0, ys.minOrNull() ?: 0.0, ys.maxOrNull() ?: 0.0,
                    if (metas.isEmpty()) "" else " meta=" + metas.joinToString(","),
                ))
            }
        }
        val notes = c.notesText()
        if (notes.isNotEmpty()) {
            sb.append("NOTES\n")
            for (n in notes.takeLast(10)) sb.append("  $n\n")
        }
        sb.append("BUFFER '${Names.printable(c.buffer.take(200))}'\n")
        return sb.toString()
    }
}
