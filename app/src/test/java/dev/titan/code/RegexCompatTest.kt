package dev.titan.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rev 33: unit tests run on the desktop JVM, but the phone's `java.util.regex` is ICU, which is
 * stricter. `[^{}]` compiled here and threw `PatternSyntaxException` on the phone (Codeforces
 * maths in editorials and statements): inside `[...]`, ICU reads `{…}` as a string. This scans
 * every `Regex(…)` literal in the app's sources (with `$CONST` templates filled from the same
 * file) for what ICU rejects or reads differently.
 */
class RegexCompatTest {

    @Test fun appRegexesSuitAndroid() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val problems = mutableListOf<String>()
        var checked = 0
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            val consts = Regex("""const val (\w+) = "((?:[^"\\]|\\.)*)"""").findAll(src).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            for (lit in literals(src)) {
                val pattern = Regex("""\$(\w+)""").replace(lit) { consts[it.groupValues[1]] ?: "" }
                checked++
                icuProblem(pattern)?.let { problems += "${f.name}: $it in /$pattern/" }
            }
        }
        assertTrue("found no regexes to check", checked > 50)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun checkerCatchesWhatTheEngineRejects() {
        assertEquals("'{' or '}' inside [...]", icuProblem("\\{([^{}]*)}")?.substringBefore(" at"))
        assertEquals(null, icuProblem("\\{([^\\{\\}]*)\\}"))
        assertEquals(null, icuProblem("a{2,3}b{4}c{1,}"))
        assertTrue(icuProblem("a}")!!.startsWith("unescaped '}'"))
        assertTrue(icuProblem("x{y")!!.startsWith("'{' that isn't a repeat count"))
        assertTrue(icuProblem("(?iu)x")!!.startsWith("inline flag"))
        assertEquals(null, icuProblem("(?is)x\\Q{}[\\E"))
    }

    /** The patterns passed to `Regex(…)` as string literals, Kotlin escapes resolved. */
    private fun literals(src: String): List<String> {
        val out = mutableListOf<String>()
        Regex("""Regex\(\s*"\"\"(.*?)"\"\"""", RegexOption.DOT_MATCHES_ALL).findAll(src).forEach { out += it.groupValues[1] }
        Regex("""Regex\(\s*"((?:[^"\\]|\\.)*)"""").findAll(src).forEach { m ->
            if (!m.value.contains("\"\"\"")) out += unescape(m.groupValues[1])
        }
        return out
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(when (val n = s[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> n })
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    /** The first thing in [p] that ICU rejects or reads differently from the JVM, or null. */
    private fun icuProblem(p: String): String? {
        var i = 0
        var inClass = false
        while (i < p.length) {
            val c = p[i]
            when {
                c == '\\' && p.startsWith("\\Q", i) -> { val e = p.indexOf("\\E", i + 2); i = if (e < 0) p.length else e + 2; continue }
                c == '\\' -> { i += 2; continue }
                inClass && c == ']' -> inClass = false
                inClass && (c == '{' || c == '}') -> return "'{' or '}' inside [...] at $i"
                inClass -> Unit
                c == '[' -> { inClass = true; if (p.getOrNull(i + 1) == '^') i++; if (p.getOrNull(i + 1) == ']') i++ }
                c == '{' -> {
                    val m = Regex("""\{\d+(,\d*)?}""").matchAt(p, i) ?: return "'{' that isn't a repeat count at $i"
                    i += m.value.length; continue
                }
                c == '}' -> return "unescaped '}' at $i"
                c == '(' && p.startsWith("(?", i) -> {
                    val flags = Regex("""\(\?([a-zA-Z-]+)[:)]""").matchAt(p, i)?.groupValues?.get(1)
                    if (flags != null && flags.any { it !in "imsxw-" }) return "inline flag (?$flags) at $i"
                }
            }
            i++
        }
        return null
    }
}
