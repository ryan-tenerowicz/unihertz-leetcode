package dev.titan.code.editor

/**
 * Word completion for `sym+Space` (PLAN §5.4, M5). Snippets were removed at the owner's request
 * (rev 13).
 *
 * Order: words already in the buffer, then Python keywords, builtins and the names LeetCode
 * pre-imports. At most [LIMIT] items.
 */
object Completer {
    data class Item(val label: String, val insert: String, val kind: Kind)

    enum class Kind { WORD, NAME }

    const val LIMIT = 8

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
}
