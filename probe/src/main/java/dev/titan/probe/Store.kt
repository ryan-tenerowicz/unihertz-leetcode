package dev.titan.probe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists guided-run progress and each test's result (with its raw events) in app storage. */
object Store {
    private const val MAX_EVENTS = 25_000

    private fun prefs(c: Context) = c.getSharedPreferences("probe", Context.MODE_PRIVATE)

    fun index(c: Context): Int = prefs(c).getInt("index", 0)
    fun setIndex(c: Context, i: Int) { prefs(c).edit().putInt("index", i).apply() }

    fun declaredModes(c: Context): Set<String> = prefs(c).getStringSet("modes", emptySet()) ?: emptySet()
    fun setDeclaredModes(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("modes", s).apply() }

    private fun dir(c: Context): File = File(c.filesDir, "results").apply { mkdirs() }

    private fun file(c: Context, key: String): File =
        File(dir(c), key.replace("@", "_at_").replace("/", "_").replace("-", "_") + ".json")

    fun saveResult(
        c: Context, step: TestStep, status: String, notes: String,
        auto: Auto?, summary: String, ctx: RunCtx,
    ) {
        val events = JSONArray()
        val list = if (ctx.events.size > MAX_EVENTS) ctx.events.takeLast(MAX_EVENTS) else ctx.events
        for (e in list) events.put(e.json)
        val j = JSONObject()
            .put("key", step.key)
            .put("id", step.def.id)
            .put("variant", step.variant)
            .put("variantLabel", step.variantLabel)
            .put("title", step.def.title)
            .put("status", status)
            .put("notes", notes)
            .put("auto", if (auto == null) JSONObject.NULL else JSONObject().put("status", auto.status).put("text", auto.text))
            .put("summary", summary)
            .put("buffer", ctx.buffer)
            .put("extra", ctx.extra)
            .put("declaredModes", JSONArray(declaredModes(c).toList()))
            .put("savedAt", System.currentTimeMillis())
            .put("eventCount", ctx.events.size)
            .put("eventsTruncated", ctx.events.size > MAX_EVENTS)
            .put("events", events)
        file(c, step.key).writeText(j.toString())
    }

    fun load(c: Context, key: String): JSONObject? {
        val f = file(c, key)
        if (!f.exists()) return null
        return try { JSONObject(f.readText()) } catch (e: Exception) { null }
    }

    fun status(c: Context, key: String): String? = load(c, key)?.optString("status")

    fun resetAll(c: Context) {
        dir(c).listFiles()?.forEach { it.delete() }
        setIndex(c, 0)
    }
}
