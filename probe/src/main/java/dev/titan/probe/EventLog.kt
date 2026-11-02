package dev.titan.probe

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONObject

/**
 * In-memory log of everything the app receives. Every entry belongs to the test that was active
 * when it arrived (EventLog.currentTest), and is mirrored to logcat under the tag "TitanProbe".
 * Main thread only; use [noteAsync] from other threads.
 */
object EventLog {
    const val TAG = "TitanProbe"

    class Entry(
        val seq: Long,
        val testId: String,
        val kind: String,   // key | motion | ic | note
        val stage: String,  // key: preIme/activity/view; motion: touch/generic/captured/trackball; ic: method; note: category
        val json: JSONObject,
        val line: String,
    )

    private val main = Handler(Looper.getMainLooper())
    private var seq = 0L
    val entries = ArrayList<Entry>()
    var currentTest: String = "free"
    private val listeners = ArrayList<(Entry) -> Unit>()

    fun addListener(l: (Entry) -> Unit) { listeners.add(l) }
    fun removeListener(l: (Entry) -> Unit) { listeners.remove(l) }

    fun clear(testId: String) { entries.removeAll { it.testId == testId } }
    fun forTest(testId: String): List<Entry> = entries.filter { it.testId == testId }

    private fun add(kind: String, stage: String, json: JSONObject, line: String) {
        seq += 1
        json.put("seq", seq).put("test", currentTest).put("kind", kind).put("stage", stage)
            .put("uptimeMs", SystemClock.uptimeMillis())
        val e = Entry(seq, currentTest, kind, stage, json, line)
        entries.add(e)
        if (entries.size > 80_000) entries.subList(0, 20_000).clear()
        Log.d(TAG, "[$currentTest] $line")
        for (l in listeners.toList()) l(e)
    }

    fun key(stage: String, ev: KeyEvent) {
        val ch = Names.uni(ev.getUnicodeChar(ev.metaState))
        val j = JSONObject()
            .put("action", Names.keyAction(ev.action))
            .put("keyCode", ev.keyCode)
            .put("key", Names.key(ev.keyCode))
            .put("scanCode", ev.scanCode)
            .put("meta", ev.metaState)
            .put("metaNames", Names.meta(ev.metaState))
            .put("repeat", ev.repeatCount)
            .put("char", ch)
            .put("charNoMeta", Names.uni(ev.getUnicodeChar(0)))
            .put("label", ev.displayLabel.let { if (it.code == 0) "" else it.toString() })
            .put("source", ev.source)
            .put("sourceNames", Names.source(ev.source))
            .put("deviceId", ev.deviceId)
            .put("device", Names.device(ev.deviceId))
            .put("eventTime", ev.eventTime)
            .put("downTime", ev.downTime)
            .put("flags", ev.flags)
        @Suppress("DEPRECATION")
        val chars = ev.characters
        if (chars != null) j.put("characters", chars)
        val line = "%-8s KEY %-4s %-14s sc=%-4d meta=%-10s ch='%s' rep=%d dev=%s".format(
            stage, Names.keyAction(ev.action), Names.key(ev.keyCode), ev.scanCode,
            Names.meta(ev.metaState), Names.printable(ch), ev.repeatCount, Names.device(ev.deviceId),
        )
        add("key", stage, j, line)
    }

    private fun fin(v: Float): Double = if (v.isFinite()) v.toDouble() else 0.0

    private fun axisSum(ev: MotionEvent, axis: Int): Double {
        var s = 0.0
        for (i in 0 until ev.historySize) s += fin(ev.getHistoricalAxisValue(axis, i))
        return s + fin(ev.getAxisValue(axis))
    }

    fun motion(stage: String, ev: MotionEvent) {
        val j = JSONObject()
            .put("action", MotionEvent.actionToString(ev.action))
            .put("source", ev.source)
            .put("sourceNames", Names.source(ev.source))
            .put("deviceId", ev.deviceId)
            .put("device", Names.device(ev.deviceId))
            .put("tool", Names.tool(ev.getToolType(0)))
            .put("pointers", ev.pointerCount)
            .put("x", fin(ev.x)).put("y", fin(ev.y))
            .put("rx", fin(ev.getAxisValue(MotionEvent.AXIS_RELATIVE_X)))
            .put("ry", fin(ev.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)))
            .put("rxSum", axisSum(ev, MotionEvent.AXIS_RELATIVE_X))
            .put("rySum", axisSum(ev, MotionEvent.AXIS_RELATIVE_Y))
            .put("vscroll", fin(ev.getAxisValue(MotionEvent.AXIS_VSCROLL)))
            .put("hscroll", fin(ev.getAxisValue(MotionEvent.AXIS_HSCROLL)))
            .put("gestureX", fin(ev.getAxisValue(MotionEvent.AXIS_GESTURE_X_OFFSET)))
            .put("gestureY", fin(ev.getAxisValue(MotionEvent.AXIS_GESTURE_Y_OFFSET)))
            .put("gestureScrollX", fin(ev.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE)))
            .put("gestureScrollY", fin(ev.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE)))
            .put("history", ev.historySize)
            .put("buttons", ev.buttonState)
            .put("meta", ev.metaState)
            .put("metaNames", Names.meta(ev.metaState))
            .put("classification", Names.classification(ev.classification))
            .put("eventTime", ev.eventTime)
        val line = "%-8s MOTION %-22s src=%s tool=%s x=%.0f y=%.0f rel=(%.1f,%.1f) scroll=(%.1f,%.1f) meta=%s cls=%s dev=%s".format(
            stage, MotionEvent.actionToString(ev.action), Names.source(ev.source), Names.tool(ev.getToolType(0)),
            fin(ev.x), fin(ev.y), j.getDouble("rxSum"), j.getDouble("rySum"),
            fin(ev.getAxisValue(MotionEvent.AXIS_HSCROLL)), fin(ev.getAxisValue(MotionEvent.AXIS_VSCROLL)),
            Names.meta(ev.metaState), Names.classification(ev.classification), Names.device(ev.deviceId),
        )
        add("motion", stage, j, line)
    }

    /** A call from the IME into our InputConnection. */
    fun ic(method: String, detail: String) {
        add("ic", method, JSONObject().put("method", method).put("detail", detail), "IME→IC   $method $detail")
    }

    fun note(category: String, msg: String) {
        add("note", category, JSONObject().put("category", category).put("msg", msg), "NOTE     [$category] $msg")
    }

    fun noteAsync(category: String, msg: String) {
        main.post { note(category, msg) }
    }
}
