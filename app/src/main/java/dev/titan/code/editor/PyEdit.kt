package dev.titan.code.editor

/** A position in the buffer: 0-based line and column. */
data class Pos(val line: Int, val col: Int) : Comparable<Pos> {
    override fun compareTo(other: Pos) = compareValuesBy(this, other, Pos::line, Pos::col)
}

/**
 * The text model PyEdit works on. Sora implements it in `TitanEditor`; tests use a plain list of
 * lines. Keeping it this small is what makes the editing rules testable on the JVM.
 */
interface TextBuffer {
    val lineCount: Int
    fun line(i: Int): String
    /** Selection, ordered (start <= end). Equal when there's just a cursor. */
    val selStart: Pos
    val selEnd: Pos
    /** Replace [start, end) with [text]. Doesn't move the cursor; callers [select] afterwards. */
    fun replace(start: Pos, end: Pos, text: String)
    fun select(start: Pos, end: Pos = start)
    /** Run [block] as one undo step. */
    fun group(block: () -> Unit)
}

/** What `sym+C`/`sym+X` copied. [wholeLine] text pastes as whole lines above the cursor line. */
data class Clip(val text: String, val wholeLine: Boolean)

/**
 * Python-aware editing rules (PLAN §5.5) and the line commands of §5.4, on top of [TextBuffer].
 *
 * Decisions (M1, documented in PLAN Appendix B):
 * - Openers `( [ {` pair only when the next character is end of line, whitespace or a closer
 *   (so `(` before `foo` doesn't produce `()foo`). Quotes additionally don't pair after a word
 *   character, except string prefixes like `f` or `rb`.
 * - String/comment detection scans the current line only (single-line strings and `#`);
 *   triple-quoted strings spanning lines aren't detected.
 * - Typing over a selection deletes it, then the normal rules apply (no wrapping).
 * - Enter on a whitespace-only line clears it; text after the cursor loses its leading spaces.
 * - Enter after an unclosed opener indents one level.
 */
class PyEdit(private val buf: TextBuffer, var indentWidth: Int = 4) {

    private val cursor get() = buf.selEnd
    private val hasSelection get() = buf.selStart != buf.selEnd

    // ---- typing -------------------------------------------------------------------------

    fun type(text: String) {
        if (text.length != 1 && !(text.length == 2 && Character.isSurrogatePair(text[0], text[1]))) {
            insertPlain(text)
            return
        }
        val c = text[0]
        buf.group {
            if (hasSelection) deleteSelection()
            val p = cursor
            val line = buf.line(p.line)
            val next = line.getOrNull(p.col)
            val inCode = PyScan.contextAt(line, p.col) == PyScan.Context.CODE
            when {
                c in CLOSERS && next == c && inCode -> buf.select(Pos(p.line, p.col + 1))
                c in QUOTES && next == c && PyScan.stringQuoteAt(line, p.col) == c -> buf.select(Pos(p.line, p.col + 1))
                !inCode -> insertAt(p, text)
                c in OPENERS && pairAllowed(next) -> insertAt(p, "$c${OPENERS[c]}", cursorOffset = 1)
                c in QUOTES && pairAllowed(next) && quoteMayOpen(line, p.col) -> insertAt(p, "$c$c", cursorOffset = 1)
                else -> insertAt(p, text)
            }
        }
    }

    /** Paste-like insert: no pairing, no indentation rules. */
    fun insertPlain(text: String) = buf.group {
        if (hasSelection) deleteSelection()
        insertAt(cursor, text)
    }

    private fun pairAllowed(next: Char?) = next == null || next.isWhitespace() || next in ")]},:;"

    private fun quoteMayOpen(line: String, col: Int): Boolean {
        val before = line.substring(0, col)
        if (before.isEmpty() || !isWord(before.last())) return true
        return STRING_PREFIX.containsMatchIn(before)
    }

    // ---- Enter --------------------------------------------------------------------------

    fun newline() = buf.group {
        if (hasSelection) deleteSelection()
        val p = cursor
        val line = buf.line(p.line)
        val left = line.substring(0, p.col)
        val right = line.substring(p.col)
        val indent = minOf(leadingSpaces(line), p.col)
        val code = PyScan.stripComment(left).trimEnd()
        val prev = code.lastOrNull()
        val leftKept = if (left.isBlank()) "" else left.trimEnd()
        val rightKept = right.trimStart()

        val first = rightKept.firstOrNull()
        if (prev != null && prev in OPENERS && first == OPENERS[prev]) {
            // Between a bracket pair: open a block.
            val inner = " ".repeat(indent + indentWidth)
            val outer = " ".repeat(indent)
            buf.replace(Pos(p.line, 0), Pos(p.line, line.length), "$leftKept\n$inner\n$outer$rightKept")
            buf.select(Pos(p.line + 1, inner.length))
            return@group
        }
        val newIndent = when {
            prev == ':' -> indent + indentWidth
            prev != null && prev in OPENERS -> indent + indentWidth
            DEDENT_AFTER.containsMatchIn(code.trimStart()) -> (indent - indentWidth).coerceAtLeast(0)
            else -> indent
        }
        val pad = " ".repeat(newIndent)
        buf.replace(Pos(p.line, 0), Pos(p.line, line.length), "$leftKept\n$pad$rightKept")
        buf.select(Pos(p.line + 1, pad.length))
    }

    // ---- deleting -----------------------------------------------------------------------

    fun backspace() = buf.group {
        if (hasSelection) { deleteSelection(); return@group }
        val p = cursor
        if (p.col == 0) {
            if (p.line == 0) return@group
            val prevLen = buf.line(p.line - 1).length
            buf.replace(Pos(p.line - 1, prevLen), p, "")
            buf.select(Pos(p.line - 1, prevLen))
            return@group
        }
        val line = buf.line(p.line)
        val before = line[p.col - 1]
        val after = line.getOrNull(p.col)
        val emptyQuotes = before in QUOTES && before == after && PyScan.stringQuoteAt(line, p.col) == before
        if (after != null && (OPENERS[before] == after || emptyQuotes)) {
            buf.replace(Pos(p.line, p.col - 1), Pos(p.line, p.col + 1), "")
            buf.select(Pos(p.line, p.col - 1))
            return@group
        }
        if (line.substring(0, p.col).isBlank()) {
            val stop = ((p.col - 1) / indentWidth) * indentWidth
            buf.replace(Pos(p.line, stop), p, "")
            buf.select(Pos(p.line, stop))
            return@group
        }
        val n = if (p.col >= 2 && Character.isSurrogatePair(line[p.col - 2], line[p.col - 1])) 2 else 1
        buf.replace(Pos(p.line, p.col - n), p, "")
        buf.select(Pos(p.line, p.col - n))
    }

    fun deleteForward() = buf.group {
        if (hasSelection) { deleteSelection(); return@group }
        val p = cursor
        val line = buf.line(p.line)
        if (p.col < line.length) {
            val n = if (p.col + 1 < line.length && Character.isSurrogatePair(line[p.col], line[p.col + 1])) 2 else 1
            buf.replace(p, Pos(p.line, p.col + n), "")
        } else if (p.line + 1 < buf.lineCount) {
            buf.replace(p, Pos(p.line + 1, 0), "")
        }
        buf.select(p)
    }

    // ---- indent (sym+Space) -------------------------------------------------------------

    /** One indent level at the cursor; with a selection spanning lines, indents every line. */
    fun indent() = buf.group {
        if (hasSelection && buf.selStart.line != buf.selEnd.line) {
            val (first, last) = selectedLines()
            val pad = " ".repeat(indentWidth)
            for (i in first..last) if (buf.line(i).isNotBlank()) buf.replace(Pos(i, 0), Pos(i, 0), pad)
            buf.select(Pos(first, 0), Pos(last, buf.line(last).length))
            return@group
        }
        if (hasSelection) deleteSelection()
        val p = cursor
        val n = indentWidth - (p.col % indentWidth)
        insertAt(p, " ".repeat(n))
    }

    // ---- line commands (§5.4) -----------------------------------------------------------

    /** `sym+L`: select the current line; repeat to extend by one more line. */
    fun selectLine() {
        val s = buf.selStart
        val e = buf.selEnd
        val wholeLines = hasSelection && s.col == 0 && (e.col == 0 && e.line > s.line || e == endOfDoc())
        val first = if (wholeLines) s.line else cursor.line
        val nextLine = (if (wholeLines) e.line else cursor.line) + 1
        val end = if (nextLine < buf.lineCount) Pos(nextLine, 0) else endOfDoc()
        if (wholeLines && end == e) return
        buf.select(Pos(first, 0), end)
    }

    /** `sym+D`: delete the current line (or every line the selection touches). */
    fun deleteLine() = buf.group {
        val (first, last) = selectedLines()
        val col = cursor.col
        when {
            last + 1 < buf.lineCount -> buf.replace(Pos(first, 0), Pos(last + 1, 0), "")
            first > 0 -> buf.replace(Pos(first - 1, buf.line(first - 1).length), Pos(last, buf.line(last).length), "")
            else -> buf.replace(Pos(0, 0), Pos(last, buf.line(last).length), "")
        }
        val line = first.coerceAtMost(buf.lineCount - 1)
        buf.select(Pos(line, col.coerceAtMost(buf.line(line).length)))
    }

    /** `sym+J`: duplicate the current line (or selected lines) below; the cursor follows the copy. */
    fun duplicateLine() = buf.group {
        val (first, last) = selectedLines()
        val block = (first..last).joinToString("\n") { buf.line(it) }
        val s = buf.selStart
        val e = buf.selEnd
        buf.replace(Pos(last, buf.line(last).length), Pos(last, buf.line(last).length), "\n$block")
        val shift = last - first + 1
        buf.select(Pos(s.line + shift, s.col), Pos(e.line + shift, e.col))
    }

    /** `sym+K`: toggle `# ` on the current line or the selected lines. */
    fun toggleComment() = buf.group {
        val (first, last) = selectedLines()
        val lines = (first..last).filter { buf.line(it).isNotBlank() }
        if (lines.isEmpty()) return@group
        val allCommented = lines.all { buf.line(it).trimStart().startsWith("#") }
        val s = buf.selStart
        val e = buf.selEnd
        var deltaS = 0
        var deltaE = 0
        if (allCommented) {
            for (i in lines) {
                val l = buf.line(i)
                val at = leadingSpaces(l)
                val n = if (l.startsWith("# ", at)) 2 else 1
                buf.replace(Pos(i, at), Pos(i, at + n), "")
                if (i == s.line && s.col > at) deltaS = -minOf(n, s.col - at)
                if (i == e.line && e.col > at) deltaE = -minOf(n, e.col - at)
            }
        } else {
            val at = lines.minOf { leadingSpaces(buf.line(it)) }
            for (i in lines) {
                buf.replace(Pos(i, at), Pos(i, at), "# ")
                if (i == s.line && s.col >= at) deltaS = 2
                if (i == e.line && e.col >= at) deltaE = 2
            }
        }
        buf.select(Pos(s.line, (s.col + deltaS).coerceAtLeast(0)), Pos(e.line, (e.col + deltaE).coerceAtLeast(0)))
    }

    // ---- clipboard ----------------------------------------------------------------------

    fun copy(): Clip =
        if (hasSelection) Clip(selectedText(), false)
        else Clip(buf.line(cursor.line) + "\n", true)

    fun cut(): Clip {
        val clip = copy()
        if (clip.wholeLine) deleteLine() else buf.group { deleteSelection() }
        return clip
    }

    /** Whole-line clips go above the current line (VS Code style); others at the cursor. */
    fun paste(clip: Clip) = buf.group {
        if (clip.wholeLine && !hasSelection) {
            val p = cursor
            buf.replace(Pos(p.line, 0), Pos(p.line, 0), clip.text)
            val added = clip.text.count { it == '\n' }
            buf.select(Pos(p.line + added, p.col))
        } else {
            if (hasSelection) deleteSelection()
            insertAt(cursor, clip.text)
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    // ---- completion (§5.4 sym+Space, M5) -----------------------------------------------

    /** The identifier being typed just before the cursor, in code only; "" otherwise. */
    fun wordPrefix(): String {
        if (hasSelection) return ""
        val p = cursor
        val line = buf.line(p.line)
        var i = p.col
        while (i > 0 && isWord(line[i - 1])) i--
        val w = line.substring(i, p.col)
        if (w.isEmpty() || w[0].isDigit()) return ""
        if (p.col < line.length && isWord(line[p.col])) return ""
        return if (PyScan.contextAt(line, i) == PyScan.Context.CODE) w else ""
    }

    /**
     * Where a completion would go (rev 38): the identifier before the cursor (may be empty right
     * after a `.`), whether it follows a `.`, and the cursor (0-based line and column), in code
     * only. Null when there's nothing to complete: a selection, mid-word, a number, a string or
     * comment, or no prefix and no `.`.
     */
    fun completionSite(): CompletionSite? {
        if (hasSelection) return null
        val p = cursor
        val line = buf.line(p.line)
        var i = p.col
        while (i > 0 && isWord(line[i - 1])) i--
        val w = line.substring(i, p.col)
        if (w.isNotEmpty() && w[0].isDigit()) return null
        if (p.col < line.length && isWord(line[p.col])) return null
        val dot = i > 0 && line[i - 1] == '.'
        if (w.isEmpty() && !dot) return null
        // `1.` and `x = 2.5` are numbers, not attribute access.
        if (dot && w.isEmpty() && i >= 2 && line[i - 2].isDigit()) {
            var j = i - 2
            while (j > 0 && isWord(line[j - 1])) j--
            if (line[j].isDigit()) return null
        }
        if (PyScan.contextAt(line, i) != PyScan.Context.CODE) return null
        return CompletionSite(w, dot, p.line, p.col, line.substring(0, if (dot) i - 1 else i), line.substring(p.col))
    }

    /**
     * Replace the [prefixLen] characters before the cursor with [text], as one undo step.
     * In [text], a tab is one indent level, each new line keeps the current line's indent,
     * and `$0` marks where the cursor ends (default: the end).
     */
    fun complete(prefixLen: Int, text: String) = buf.group {
        val p = cursor
        val start = Pos(p.line, (p.col - prefixLen).coerceAtLeast(0))
        val indent = " ".repeat(leadingSpaces(buf.line(p.line)))
        var t = text.replace("\t", " ".repeat(indentWidth)).replace("\n", "\n" + indent)
        val mark = t.indexOf("\$0")
        t = t.replace("\$0", "")
        buf.replace(start, p, "")
        insertAt(start, t, if (mark >= 0) mark else t.length)
    }

    /** Identifiers in the buffer (3+ characters), for completion. */
    fun words(): Set<String> = WORD.findAll(allText()).map { it.value }.filter { it.length >= 3 }.toSet()

    fun allText(): String = (0 until buf.lineCount).joinToString("\n") { buf.line(it) }

    private fun offsetOf(p: Pos): Int {
        var o = 0
        for (i in 0 until p.line) o += buf.line(i).length + 1
        return o + p.col
    }

    private fun posOf(offset: Int): Pos {
        var o = offset
        for (i in 0 until buf.lineCount) {
            val len = buf.line(i).length
            if (o <= len) return Pos(i, o)
            o -= len + 1
        }
        return endOfDoc()
    }

    private fun selectedText(): String {
        val s = buf.selStart
        val e = buf.selEnd
        if (s.line == e.line) return buf.line(s.line).substring(s.col, e.col)
        val sb = StringBuilder(buf.line(s.line).substring(s.col))
        for (i in s.line + 1 until e.line) sb.append('\n').append(buf.line(i))
        sb.append('\n').append(buf.line(e.line).substring(0, e.col))
        return sb.toString()
    }

    /** Lines the selection touches; a selection ending at column 0 doesn't include that line. */
    private fun selectedLines(): Pair<Int, Int> {
        val s = buf.selStart
        val e = buf.selEnd
        val last = if (e.line > s.line && e.col == 0) e.line - 1 else e.line
        return s.line to last
    }

    private fun endOfDoc() = Pos(buf.lineCount - 1, buf.line(buf.lineCount - 1).length)

    private fun deleteSelection() {
        val s = buf.selStart
        buf.replace(s, buf.selEnd, "")
        buf.select(s)
    }

    private fun insertAt(p: Pos, text: String, cursorOffset: Int = text.length) {
        buf.replace(p, p, text)
        val head = text.substring(0, cursorOffset)
        val nl = head.lastIndexOf('\n')
        buf.select(if (nl < 0) Pos(p.line, p.col + head.length) else Pos(p.line + head.count { it == '\n' }, head.length - nl - 1))
    }

    private fun leadingSpaces(s: String) = s.length - s.trimStart(' ', '\t').length
    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    companion object {
        val OPENERS = mapOf('(' to ')', '[' to ']', '{' to '}')
        val CLOSERS = setOf(')', ']', '}')
        val QUOTES = setOf('\'', '"')
        private val STRING_PREFIX = Regex("""(^|[^\w])([rRbBfFuU]|[rR][bBfF]|[bBfF][rR])$""")
        private val DEDENT_AFTER = Regex("""^(return|break|continue|pass|raise)\b""")
        private val WORD = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    }
}

/** A one-line Python scanner: is a column in code, a string or a comment? */
/** See [PyEdit.completionSite]. */
/** [before]: the line before the word (and its `.`); [after]: the line after the cursor. */
data class CompletionSite(
    val prefix: String, val afterDot: Boolean, val line: Int, val col: Int,
    val before: String = "", val after: String = "",
) {
    /** Where the word being completed starts; the suggestions belong to this spot. */
    val anchor get() = line to col - prefix.length
}

object PyScan {
    enum class Context { CODE, STRING, COMMENT }

    /** Context just before [col] on [line]. */
    fun contextAt(line: String, col: Int): Context = scan(line, col).first

    /** If [col] is inside a string, the quote character that opened it. */
    fun stringQuoteAt(line: String, col: Int): Char? = scan(line, col).second

    /** [line] without a trailing `#` comment (strings respected). */
    fun stripComment(line: String): String {
        var quote: String? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (quote != null) {
                if (c == '\\') { i += 2; continue }
                if (line.startsWith(quote, i)) { i += quote.length; quote = null; continue }
            } else {
                if (c == '#') return line.substring(0, i)
                if (c == '\'' || c == '"') {
                    quote = if (line.startsWith("$c$c$c", i)) "$c$c$c" else "$c"
                    i += quote.length
                    continue
                }
            }
            i++
        }
        return line
    }

    private fun scan(line: String, col: Int): Pair<Context, Char?> {
        var quote: String? = null
        var i = 0
        val end = col.coerceAtMost(line.length)
        while (i < end) {
            val c = line[i]
            if (quote != null) {
                if (c == '\\') { i += 2; continue }
                if (line.startsWith(quote, i) && i + quote.length <= end) { i += quote.length; quote = null; continue }
                if (line.startsWith(quote, i)) return Context.STRING to quote[0]
            } else {
                if (c == '#') return Context.COMMENT to null
                if (c == '\'' || c == '"') {
                    quote = if (line.startsWith("$c$c$c", i) && i + 3 <= end) "$c$c$c" else "$c"
                    i += quote.length
                    continue
                }
            }
            i++
        }
        return if (quote != null) Context.STRING to quote[0] else Context.CODE to null
    }
}
