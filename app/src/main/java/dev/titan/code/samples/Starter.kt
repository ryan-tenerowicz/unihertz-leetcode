package dev.titan.code.samples

/**
 * Prepares a `python3` starter snippet for the editor (PLAN §5.5 "Starter code").
 *
 * LeetCode snippets end each method with an indented empty body line (no `pass`). We put the
 * cursor at the end of the first such line. If a body line lost its indentation (trimmed
 * whitespace), it is re-indented to one level deeper than its `def`, so the cursor always lands
 * where the first statement goes.
 *
 * LeetCode snippets indent by 4. With a 2-space indent setting (PLAN §4.2), each leading group of
 * 4 spaces becomes 2, so the starter matches what smart Enter will produce.
 */
object Starter {
    data class Prepared(val text: String, val line: Int, val column: Int)

    private val DEF = Regex("""^(\s*)(async\s+)?def\s.*:\s*$""")

    fun prepare(snippet: String, indentWidth: Int = 4): Prepared {
        val lines = snippet.replace("\r\n", "\n").split("\n").map { reindent(it, indentWidth) }.toMutableList()
        var target: Pair<Int, Int>? = null
        var i = 0
        while (i < lines.size) {
            val m = DEF.matchEntire(lines[i])
            if (m != null) {
                val bodyIndent = m.groupValues[1].length + indentWidth
                val next = i + 1
                if (next >= lines.size) {
                    lines.add(" ".repeat(bodyIndent))
                } else if (lines[next].isBlank() && lines[next].length < bodyIndent) {
                    lines[next] = " ".repeat(bodyIndent)
                }
                if (lines[next].isBlank() && target == null) target = next to lines[next].length
            }
            i++
        }
        val (line, column) = target ?: (lines.lastIndex to lines.last().length)
        return Prepared(lines.joinToString("\n"), line, column)
    }

    private fun reindent(line: String, width: Int): String {
        if (width == 4) return line
        val n = line.length - line.trimStart(' ').length
        return " ".repeat((n / 4) * width + n % 4) + line.substring(n)
    }
}
