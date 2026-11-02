package dev.titan.probe

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Builds the report (JSON with raw events + Markdown summary) and writes it to
 * Download/titan-probe/ (MediaStore) and to app storage (files/reports/, readable via run-as).
 */
object Report {
    const val JSON_NAME = "titan-probe-report.json"
    const val MD_NAME = "titan-probe-report.md"
    private val bg = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val METAS = listOf(
        "plain" to 0,
        "shift" to KeyEvent.META_SHIFT_ON,
        "alt" to KeyEvent.META_ALT_ON,
        "sym" to KeyEvent.META_SYM_ON,
        "alt+shift" to (KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON),
        "sym+shift" to (KeyEvent.META_SYM_ON or KeyEvent.META_SHIFT_ON),
    )

    private val MAP_KEYS: List<Int> = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() +
        (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toList() + listOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
        KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH,
        KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_AT,
        KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_PLUS,
    )

    private val PROBE_KEYS = intArrayOf(
        KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_MINUS,
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LANGUAGE_SWITCH,
    )

    // ---- device information -----------------------------------------------------------------

    fun deviceInfo(c: Context): JSONObject {
        val j = JSONObject()
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE)
            .put("product", Build.PRODUCT).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("build", Build.DISPLAY).put("fingerprint", Build.FINGERPRINT)
        val dm = c.resources.displayMetrics
        j.put("screenPx", "${dm.widthPixels}x${dm.heightPixels}").put("densityDpi", dm.densityDpi)
            .put("density", dm.density.toDouble())
        j.put("configuration", c.resources.configuration.toString())
        try {
            j.put("defaultIme", Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
        } catch (e: Exception) {
            j.put("defaultIme", "unreadable: ${e.javaClass.simpleName}")
        }
        try {
            val imm = c.getSystemService(InputMethodManager::class.java)
            j.put("enabledImes", JSONArray(imm.enabledInputMethodList.map { it.id }))
        } catch (e: Exception) {
            j.put("enabledImes", "unreadable: ${e.javaClass.simpleName}")
        }
        j.put("declaredKeyboardModes", JSONArray(Store.declaredModes(c).toList()))
        @Suppress("DEPRECATION")
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        j.put("appVersion", "${pi.versionName} (${pi.longVersionCode})")
        j.put("generatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
        return j
    }

    fun keyMap(k: KeyCharacterMap): JSONArray {
        val rows = JSONArray()
        for (code in MAP_KEYS) {
            val r = JSONObject().put("key", Names.key(code))
            val label = k.getDisplayLabel(code)
            r.put("label", if (label.code == 0) "" else label.toString())
            for ((name, meta) in METAS) r.put(name, Names.uni(k.get(code, meta)))
            rows.put(r)
        }
        return rows
    }

    fun inputDevices(): JSONArray {
        val arr = JSONArray()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val j = JSONObject()
                .put("id", id).put("name", d.name).put("descriptor", d.descriptor)
                .put("sources", Names.source(d.sources)).put("sourcesRaw", d.sources)
                .put("keyboardType", when (d.keyboardType) {
                    InputDevice.KEYBOARD_TYPE_ALPHABETIC -> "alphabetic"
                    InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC -> "non-alphabetic"
                    else -> "none"
                })
                .put("virtual", d.isVirtual).put("external", d.isExternal)
                .put("vendorId", d.vendorId).put("productId", d.productId)
            val ranges = JSONArray()
            for (r in d.motionRanges) {
                ranges.put(JSONObject().put("axis", MotionEvent.axisToString(r.axis)).put("source", Names.source(r.source))
                    .put("min", r.min.toDouble()).put("max", r.max.toDouble()).put("resolution", r.resolution.toDouble()))
            }
            j.put("motionRanges", ranges)
            val has = d.hasKeys(*PROBE_KEYS)
            val hk = JSONObject()
            PROBE_KEYS.forEachIndexed { i, k -> hk.put(Names.key(k), has[i]) }
            j.put("hasKeys", hk)
            if (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) j.put("keyMap", keyMap(d.keyCharacterMap))
            arr.put(j)
        }
        return arr
    }

    // ---- building ---------------------------------------------------------------------------

    fun buildJson(c: Context, freeLog: List<JSONObject>): JSONObject {
        val results = JSONArray()
        for (s in Catalogue.steps()) {
            if (s !is TestStep) continue
            val r = Store.load(c, s.key) ?: JSONObject().put("key", s.key).put("id", s.def.id)
                .put("variant", s.variant).put("variantLabel", s.variantLabel).put("title", s.def.title).put("status", "NOT RUN")
            results.put(r)
        }
        return JSONObject()
            .put("schema", 1)
            .put("device", deviceInfo(c))
            .put("inputDevices", inputDevices())
            .put("results", results)
            .put("freeLog", JSONArray(freeLog))
    }

    private fun cell(s: String): String = when (s) {
        "" -> "·"
        "|" -> "`\\|`"
        "`" -> "`` ` ``"
        " " -> "␣"
        "\n" -> "⏎"
        else -> "`$s`"
    }

    fun buildMarkdown(json: JSONObject): String {
        val sb = StringBuilder()
        val d = json.getJSONObject("device")
        sb.append("# Titan Probe report\n\n")
        sb.append("| | |\n|---|---|\n")
        for (k in listOf("model", "manufacturer", "android", "sdk", "build", "screenPx", "densityDpi", "defaultIme", "appVersion", "generatedAt")) {
            sb.append("| $k | ${d.opt(k)} |\n")
        }
        sb.append("| declared keyboard modes | ${d.optJSONArray("declaredKeyboardModes")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } } ?: ""} |\n")
        sb.append("| enabled IMEs | ${d.opt("enabledImes")} |\n\n")

        // Results table
        val results = json.getJSONArray("results")
        val rows = (0 until results.length()).map { results.getJSONObject(it) }
        val counts = rows.groupingBy { it.optString("status") }.eachCount()
        sb.append("## Results\n\n")
        sb.append(counts.entries.joinToString(" · ") { "${it.key}: ${it.value}" }).append("\n\n")
        sb.append("| Test | Variant | Result | Auto | Notes |\n|---|---|---|---|---|\n")
        for (r in rows) {
            val auto = r.optJSONObject("auto")
            val autoS = if (auto == null) "" else auto.optString("status") + " — " + auto.optString("text").lineSequence().first().take(90)
            sb.append("| ${r.optString("id")} ${r.optString("title")} | ${r.optString("variant")} | **${r.optString("status")}** | ")
            sb.append(autoS.replace("|", "\\|")).append(" | ").append(r.optString("notes").replace("|", "\\|").replace("\n", " ")).append(" |\n")
        }

        // Input devices + key maps
        sb.append("\n## Input devices\n\n| id | name | sources | keyboard | axes |\n|---|---|---|---|---|\n")
        val devs = json.getJSONArray("inputDevices")
        for (i in 0 until devs.length()) {
            val dv = devs.getJSONObject(i)
            val mr = dv.getJSONArray("motionRanges")
            val axes = (0 until mr.length()).map { mr.getJSONObject(it).optString("axis").removePrefix("AXIS_") }.distinct().joinToString(" ")
            sb.append("| ${dv.optInt("id")} | ${dv.optString("name")} | ${dv.optString("sources")} | ${dv.optString("keyboardType")} | $axes |\n")
        }
        for (i in 0 until devs.length()) {
            val dv = devs.getJSONObject(i)
            val km = dv.optJSONArray("keyMap") ?: continue
            sb.append("\n### Key character map: ${dv.optString("name")} (#${dv.optInt("id")})\n\n")
            val hk = dv.optJSONObject("hasKeys")
            if (hk != null) {
                val present = hk.keys().asSequence().filter { hk.optBoolean(it) }.toList()
                sb.append("Has keys: ${present.joinToString(" ")}\n\n")
            }
            sb.append("| key | label | plain | shift | alt | sym | alt+shift | sym+shift |\n|---|---|---|---|---|---|---|---|\n")
            for (k in 0 until km.length()) {
                val r = km.getJSONObject(k)
                sb.append("| ${r.optString("key")} | ${cell(r.optString("label"))} |")
                for ((name, _) in METAS) sb.append(" ${cell(r.optString(name))} |")
                sb.append("\n")
            }
        }

        // Symbols (K16)
        for (r in rows.filter { it.optString("id") == "K16" }) {
            val syms = r.optJSONObject("extra")?.optJSONArray("symbols") ?: continue
            sb.append("\n## K16 symbols — ${r.optString("variantLabel")}\n\n| symbol | keystrokes | found | wrong chars first |\n|---|---|---|---|\n")
            for (k in 0 until syms.length()) {
                val s = syms.getJSONObject(k)
                val wrong = s.optJSONArray("wrong")?.let { a -> (0 until a.length()).joinToString(" ") { a.getString(it) } } ?: ""
                sb.append("| ${cell(s.optString("sym"))} | ${s.optInt("keystrokes")} | ${if (s.optBoolean("found")) "yes" else "**no**"} | ${wrong.replace("|", "\\|")} |\n")
            }
        }

        // Per-test details
        sb.append("\n## Details\n")
        for (r in rows) {
            if (r.optString("status") == "NOT RUN") continue
            sb.append("\n### ${r.optString("key")} — ${r.optString("title")} — ${r.optString("status")}\n\n")
            if (r.optString("variantLabel").isNotEmpty()) sb.append("${r.optString("variantLabel")}\n\n")
            r.optJSONObject("auto")?.let { sb.append("Auto: ${it.optString("status")} — ${it.optString("text")}\n\n") }
            if (r.optString("notes").isNotEmpty()) sb.append("Notes: ${r.optString("notes")}\n\n")
            sb.append("```\n").append(r.optString("summary")).append("```\n")
        }
        return sb.toString()
    }

    // ---- writing ----------------------------------------------------------------------------

    private fun writeDownload(c: Context, name: String, mime: String, data: ByteArray): Uri? {
        val cr = c.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val rel = Environment.DIRECTORY_DOWNLOADS + "/titan-probe/"
        var uri: Uri? = null
        cr.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, rel), null,
        )?.use { cur -> if (cur.moveToFirst()) uri = ContentUris.withAppendedId(collection, cur.getLong(0)) }
        if (uri == null) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            uri = cr.insert(collection, v)
        }
        val u = uri ?: return null
        cr.openOutputStream(u, "wt")?.use { it.write(data) }
        return u
    }

    /** Builds and saves the report off the main thread; [done] runs on the main thread. */
    fun save(c: Context, done: (List<Uri>, String?) -> Unit) {
        val app = c.applicationContext
        val freeLog = EventLog.forTest("free").takeLast(3000).map { it.json }
        bg.execute {
            try {
                val json = buildJson(app, freeLog)
                val md = buildMarkdown(json)
                val jsonBytes = json.toString(1).toByteArray()
                val mdBytes = md.toByteArray()
                val dir = File(app.filesDir, "reports").apply { mkdirs() }
                File(dir, JSON_NAME).writeBytes(jsonBytes)
                File(dir, MD_NAME).writeBytes(mdBytes)
                val uris = listOfNotNull(
                    writeDownload(app, JSON_NAME, "application/json", jsonBytes),
                    writeDownload(app, MD_NAME, "text/markdown", mdBytes),
                )
                main.post { done(uris, null) }
            } catch (e: Exception) {
                EventLog.noteAsync("report", "save failed: $e")
                main.post { done(emptyList(), e.toString()) }
            }
        }
    }

    fun share(a: Activity, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        a.startActivity(Intent.createChooser(i, "Send probe report"))
    }
}
