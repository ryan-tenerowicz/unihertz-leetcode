package dev.titan.code.data

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * Curated problem lists mapped to LeetCode problems (PLAN §6.3, M6: NeetCode starts as lists).
 * A dated snapshot is bundled in `assets/lists/neetcode.json`, so the lists work offline.
 */
data class ProblemCollection(val id: String, val title: String, val slugs: List<String>, val patterns: Map<String, String>)

object Collections {
    fun load(assets: AssetManager): List<ProblemCollection> = parse(assets.open("lists/neetcode.json").bufferedReader().use { it.readText() })

    fun parse(json: String): List<ProblemCollection> {
        val lists = JSONObject(json).getJSONArray("lists")
        return (0 until lists.length()).map { i ->
            val o = lists.getJSONObject(i)
            val items = o.getJSONArray("items")
            val pairs = (0 until items.length()).map { j -> items.getJSONArray(j).let { it.getString(0) to it.getString(1) } }
            ProblemCollection(o.getString("id"), o.getString("title"), pairs.map { it.first }, pairs.toMap())
        }
    }
}
