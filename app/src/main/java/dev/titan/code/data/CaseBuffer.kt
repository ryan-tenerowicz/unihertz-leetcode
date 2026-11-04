package dev.titan.code.data

import dev.titan.code.data.neetcode.NeetCodeApi
import org.json.JSONObject

/**
 * The test-case editor's buffer (PLAN §14.2 stage 4, rev 24). A case is edited in the Code pane,
 * with the same keys, palette and swipes as code: one commented section per input, then an
 * optional expected output.
 *
 * ```
 * # Test case for Two Sum: each input as JSON. sym+S saves · Back cancels
 * # nums (integer[])
 * [2,7,11,15]
 * # target (integer)
 * 9
 * # expected output (optional)
 * [0,1]
 * ```
 *
 * Codeforces cases have one free-form section, the program's whole input.
 */
object CaseBuffer {
    data class Section(val name: String, val type: String?)

    sealed class Parsed {
        data class Case(val input: String, val expected: String?) : Parsed()
        /** [line] is 0-based, for putting the cursor on the problem. */
        data class Error(val line: Int, val message: String) : Parsed()
    }

    private const val EXPECTED = "expected output (optional)"

    /** The inputs a case has, in order (design problems: the operations and their arguments). */
    fun sections(d: ProblemDetail): List<Section> {
        if (d.platform == Platform.CODEFORCES) return listOf(Section("input, as the program reads it", null))
        val meta = runCatching { JSONObject(d.metaData) }.getOrDefault(JSONObject())
        if (meta.has("classname")) return listOf(Section("operations", "string[]"), Section("arguments", "list of argument lists"))
        val params = meta.optJSONArray("params")
        val types = (0 until (params?.length() ?: 0)).associate { i ->
            params!!.getJSONObject(i).let { it.optString("name") to it.optString("type") }
        }
        val names = NeetCodeApi.inputNames(meta).ifEmpty { types.keys.toList() }
        return names.map { Section(it, types[it]?.takeIf(String::isNotEmpty)) }
    }

    fun render(d: ProblemDetail, input: String?, expected: String?): String {
        val secs = sections(d)
        val stdin = d.platform == Platform.CODEFORCES
        val values = when {
            input == null -> emptyList()
            stdin -> listOf(input.trimEnd('\n'))
            else -> input.split("\n")
        }
        val sb = StringBuilder()
        sb.append("# Test case for ${d.title}: ")
            .append(if (stdin) "the input exactly as the program reads it." else "each input as JSON, on one line or several.")
            .append(" sym+S saves · Back cancels\n")
        secs.forEachIndexed { i, s ->
            sb.append("# ${s.name}").append(s.type?.let { " ($it)" } ?: "").append('\n')
            sb.append(values.getOrNull(i) ?: "").append('\n')
        }
        sb.append("# $EXPECTED\n")
        sb.append(expected?.trimEnd('\n') ?: "").append('\n')
        return sb.toString()
    }

    /** The line to start editing on: the first input's value. */
    fun firstValueLine(): Int = 2

    fun parse(d: ProblemDetail, text: String): Parsed {
        val secs = sections(d)
        val stdin = d.platform == Platform.CODEFORCES
        val headers = secs.map { "# ${it.name}" } + "# $EXPECTED"
        val lines = text.replace("\r\n", "\n").split("\n")
        val values = Array(headers.size) { mutableListOf<Pair<Int, String>>() }
        var current = -1
        lines.forEachIndexed { n, raw ->
            val l = raw.trimEnd()
            val h = headers.indexOfFirst { l == it || l.startsWith("$it (") }
            when {
                h >= 0 -> current = h
                current < 0 || (l.startsWith("#") && !stdin) -> Unit
                else -> values[current] += n to raw
            }
        }
        if (current < 0) return Parsed.Error(0, "The section headers are gone; press Back and start again.")
        val inputs = mutableListOf<String>()
        secs.forEachIndexed { i, s ->
            val v = values[i]
            if (stdin) {
                val text0 = v.map { it.second }.joinToString("\n").trim('\n')
                if (text0.isBlank()) return Parsed.Error(headerLine(lines, headers[i]) + 1, "The input is empty.")
                inputs += text0 + "\n"
                return@forEachIndexed
            }
            val joined = v.map { it.second.trim() }.filter(String::isNotEmpty).joinToString(" ")
            val at = v.firstOrNull { it.second.isNotBlank() }?.first ?: (headerLine(lines, headers[i]) + 1)
            if (joined.isEmpty()) return Parsed.Error(at, "No value for ${s.name}.")
            Json.check(joined)?.let { return Parsed.Error(at, "${s.name} isn't valid JSON: $it") }
            inputs += joined
        }
        val e = values.last()
        val expected = if (stdin) e.map { it.second }.joinToString("\n").trim('\n').takeIf(String::isNotBlank)
        else e.map { it.second.trim() }.filter(String::isNotEmpty).joinToString(" ").takeIf(String::isNotEmpty)
        if (expected != null && !stdin) {
            Json.check(expected)?.let { return Parsed.Error(e.first { it.second.isNotBlank() }.first, "The expected output isn't valid JSON: $it") }
        }
        return Parsed.Case(inputs.joinToString(if (stdin) "" else "\n"), expected)
    }

    private fun headerLine(lines: List<String>, header: String) = lines.indexOfFirst { it.trimEnd() == header || it.startsWith("$header (") }.coerceAtLeast(0)

    /** A strict JSON check (org.json accepts unquoted strings, which LeetCode doesn't). */
    object Json {
        /** Null if [s] is one valid JSON value; otherwise what's wrong. */
        fun check(s: String): String? {
            val p = P(s)
            return try {
                p.ws(); p.value(); p.ws()
                if (p.i < s.length) "unexpected '${s[p.i]}' at character ${p.i + 1}" else null
            } catch (e: IllegalArgumentException) {
                e.message
            }
        }

        private class P(val s: String) {
            var i = 0
            fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun fail(what: String): Nothing =
                throw IllegalArgumentException(if (i < s.length) "$what at character ${i + 1} ('${s[i]}')" else "$what at the end")
            fun value() {
                if (i >= s.length) fail("a value is missing")
                when (val c = s[i]) {
                    '[' -> list('[', ']') { value() }
                    '{' -> list('{', '}') { str(); ws(); if (i >= s.length || s[i] != ':') fail("':' expected"); i++; ws(); value() }
                    '"' -> str()
                    't' -> word("true")
                    'f' -> word("false")
                    'n' -> word("null")
                    else -> if (c == '-' || c.isDigit()) num() else fail(if (c.isLetter()) "strings need double quotes" else "a value expected")
                }
            }
            fun list(open: Char, close: Char, item: () -> Unit) {
                i++; ws()
                if (i < s.length && s[i] == close) { i++; return }
                while (true) {
                    item(); ws()
                    if (i >= s.length) fail("'$close' missing")
                    when (s[i]) {
                        ',' -> { i++; ws() }
                        close -> { i++; return }
                        else -> fail("',' or '$close' expected")
                    }
                }
            }
            fun str() {
                if (i >= s.length || s[i] != '"') fail("a string expected")
                i++
                while (i < s.length && s[i] != '"') { if (s[i] == '\\') i++; i++ }
                if (i >= s.length) fail("a closing quote is missing")
                i++
            }
            fun word(w: String) { if (!s.startsWith(w, i)) fail("strings need double quotes"); i += w.length }
            fun num() {
                val start = i
                if (s[i] == '-') i++
                while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
                if (s.substring(start, i).toDoubleOrNull() == null) { i = start; fail("not a number") }
            }
        }
    }
}
