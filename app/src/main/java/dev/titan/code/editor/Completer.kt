package dev.titan.code.editor

import org.json.JSONObject

/**
 * Completion for the Code pane (PLAN §5.4). Snippets were removed at the owner's request (rev 13).
 *
 * Rev 38: suggestions come from Jedi in the `:complete` process ([dev.titan.code.runner.CompletionClient]),
 * which infers types: `nums: List[int]` then `nums.` offers the list methods. [merge] puts
 * Jedi's names first and, for a plain name (not after a `.`), fills up with buffer words.
 * [suggest] is the fallback while Jedi is starting or if it fails: words in the buffer, then
 * Python keywords, builtins and LeetCode's pre-imported names.
 */
object Completer {
    /** [args]: for a function or class, how many arguments it takes (0 = none); null if unknown. */
    data class Item(val label: String, val insert: String, val kind: Kind, val args: Int? = null)

    enum class Kind { WORD, NAME, FUNCTION, CLASS, MODULE, KEYWORD, VALUE }

    /** Fallback list length; the strip scrolls, so Jedi's list can be longer. */
    const val LIMIT = 8
    const val JEDI_LIMIT = 60

    /** Keywords, builtins and LeetCode's pre-imported names (PLAN §7). */
    val NAMES: List<String> = (
        "False None True and as assert async await break class continue def del elif else except finally for from global if " +
            "import in is lambda nonlocal not or pass raise return try while with yield " +
            "abs all any bin bool chr dict divmod enumerate filter float frozenset getattr hasattr hash hex id input int isinstance " +
            "iter len list map max min next object oct ord pow print range repr reversed round set setattr slice sorted str sum " +
            "super tuple type zip self Solution ListNode TreeNode Optional List Dict Set Tuple " +
            "defaultdict Counter deque OrderedDict heappush heappop heapify heappushpop heapreplace nlargest nsmallest " +
            "bisect_left bisect_right insort accumulate combinations permutations product pairwise groupby chain " +
            "cache lru_cache reduce cmp_to_key gcd lcm sqrt isqrt ceil floor log2 comb factorial inf " +
            "ascii_lowercase ascii_uppercase digits SortedList SortedDict SortedSet"
        ).split(" ")

    fun suggest(prefix: String, bufferWords: Set<String>): List<Item> {
        if (prefix.length < 2) return emptyList()
        val out = LinkedHashMap<String, Item>()
        bufferWords.filter { it.startsWith(prefix) && it != prefix }.sortedWith(compareBy({ it.length }, { it }))
            .forEach { out.putIfAbsent(it, Item(it, it, Kind.WORD)) }
        NAMES.filter { it.startsWith(prefix) && it != prefix }.sortedBy { it.length }
            .forEach { out.putIfAbsent(it, Item(it, it, Kind.NAME)) }
        return out.values.take(LIMIT)
    }

    /** The request for `titan_complete.complete`: [line] and [col] as the editor counts them (0-based). */
    fun request(code: String, line: Int, col: Int): String =
        JSONObject().put("code", code).put("line", line + 1).put("col", col).put("limit", JEDI_LIMIT).toString()

    /** Jedi's names from a `titan_complete` result; null if it failed (use [suggest] then). */
    fun parse(json: String): List<Item>? = runCatching {
        val o = JSONObject(json)
        if (o.has("error")) return null
        val a = o.getJSONArray("items")
        List(a.length()) { i ->
            val it = a.getJSONObject(i)
            val name = it.getString("name")
            Item(name, name, kindOf(it.optString("type")), if (it.has("args")) it.getInt("args") else null)
        }
    }.getOrNull()

    private fun kindOf(type: String) = when (type) {
        "function" -> Kind.FUNCTION
        "class" -> Kind.CLASS
        "module" -> Kind.MODULE
        "keyword" -> Kind.KEYWORD
        else -> Kind.VALUE
    }

    /**
     * What accepting [item] inserts (rev 39): functions and classes get `()`, with the cursor
     * (`$0`) inside when they take arguments and after when they take none. Not where a bare name
     * is meant: a `(` already follows, a decorator, `def`/`class`/`import` lines, a function
     * passed as a value (`key=len`, `map(int, …)`), or a class in a type hint, `except`,
     * `isinstance` or a base-class list.
     */
    fun insertFor(item: Item, site: CompletionSite): String {
        val name = item.insert
        if (item.kind != Kind.FUNCTION && item.kind != Kind.CLASS) return name
        if (site.after.trimStart().startsWith("(")) return name
        val before = site.before
        val head = before.trimStart()
        if (head.startsWith("@") || NO_CALL_LINE.containsMatchIn(head)) return name
        if (FUNCTION_AS_VALUE.containsMatchIn(before)) return name
        if (item.kind == Kind.CLASS && (CLASS_AS_TYPE.containsMatchIn(before) || insideSquareBrackets(before))) return name
        return if (item.args == 0) "$name()\$0" else "$name(\$0)"
    }

    private val NO_CALL_LINE = Regex("^(def|class|import|from)\\b")
    private val FUNCTION_AS_VALUE = Regex("(\\bkey\\s*=\\s*|\\b(map|filter)\\(\\s*)$")
    private val CLASS_AS_TYPE = Regex("(->\\s*|:\\s*|\\bexcept\\s+\\(?\\s*|\\b(isinstance|issubclass)\\(.*,\\s*)$")

    /** An unclosed `[` before the cursor: `List[`, `Dict[str, `, `Optional[`. */
    private fun insideSquareBrackets(before: String): Boolean {
        var depth = 0
        for (c in before) { if (c == '[') depth++ else if (c == ']' && depth > 0) depth-- }
        return depth > 0
    }

    /**
     * Jedi's names, then (for a plain name of 2+ characters) buffer words it didn't offer, such as
     * words in comments. After a `.` only Jedi's names: they're the object's real attributes.
     */
    fun merge(jedi: List<Item>, site: CompletionSite, bufferWords: Set<String>): List<Item> {
        if (site.afterDot) return jedi
        if (site.prefix.length < 2) return emptyList()
        val out = LinkedHashMap<String, Item>()
        jedi.forEach { out.putIfAbsent(it.label, it) }
        bufferWords.filter { it.startsWith(site.prefix) && it != site.prefix }.sortedWith(compareBy({ it.length }, { it }))
            .forEach { out.putIfAbsent(it, Item(it, it, Kind.WORD)) }
        return out.values.take(JEDI_LIMIT)
    }
}
