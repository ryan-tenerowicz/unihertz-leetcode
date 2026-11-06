package dev.titan.code

import dev.titan.code.data.codeforces.CodeforcesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rev 36: Codeforces maths. KaTeX draws `data-tex` on the phone; [CodeforcesApi.texBody] is the
 * text shown when it can't, so it must never leave command names ("minbigl") or lose braces.
 */
class TexTest {
    private fun t(s: String) = CodeforcesApi.texBody(s)

    @Test fun sizingCommandsVanish() {
        // 2273D, as reported: "minbigl(|x-a_i|, |x-b_i|bigr)".
        assertEquals("min(|x-a<sub>i</sub>|, |x-b<sub>i</sub>|)", t("\\min\\bigl(|x-a_i|, |x-b_i|\\bigr)"))
        assertEquals("max(a, b)", t("\\max\\Bigl(a, b\\Bigr)"))
        assertEquals("(a+b)", t("\\left(a+b\\right)"))
        assertEquals("∑", t("\\displaystyle\\sum").replace("Σ", "∑"))
    }

    @Test fun argumentsAndStructures() {
        assertEquals("C(n, k)", t("\\binom{n}{k}"))
        assertEquals("(a)/(b)", t("\\dfrac{a}{b}"))
        assertEquals("<sup>3</sup>√(x)", t("\\sqrt[3]{x}"))
        assertEquals("a ≡ b (mod m)", t("a \\equiv b \\pmod{m}"))
        assertEquals("lcm(a, b)", t("\\operatorname{lcm}(a, b)"))
        assertEquals("x ∈ Z", t("x \\in \\mathbb{Z}"))
        assertEquals("2<sup>n-1</sup>", t("2^{n-1}"))
        assertEquals("a<sub>i,j</sub>", t("a_{i,j}"))
        assertEquals("f(x) = 1 x>0 0 otherwise", t("f(x) = \\begin{cases} 1 &amp; x>0 \\\\ 0 &amp; \\text{otherwise} \\end{cases}"))
    }

    @Test fun symbolsAndEscapes() {
        assertEquals("{1, 2, …, n}", t("\\{1, 2, \\ldots, n\\}"))
        assertEquals("| a | ⊕ ‖ b ‖", t("\\lvert a \\rvert \\oplus \\lVert b \\rVert"))
        assertEquals("p ⇒ q ⇔ r", t("p \\Rightarrow q \\iff r"))
        assertEquals("α ≠ β, θ ≈ φ", t("\\alpha \\neq \\beta, \\theta \\approx \\varphi"))
        assertEquals("a ≤ b ≪ c", t("a \\le b \\ll c"))
        assertEquals("50% &amp; more", t("50\\% \\&amp; more"))
        assertEquals("⌊ n/2 ⌋", t("\\lfloor n/2 \\rfloor"))
        assertFalse(t("\\left\\lfloor x \\right\\rfloor").contains("left"))
    }

    @Test fun keepsTheTexForKatex() {
        val html = CodeforcesApi.tex("<p>Let \$\$\$a_i \\lt \"b\"\$\$\$ and \$\$\$\$\$\$\\sum x\$\$\$\$\$\$</p>")
        assertTrue(html, html.contains("<span class=\"math\" data-tex=\"a_i \\lt &quot;b&quot;\">"))
        assertTrue(html, html.contains("<div class=\"math\" data-tex=\"\\sum x\">Σ x</div>"))
    }

    @Test fun oldStatementsWithMathsAreReparsed() {
        assertTrue(CodeforcesApi.needsReparse("""{"stdin":true}""", "<p><span class=\"math\">x</span></p>"))
        assertFalse(CodeforcesApi.needsReparse("""{"stdin":true}""", "<p>no maths</p>"))
        assertFalse(CodeforcesApi.needsReparse("""{"parser":${CodeforcesApi.PARSER}}""", "<span class=\"math\" data-tex=\"x\">x</span>"))
    }
}
