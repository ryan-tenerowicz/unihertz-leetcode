package dev.titan.code.samples

import android.content.res.AssetManager
import org.json.JSONArray

/** A bundled M1 sample problem (PLAN §12): statement, starter snippet and reference solution. */
data class Sample(
    val slug: String,
    val number: Int,
    val title: String,
    val difficulty: String,
    val statementHtml: String,
    val starter: String,
    val reference: String,
) {
    val label get() = "$number. $title"
}

/** Loads `assets/samples/` once. M2 replaces this with the Room-backed problem store. */
class SampleLibrary(assets: AssetManager) {
    val all: List<Sample>

    init {
        fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }
        val index = JSONArray(read("samples/index.json"))
        all = (0 until index.length()).map { i ->
            val o = index.getJSONObject(i)
            val slug = o.getString("slug")
            Sample(
                slug = slug,
                number = o.getInt("number"),
                title = o.getString("title"),
                difficulty = o.getString("difficulty"),
                statementHtml = read("samples/$slug/statement.html"),
                starter = read("samples/$slug/starter.py"),
                reference = read("samples/$slug/reference.py"),
            )
        }
    }

    fun bySlug(slug: String?): Sample = all.firstOrNull { it.slug == slug } ?: all.first()
}
