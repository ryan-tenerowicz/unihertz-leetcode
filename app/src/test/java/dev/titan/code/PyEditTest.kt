package dev.titan.code

import dev.titan.code.editor.Pos
import dev.titan.code.editor.PyEdit
import dev.titan.code.editor.TextBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A plain-lines [TextBuffer]. `|` in [text] marks the cursor; `[`…`]` aren't used as markers. */
class FakeBuffer(text: String) : TextBuffer {
    private val lines = mutableListOf<String>()
    override var selStart = Pos(0, 0)
    override var selEnd = Pos(0, 0)
    var groups = 0

    init {
        val i = text.indexOf('|')
        val clean = text.replaceFirst("|", "")
        lines += clean.split("\n")
        if (i >= 0) {
            val before = text.substring(0, i)
            val p = Pos(before.count { it == '\n' }, before.length - before.lastIndexOf('\n') - 1)
            selStart = p; selEnd = p
        }
    }

    override val lineCount get() = lines.size
    override fun line(i: Int) = lines[i]
    override fun replace(start: Pos, end: Pos, text: String) {
        val head = lines[start.line].substring(0, start.col)
        val tail = lines[end.line].substring(end.col)
        repeat(end.line - start.line + 1) { lines.removeAt(start.line) }
        lines.addAll(start.line, (head + text + tail).split("\n"))
    }
    override fun select(start: Pos, end: Pos) { selStart = minOf(start, end); selEnd = maxOf(start, end) }
    override fun group(block: () -> Unit) { groups++; block() }

    /** Buffer text with `|` at the cursor (selection not shown). */
    fun show(): String {
        val sb = StringBuilder()
        lines.forEachIndexed { i, l ->
            if (i > 0) sb.append('\n')
            sb.append(if (i == selEnd.line) l.substring(0, selEnd.col) + "|" + l.substring(selEnd.col) else l)
        }
        return sb.toString()
    }
}

class PyEditTest {
    private fun ed(text: String) = FakeBuffer(text).let { it to PyEdit(it) }
    private fun typeAll(e: PyEdit, s: String) = s.forEach { if (it == '\n') e.newline() else e.type(it.toString()) }

    // E03 auto-pairing
    @Test fun openersPairAndTypeOver() {
        val (b, e) = ed("|")
        typeAll(e, "f(")
        assertEquals("f(|)", b.show())
        typeAll(e, "x)")
        assertEquals("f(x)|", b.show())
        typeAll(e, " [{")
        assertEquals("f(x) [{|}]", b.show())
    }

    @Test fun backspaceDeletesEmptyPair() {
        val (b, e) = ed("x = (|)")
        e.backspace()
        assertEquals("x = |", b.show())
        val (b2, e2) = ed("s = '|'")
        e2.backspace()
        assertEquals("s = |", b2.show())
    }

    @Test fun backspaceBetweenTwoStringsDeletesOneQuote() {
        val (b, e) = ed("'a'|'b'")
        e.backspace()
        assertEquals("'a|'b'", b.show())
    }

    @Test fun quotesPairAndTypeOver() {
        val (b, e) = ed("x = |")
        typeAll(e, "''.join")
        assertEquals("x = ''.join|", b.show())
        val (b2, e2) = ed("|")
        typeAll(e2, "f'")
        assertEquals("f'|'", b2.show())
    }

    @Test fun noPairingInsideStringsOrComments() {
        val (b, e) = ed("x = |")
        typeAll(e, "'(['")
        assertEquals("x = '(['|", b.show())
        val (b2, e2) = ed("# see |")
        typeAll(e2, "(x")
        assertEquals("# see (x|", b2.show())
    }

    @Test fun openerBeforeWordDoesNotPair() {
        val (b, e) = ed("|foo")
        e.type("(")
        assertEquals("(|foo", b.show())
    }

    @Test fun wordQuoteDoesNotPair() {
        val (b, e) = ed("# don|")
        e.type("'")
        assertEquals("# don'|", b.show())
        val (b2, e2) = ed("x|")
        e2.type("'")
        assertEquals("x'|", b2.show())
    }

    // E04 smart Enter
    @Test fun enterAfterColonIndents() {
        val (b, e) = ed("    for x in y:|")
        e.newline()
        assertEquals("    for x in y:\n        |", b.show())
    }

    @Test fun enterCopiesIndent() {
        val (b, e) = ed("        a = 1|")
        e.newline()
        assertEquals("        a = 1\n        |", b.show())
    }

    @Test fun enterAfterReturnDedents() {
        val (b, e) = ed("            return x|")
        e.newline()
        assertEquals("            return x\n        |", b.show())
    }

    @Test fun enterInsideBracketsOpensBlock() {
        val (b, e) = ed("    x = [|]")
        e.newline()
        assertEquals("    x = [\n        |\n    ]", b.show())
    }

    @Test fun enterIgnoresColonInComment() {
        val (b, e) = ed("    a = 1  # note:|")
        e.newline()
        assertEquals("    a = 1  # note:\n    |", b.show())
    }

    @Test fun enterOnBlankLineClearsIt() {
        val (b, e) = ed("        |")
        e.newline()
        assertEquals("\n        |", b.show())
    }

    // E05 smart Backspace
    @Test fun backspaceInIndentGoesToPreviousStop() {
        val (b, e) = ed("            |x")
        e.backspace()
        assertEquals("        |x", b.show())
        val (b2, e2) = ed("      |")
        e2.backspace()
        assertEquals("    |", b2.show())
    }

    @Test fun indentToNextStop() {
        val (b, e) = ed("ab|")
        e.indent()
        assertEquals("ab  |", b.show())
    }

    // E01/E02: typing the references with the rules gives the reference text.
    private fun typeReference(starter: String, reference: String) {
        val b = FakeBuffer(starter + "|")
        val e = PyEdit(b)
        val body = reference.trimEnd().lines().drop(2)
        var prevIndent = 8
        body.forEachIndexed { i, line ->
            if (i > 0) {
                e.newline()
                val cur = b.line(b.selEnd.line).length
                val want = line.length - line.trimStart().length
                var n = cur
                while (n > want) { e.backspace(); n = b.selEnd.col }
                assertEquals("indent before line '$line'", want, b.selEnd.col)
            } else assertEquals(prevIndent, b.selEnd.col)
            line.trimStart().forEach { c -> e.type(c.toString()); typeOverCheck(b) }
            // Closers typed by auto-pairing: the cursor may sit before them; jump to line end.
            b.select(Pos(b.selEnd.line, b.line(b.selEnd.line).length))
            prevIndent = 0
        }
        assertEquals(reference.trimEnd(), e.allText().trimEnd())
    }

    private fun typeOverCheck(b: FakeBuffer) = assertTrue(b.selEnd.col <= b.line(b.selEnd.line).length)

    private fun asset(path: String) = java.io.File("src/main/assets/samples/$path").readText()

    @Test fun referencesTypeExactly() {
        for (slug in listOf("two-sum", "group-anagrams", "valid-parentheses")) {
            typeReference(asset("$slug/starter.py"), asset("$slug/reference.py"))
        }
    }

    // §5.4 line commands
    @Test fun lineCommands() {
        val (b, e) = ed("a\nb|\nc")
        e.duplicateLine()
        assertEquals("a\nb\nb|\nc", b.show())
        e.deleteLine()
        assertEquals("a\nb\nc|", b.show())
        e.toggleComment()
        assertEquals("a\nb\n# c|", b.show())
        e.toggleComment()
        assertEquals("a\nb\nc|", b.show())
    }

    @Test fun lineCopyPastesAbove() {
        val (b, e) = ed("one|\ntwo")
        val clip = e.copy()
        assertTrue(clip.wholeLine)
        b.select(Pos(1, 1))
        e.paste(clip)
        assertEquals("one\none\nt|wo", b.show())
    }

    @Test fun selectLineExtends() {
        val (b, e) = ed("a\nb|\nc")
        e.selectLine()
        assertEquals(Pos(1, 0) to Pos(2, 0), b.selStart to b.selEnd)
        e.selectLine()
        assertEquals(Pos(1, 0) to Pos(2, 1), b.selStart to b.selEnd)
    }
}
