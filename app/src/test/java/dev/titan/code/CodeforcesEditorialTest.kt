package dev.titan.code

import dev.titan.code.data.codeforces.CodeforcesApi
import dev.titan.code.data.codeforces.CodeforcesEditorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rev 32: Codeforces editorials in the Comments pane. */
class CodeforcesEditorialTest {
    private fun res(name: String) = javaClass.getResource("/codeforces/$name")!!.readText()
    private val page = res("editorial-132373.html")

    @Test fun tutorialLinksFromContestMaterials() {
        assertEquals(listOf(132373L), CodeforcesEditorial.tutorialIds(page))
        val sidebar = """<div id="sidebar"><div class="roundbox sidebox"><div class="caption">→ Contest materials</div><ul>
            <li><a href="/blog/entry/1" title="Announcement (en)">Announcement (en)</a></li>
            <li><a href="/blog/entry/2" title="Разбор задач (ru)">Разбор задач (ru)</a></li>
            <li><a href="https://codeforces.com/blog/entry/3" title="Tutorial #1 (en)">Tutorial #1 (en)</a></li>
            <li><a href="https://www.youtube.com/watch?v=x" title="Video Tutorial (en)">Video Tutorial</a></li></ul></div>
            <div class="roundbox sidebox"><div class="caption">Recent actions</div><a href="/blog/entry/9">Editorial of something else</a></div></div>"""
        assertEquals(listOf(3L, 2L), CodeforcesEditorial.tutorialIds(sidebar))
        assertEquals(emptyList<Long>(), CodeforcesEditorial.tutorialIds("<html><body><p>No sidebar</p></body></html>"))
    }

    @Test fun statementSavesTheTutorialLinks() {
        val d = CodeforcesApi.parseStatement(res("4A.html"), "cf:4A").detail
        assertEquals(emptyList<Long>(), CodeforcesApi.editorialIds(d.metaData))
        assertNull(CodeforcesApi.editorialIds("""{"stdin":true}"""))
    }

    @Test fun cutsOutOneProblemsPart() {
        val e = CodeforcesEditorial.parseEntry(page, 132373)
        assertEquals("Codeforces Round 964 (Div. 4) Editorial", e.title)
        assertEquals("flamestorm", e.author)

        val a = CodeforcesEditorial.show(CodeforcesEditorial.section(e, 1999, "A", "A+B Again?")!!)
        assertTrue(a, a.contains("1999A - A+B Again?"))
        assertTrue(a.contains("sum of the two digits"))
        assertTrue("maths becomes text", a.contains("⌊") && !a.contains("$$$"))
        assertTrue("spoilers become open details: $a", a.contains("<details class=\"spoiler\" open><summary>Tutorial</summary>"))
        assertTrue("C++ shown in full", a.contains("<pre><span class=\"lang\">cpp</span><code>#include") && !a.contains("class=\"code\""))
        assertTrue("code is escaped once", a.contains("#include &lt;bits/stdc++.h&gt;"))
        assertFalse("stops at the next problem", a.contains("Card Game"))
        assertFalse(a.contains("<a "))
        assertFalse(a.contains("style="))

        val b = CodeforcesEditorial.show(CodeforcesEditorial.section(e, 1999, "B", "Card Game")!!)
        assertTrue(b.contains("Suneet"))
        assertTrue("Python shown in full", b.contains("<pre><span class=\"lang\">python</span><code>") && !b.contains("more lines"))
        assertFalse(b.contains("A+B Again?"))
        assertFalse(b.contains("Showering"))

        // A tutorial the page didn't fill says so.
        val c = CodeforcesEditorial.show(CodeforcesEditorial.section(e, 1999, "C", "Showering")!!)
        assertTrue(c.contains("didn't load"))
    }

    @Test fun findsTheProblemByTitleInTheOtherDivision() {
        val e = CodeforcesEditorial.parseEntry(page, 132373)
        // The same problem in a parallel contest (another id and letter) is found by its title.
        val b = CodeforcesEditorial.section(e, 2000, "A", "Card Game")
        assertNotNull(b)
        assertTrue(b!!.contains("Suneet"))
        assertNull(CodeforcesEditorial.section(e, 2000, "Q", "Nothing Like It"))
        assertTrue(CodeforcesEditorial.show(CodeforcesEditorial.whole(e)).contains("Showering"))
    }

    @Test fun headingStyleEditorials() {
        val html = """<html><head><title>Editorial - Codeforces</title></head><body><div class="topic"><div class="title">Round 1 Editorial</div>
            <div class="content"><div class="ttypography"><div>
            <p>Thanks!</p>
            <h3>A. First Steps</h3><p>Count them.</p>
            <p><strong>B. Second Thoughts</strong></p><p>Sort them, $$$ n \log n $$$.</p><pre>n = int(input())
print(n)</pre>
            <h3>C. Third Time</h3><p>Use a heap.</p>
            </div></div></div></div></body></html>"""
        val e = CodeforcesEditorial.parseEntry(html, 5)
        assertEquals("", e.author)
        val b = CodeforcesEditorial.show(CodeforcesEditorial.section(e, 100, "B", "Second Thoughts")!!)
        assertTrue(b.contains("Sort them") && b.contains("log n"))
        assertTrue(b.contains("print(n)"))
        assertFalse(b.contains("Count them"))
        assertFalse(b.contains("heap"))
        assertTrue(CodeforcesEditorial.show(CodeforcesEditorial.section(e, 100, "C", "Third Time")!!).contains("heap"))
    }

    /** Rev 33: what's saved is the page's HTML; formatting happens when it's shown and can't fail. */
    @Test fun savedRawAndShownSafely() {
        val e = CodeforcesEditorial.parseEntry(page, 132373)
        val raw = CodeforcesEditorial.section(e, 1999, "A", "A+B Again?")!!
        assertTrue("saved as the page wrote it", raw.contains("spoiler-title") && raw.contains("$$$"))
        // Editorials saved by rev 32 were already formatted: shown as they are, not formatted twice.
        val old = "<details class=\"spoiler\"><summary>Tutorial</summary><p>x</p></details>"
        assertEquals(old, CodeforcesEditorial.show(old))
        // Broken maths stays as its TeX instead of failing the page.
        val shown = CodeforcesEditorial.show("<!--cf-editorial--><p>$$$\\frac{a}{b$$$ and $$$ x^{2} $$$</p>")
        assertTrue(shown, shown.contains("x<sup>2</sup>"))
        // Rev 35: \gt and "\ " before a letter (seen in 2275D's editorial).
        val gt = CodeforcesApi.texBody("a_i \\gt b_i \\lor \\ a_i \\lt c_i")
        assertEquals("a<sub>i</sub> &gt; b<sub>i</sub> ∨ a<sub>i</sub> &lt; c<sub>i</sub>", gt)
    }

    @Test fun challengePage() {
        val err = runCatching { CodeforcesEditorial.parseEntry("<html><head><title>Just a moment...</title></head></html>", 1) }.exceptionOrNull()
        assertTrue(err is CodeforcesApi.ApiError)
    }

    @Test fun codeLanguages() {
        assertEquals("cpp", CodeforcesEditorial.language("#include <bits/stdc++.h>\nint main() {}"))
        assertEquals("python", CodeforcesEditorial.language("for _ in range(int(input())):\n    print(1)"))
        assertEquals("java", CodeforcesEditorial.language("public static void main(String[] a) {}"))
        assertEquals("code", CodeforcesEditorial.language("x y z"))
    }
}
