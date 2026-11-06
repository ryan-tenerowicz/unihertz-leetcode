package dev.titan.code

import dev.titan.code.data.Platform
import dev.titan.code.data.ProblemFilter
import dev.titan.code.data.codeforces.CodeforcesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeforcesApiTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("codeforces/$name")!!.readText()

    @Test fun problemListNewestFirstWithRating() {
        val ps = CodeforcesApi.parseProblems(res("problems.json"))
        assertEquals("cf:2278D", ps.first().slug)
        val wm = ps.first { it.slug == "cf:4A" }
        assertEquals("Watermelon", wm.title)
        assertEquals("800", wm.difficulty)
        assertEquals("4A. Watermelon", wm.label)
        assertEquals("Easy", ProblemFilter.bucket(wm))
        assertTrue(wm.acRate > 1000)
        assertEquals(Platform.CODEFORCES, wm.platform)
        assertEquals(listOf("cf:2278D", "cf:2278E", "cf:2278F", "cf:2278G", "cf:4A"), ps.map { it.slug })
    }

    @Test fun splitKeys() {
        assertEquals(4 to "A", CodeforcesApi.split("cf:4A"))
        assertEquals(2261 to "F1", CodeforcesApi.split("cf:2261F1"))
    }

    @Test fun statementSamplesLimitsAndTex() {
        val st = CodeforcesApi.parseStatement(res("4A.html"), "cf:4A")
        val d = st.detail
        assertEquals("Watermelon", d.title)
        assertEquals("4A. Watermelon", d.label)
        assertEquals("800", d.difficulty)
        assertEquals(listOf("brute force", "math"), d.tags)
        assertEquals(listOf("8\n" to "YES\n", "3 4\n1 2 3\n" to "NO\nx y\n"), st.samples)
        assertEquals(listOf("8\n", "3 4\n1 2 3\n"), CodeforcesApi.sampleInputs(d.exampleTestcases))
        assertEquals(listOf("YES\n", "NO\nx y\n"), CodeforcesApi.sampleOutputs(d.metaData))
        val html = d.contentHtml!!
        assertTrue(html.contains("1 second") && html.contains("64 megabytes"))
        assertFalse(html.contains("time limit per test"))
        assertTrue(html, html.contains("1 ≤ w ≤ 100"))
        assertTrue(html, html.contains("a<sub>i</sub> · 10<sup>9</sup>"))
        assertTrue(html.contains("src=\"https://codeforces.com/predownloaded/aa/bb/melon.png\""))
        assertFalse(html.contains("$$$"))
        assertEquals(CodeforcesApi.STARTER, d.starter)
    }

    @Test fun texOddsAndEnds() {
        assertEquals("⌊n/2⌋", CodeforcesApi.texBody("\\lfloor n/2 \\rfloor").replace(" ", ""))
        assertEquals("(a)/(b) mod 10<sup>9</sup>+7", CodeforcesApi.texBody("\\frac{a}{b} \\bmod 10^9+7").replace("  ", " "))
        assertEquals("sum", CodeforcesApi.texBody("\\text{sum}"))
    }

    @Test fun verdictFromUserStatus() {
        val v = CodeforcesApi.parseStatus(res("status.json"), 2261, "H", 0)!!
        assertTrue(v.done && v.accepted)
        assertEquals("Accepted", v.label)
        assertEquals("250 ms", v.time)
        assertNull(CodeforcesApi.parseStatus(res("status.json"), 2261, "H", v.createdSec + 1))
        val wa = v.copy(verdict = "WRONG_ANSWER", passedTests = 2)
        assertEquals("Wrong answer on test 3", wa.label)
        assertFalse(v.copy(verdict = "TESTING").done)
        assertFalse(v.copy(verdict = null).done)
    }

    @Test fun apiErrorsAndChallenges() {
        val e = runCatching { CodeforcesApi.result("{\"status\":\"FAILED\",\"comment\":\"handle: User with handle x not found\"}") }.exceptionOrNull()
        assertEquals("handle: User with handle x not found", e?.message)
        val c = runCatching { CodeforcesApi.parseStatement("<html><head><title>Just a moment...</title></head></html>", "cf:4A") }.exceptionOrNull()
        assertEquals("Codeforces asked for a browser check", c?.message)
    }

    /** Rev 30: MathJax had already drawn the maths, so each variable showed twice ("ɑ a"). */
    @Test fun mathJaxOutputBecomesText() {
        // As saved before rev 30 (from cf:2276A): the drawing plus the MathML copy, no TeX script.
        val saved = """<p>Monocarp has <span class="MathJax_Preview" style="color: inherit;"></span><span class="MathJax" id="MathJax-Element-1-Frame" data-mathml="&lt;math&gt;&lt;mi&gt;a&lt;/mi&gt;&lt;/math&gt;"><nobr aria-hidden="true"><span class="math"><span class="mi" style="font-family: MathJax_Math-italic;">a</span></span></nobr><span class="MJX_Assistive_MathML"><math xmlns="http://www.w3.org/1998/Math/MathML"><mi>a</mi></math></span></span> slices.</p>"""
        val fixed = CodeforcesApi.repairSavedMath(saved)!!
        assertEquals("<p>Monocarp has <span class=\"math\">a</span> slices.</p>", fixed.replace("\n", ""))
        assertEquals(null, CodeforcesApi.repairSavedMath("<p>no maths</p>"))

        // A fresh page: the TeX script after the drawing wins, so the usual TeX conversion applies.
        val page = """<html><body><div class="problem-statement"><div class="header"><div class="title">A. T</div></div><p>Let <span class="MathJax_Preview"></span><span class="MathJax"><nobr>x</nobr></span><script type="math/tex">1 \le n_i \le 10^9</script> and</p><div class="MathJax_Display"><span class="MathJax"><nobr>y</nobr></span></div><script type="math/tex; mode=display">a+b</script></div></body></html>"""
        val html = CodeforcesApi.parseStatement(page, "cf:1A").detail.contentHtml!!
        assertTrue(html, html.contains("<span class=\"math\" data-tex=\"1 \\le n_i \\le 10^9\">1 ≤ n<sub>i</sub> ≤ 10<sup>9</sup></span>"))
        assertTrue(html, html.contains("<div class=\"math\" data-tex=\"a+b\">a+b</div>"))
        assertFalse(html, html.contains("MathJax") || html.contains("nobr"))

        // MathML structure keeps sub/superscripts and fractions.
        val m = org.jsoup.Jsoup.parseBodyFragment("<math><msub><mi>a</mi><mi>i</mi></msub><mo>≤</mo><msup><mn>10</mn><mn>9</mn></msup><mfrac><mi>p</mi><mi>q</mi></mfrac></math>").selectFirst("math")!!
        assertEquals("a<sub>i</sub>≤10<sup>9</sup>(p)/(q)", CodeforcesApi.mathText(m))
    }
}
