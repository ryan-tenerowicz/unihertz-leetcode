package dev.titan.code.data

/**
 * The problem list's filter (PLAN §4.3). Typed text matches like this:
 * - a number: problems whose number starts with it (`49` → 49, 490…; Codeforces `1850a` → 1850A);
 * - a word starting with `#`: a tag slug containing it (`#hash` → hash-table);
 * - other words: each must appear in the title, any order, ignoring case.
 *
 * The toggles (difficulty, status, saved) narrow the result further.
 */
data class ProblemFilter(
    val text: String = "",
    val difficulty: String? = null,
    /** "todo", "tried" or "solved"; null for any. */
    val status: String? = null,
    val savedOnly: Boolean = false,
    /** A curated list (M6): only its problems, in its order. */
    val collection: ProblemCollection? = null,
) {
    fun apply(all: List<ProblemSummary>, saved: Set<String>): List<ProblemSummary> {
        val base = collection?.let { c ->
            val bySlug = all.associateBy { it.slug }
            c.slugs.mapNotNull { bySlug[it] }
        } ?: all
        val words = text.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        val tags = words.filter { it.startsWith("#") }.map { it.drop(1) }.filter(String::isNotEmpty)
        val number = words.singleOrNull()?.takeIf { w -> w.first().isDigit() && w.all(Char::isLetterOrDigit) }
        val titleWords = if (number != null) emptyList() else words.filterNot { it.startsWith("#") }
        return base.filter { p ->
            (number == null || p.number.lowercase().startsWith(number)) &&
                titleWords.all { p.title.contains(it, ignoreCase = true) } &&
                tags.all { t -> p.tags.any { it.contains(t) } } &&
                (difficulty == null || bucket(p).equals(difficulty, ignoreCase = true)) &&
                (status == null || statusOf(p) == status) &&
                (!savedOnly || p.slug in saved)
        }
    }

    companion object {
        /**
         * Easy, Medium or Hard. Codeforces problems carry a rating instead (rev 13, *agent
         * decision*): up to 1200 is Easy, 1300–1900 Medium, 2000 and up Hard; unrated is Medium.
         */
        fun bucket(p: ProblemSummary): String {
            val rating = p.difficulty.toIntOrNull() ?: return p.difficulty
            return when {
                rating <= 1200 -> "Easy"
                rating < 2000 -> "Medium"
                else -> "Hard"
            }
        }

        fun statusOf(p: ProblemSummary) = when (p.status) {
            "ac" -> "solved"
            "notac" -> "tried"
            else -> "todo"
        }
    }
}
