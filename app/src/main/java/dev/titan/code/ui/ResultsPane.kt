package dev.titan.code.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titan.code.data.SubmissionRecord
import dev.titan.code.data.TestCase
import dev.titan.code.data.leetcode.Judge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Results pane shows at the top (PLAN §4.1 Results, M3). */
sealed class Outcome {
    abstract val kind: String
    data class Running(override val kind: String, val text: String) : Outcome()
    data class Ran(val result: Judge.RunResult) : Outcome() { override val kind get() = result.source }
    data class Submitted(val result: Judge.SubmitResult) : Outcome() { override val kind get() = result.source }
    /** Codeforces reports a verdict per test number, not per case (rev 13). */
    data class CfSubmitted(val verdict: dev.titan.code.data.codeforces.CodeforcesApi.Verdict, val url: String) : Outcome() { override val kind = "Submit to Codeforces" }
    data class Failed(override val kind: String, val message: String) : Outcome()
    /** A stress test against a reference solution (rev 24). */
    data class Stressed(val stress: dev.titan.code.runner.LocalJudge.Stress) : Outcome() { override val kind = "Stress test" }

    /** A short label for the Results tab, e.g. "✓ 3/3" (PLAN §4.1). */
    val badge: String?
        get() = when (this) {
            is Running -> "…"
            is Ran -> if (result.status == Judge.Status.ACCEPTED && result.cases.all { it.passed || it.expected == null }) "✓" else "✗"
            is Submitted -> if (result.accepted) "✓" else "✗"
            is CfSubmitted -> if (verdict.accepted) "✓" else "✗"
            is Failed -> "!"
            is Stressed -> when { stress.error != null -> "!"; stress.mismatch != null || stress.big?.error != null -> "✗"; else -> "✓" }
        }
}

@Composable
fun ResultsPane(
    platform: dev.titan.code.data.Platform,
    outcome: Outcome?,
    cases: List<TestCase>,
    history: List<SubmissionRecord>,
    scroll: ScrollState,
    mono: FontFamily,
    onDeleteCase: (TestCase) -> Unit,
    onAddFromClipboard: () -> Unit,
    onGoToLine: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onStress: () -> Unit = {},
    onSaveStressCase: (dev.titan.code.runner.LocalJudge.Mismatch) -> Unit = {},
    onEditCase: (TestCase?) -> Unit = {},
) {
    val cf = platform == dev.titan.code.data.Platform.CODEFORCES
    Column(
        Modifier.fillMaxSize().background(Colors.background).verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        when (outcome) {
            null -> Dim(
                if (cf) "Nothing run yet. sym+R (▶ Run) runs the samples below on the phone, offline, feeding each one to your program's input; sym+S twice submits to Codeforces."
                else "Nothing run yet. sym+R (▶ Run) runs the cases below locally, offline; sym+E (long-press Run) runs them on ${platform.title}; sym+S twice submits."
            )
            is Outcome.Running -> {
                Header(outcome.kind, Colors.accent, mono)
                Dim(outcome.text)
            }
            is Outcome.Failed -> {
                Header(outcome.kind, Colors.bad, mono)
                Text(outcome.message, color = Colors.text, fontSize = 14.sp)
            }
            is Outcome.Ran -> RunView(outcome.result, mono, onGoToLine)
            is Outcome.Submitted -> SubmitView(outcome.result, mono)
            is Outcome.CfSubmitted -> CfSubmitView(outcome, mono, onOpenUrl)
            is Outcome.Stressed -> StressView(outcome.stress, mono, onGoToLine, onSaveStressCase)
        }
        if (!cf && outcome !is Outcome.Running) {
            Button("⚡ Stress test: compare with a reference solution on random inputs", onStress)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Section("Test cases (${cases.size})") }
            Text(
                "＋ New test", color = Colors.accent, fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, end = 6.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).clickable { onEditCase(null) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
            Text(
                "＋ From clipboard", color = Colors.accent, fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).clickable(onClick = onAddFromClipboard)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        if (cases.isEmpty()) Dim("No examples found for this problem.")
        Dim("＋ New test (or ✎ on a case) opens it in the editor, one section per input; sym+S saves it, Back cancels.")
        cases.forEachIndexed { i, c ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${i + 1} · " + when (c.source) { "example" -> "example"; "custom" -> "yours"; else -> "from a failed submission" } +
                            if (c.verified) " · expected ✓" else "",
                        color = Colors.dim, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Text("✎", color = Colors.dim, fontSize = 16.sp, modifier = Modifier.clickable { onEditCase(c) }.padding(horizontal = 8.dp))
                    if (c.id != null) {
                        Text("✕", color = Colors.dim, fontSize = 16.sp, modifier = Modifier.clickable { onDeleteCase(c) }.padding(horizontal = 8.dp))
                    }
                }
                Mono(c.input.trimEnd('\n'), mono, Colors.text)
                if (c.expected != null) Labeled(
                    when { cf && c.source == "example" -> "Expected (sample output)"; c.verified -> "Expected (${platform.title})"
                        c.source == "custom" -> "Expected (yours, not checked by the judge)"; else -> "Expected (from the statement)" },
                    c.expected.trimEnd('\n'), mono, Colors.dim,
                )
            }
        }

        if (history.isNotEmpty()) {
            Section("Submissions")
            val fmt = SimpleDateFormat("d MMM HH:mm", Locale.US)
            history.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(fmt.format(Date(h.at)), color = Colors.dim, fontSize = 12.sp, fontFamily = mono, modifier = Modifier.width(110.dp))
                    Text(h.status, color = if (h.status == "Accepted") Colors.good else Colors.bad, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${h.passed} · ${h.runtime}" + (h.elapsedMs?.let { " · ⏱ " + dev.titan.code.data.PracticeTimer.format(it) } ?: ""),
                        color = Colors.dim, fontSize = 12.sp, fontFamily = mono)
                }
            }
        }
        Dim("Swipe on the keys to scroll. sym+T or Back returns.")
    }
}

@Composable
private fun RunView(r: Judge.RunResult, mono: FontFamily, onGoToLine: (Int) -> Unit) {
    val unchecked = r.cases.count { it.expected == null }
    val verdict = if (r.verdict == "Accepted" && unchecked > 0 && r.status == Judge.Status.ACCEPTED && r.source == "Run locally") "Ran" else r.verdict
    val stdin = r.cases.any { it.input.contains('\n') && it.output?.endsWith("\n") == true }
    Header(verdict, if (r.verdict == "Accepted" || verdict == "Ran") Colors.good else Colors.bad, mono)
    Dim("${r.source} · ${r.passedCount}/${r.cases.size} passed" + listOf(r.runtime, r.memory).filter(String::isNotEmpty).joinToString("") { " · $it" } +
        if (unchecked > 0) " · $unchecked without an expected output" else "")
    r.error?.let { err ->
        ErrorBox(err, mono)
        dev.titan.code.runner.LocalJudge.errorLine(err)?.let { line ->
            Text(
                "Go to line $line", color = Colors.accent, fontSize = 13.sp,
                modifier = Modifier.border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).clickable { onGoToLine(line) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    if (r.status == Judge.Status.COMPILE_ERROR || r.statusMsg == "Not supported locally") return
    r.cases.forEach { c ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, if (c.passed) Colors.divider else Colors.bad, RoundedCornerShape(6.dp)).padding(8.dp)) {
            val mark = when { c.passed -> "✓"; c.expected == null && c.error == null && c.output != null -> "·"; else -> "✗" }
            Text("Case ${c.index + 1} $mark" + (c.ms?.let { "  ${"%.1f".format(it)} ms" } ?: ""),
                color = if (mark == "✗") Colors.bad else Colors.good, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            c.error?.let { Labeled("Error", it, mono, Colors.bad) }
            Labeled("Input", c.input.trimEnd('\n'), mono, Colors.text)
            Labeled("Output", c.output?.trimEnd('\n') ?: "—", mono, if (c.passed) Colors.text else Colors.bad)
            Labeled("Expected", c.expected?.trimEnd('\n') ?: "—", mono, Colors.text)
            c.note?.let { Labeled("Note", it, mono, if (c.passed) Colors.dim else Colors.bad) }
            if (c.stdout.isNotBlank()) Labeled("Stdout", c.stdout.trimEnd(), mono, Colors.dim)
        }
    }
    if (stdin && r.cases.any { !it.passed && it.error == null && it.expected != null }) {
        Dim("Compared word by word, ignoring spacing. Problems that accept several answers can still pass on Codeforces.")
    }
}

/** A stress test's result (rev 24): the first difference, the speed check, and what was assumed. */
@Composable
private fun StressView(s: dev.titan.code.runner.LocalJudge.Stress, mono: FontFamily, onGoToLine: (Int) -> Unit,
                       onSave: (dev.titan.code.runner.LocalJudge.Mismatch) -> Unit) {
    if (s.error != null) {
        Header("Can't stress test", Colors.bad, mono)
        Text(s.error, color = Colors.text, fontSize = 14.sp)
        return
    }
    val m = s.mismatch
    val slow = s.big?.error != null
    val tle = m?.error?.contains("Time Limit Exceeded") == true
    Header(when { tle -> "Too slow"; m != null -> "Different answer"; slow -> "Too slow on a big input"; else -> "No differences found" },
        if (m == null && !slow) Colors.good else Colors.bad, mono)
    Dim("Stress test · ${s.tried} random inputs in ${"%.1f".format(s.seconds)} s" +
        (if (s.skipped > 0) " (${s.skipped} skipped: the reference rejected them)" else "") + " · compared with ${s.reference}")
    if (m != null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Colors.bad, RoundedCornerShape(6.dp)).padding(8.dp)) {
            Labeled("Input", m.input, mono, Colors.text)
            if (m.error != null) {
                Labeled("Your code", m.error, mono, Colors.bad)
                dev.titan.code.runner.LocalJudge.errorLine(m.error)?.let { line -> Button("Go to line $line") { onGoToLine(line) } }
            } else {
                Labeled("Your output", m.output ?: "—", mono, Colors.bad)
            }
            Labeled("Reference output", m.expected ?: "—", mono, Colors.text)
            m.note?.let { Labeled("Note", it, mono, Colors.dim) }
            Button("＋ Save as a test case (expected: the reference's output)") { onSave(m) }
        }
    }
    s.big?.let { b ->
        Section("Speed (${b.size})")
        if (b.error != null) {
            ErrorBox(b.error, mono)
            dev.titan.code.runner.LocalJudge.errorLine(b.error)?.let { line -> Button("Go to line $line") { onGoToLine(line) } }
        } else {
            Dim("Yours: ${b.ms?.let { "%.0f ms".format(it) } ?: "—"} · reference: ${b.refMs?.let { "%.0f ms".format(it) } ?: "too slow or failed"}. " +
                "The phone is slower than the judge, so compare the two rather than the absolute time.")
        }
    }
    s.warnings.forEach { Dim("⚠ $it") }
    if (s.assumptions.isNotEmpty()) {
        Section("Inputs generated")
        s.assumptions.forEach { Mono(it, mono, Colors.dim) }
        Dim("Small inputs first (up to 8 elements, values within ±10 where allowed), then one at full size.")
    }
}

@Composable
private fun Button(text: String, onClick: () -> Unit) =
    Text(
        text, color = Colors.accent, fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 4.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )

/** Codeforces' verdict (rev 13): "Wrong answer on test 3", time and memory; the test itself isn't in the API. */
@Composable
private fun CfSubmitView(o: Outcome.CfSubmitted, mono: FontFamily, onOpenUrl: (String) -> Unit) {
    val v = o.verdict
    Header(v.label, if (v.accepted) Colors.good else Colors.bad, mono)
    Dim("Codeforces · ${v.language} · ${v.time} · ${v.memory}" + if (v.accepted) "" else " · passed ${v.passedTests} tests")
    if (!v.accepted) Dim("Codeforces doesn't share the failing test through its API. Open the submission to see the tests it shows.")
    Text(
        "Open submission #${v.id} on Codeforces", color = Colors.accent, fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 4.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).clickable { onOpenUrl(o.url) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SubmitView(r: Judge.SubmitResult, mono: FontFamily) {
    Header(r.statusMsg, if (r.accepted) Colors.good else Colors.bad, mono)
    val beats = { p: Double? -> if (p == null) "" else " (beats ${"%.0f".format(p)}%)" }
    Dim("${r.source} · ${r.totalCorrect}/${r.totalTestcases} test cases" +
        (if (r.runtime.isNotEmpty() && r.runtime != "N/A") " · ${r.runtime}${beats(r.runtimePercentile)}" else "") +
        (if (r.memory.isNotEmpty() && r.memory != "N/A") " · ${r.memory}${beats(r.memoryPercentile)}" else ""))
    r.error?.let { ErrorBox(it, mono) }
    if (!r.accepted && r.lastInput != null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Colors.bad, RoundedCornerShape(6.dp)).padding(8.dp)) {
            Text("Failing case (saved to your test cases)", color = Colors.bad, fontSize = 13.sp)
            Labeled("Input", r.lastInput, mono, Colors.text)
            r.output?.let { Labeled("Output", it, mono, Colors.bad) }
            r.expected?.let { Labeled("Expected", it, mono, Colors.text) }
            if (r.stdout.isNotBlank()) Labeled("Stdout", r.stdout.trimEnd(), mono, Colors.dim)
        }
    }
}

@Composable
private fun Header(text: String, color: Color, mono: FontFamily) =
    Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))

@Composable
private fun Section(text: String) =
    Text(text, color = Colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

@Composable
private fun Dim(text: String) = Text(text, color = Colors.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun Mono(text: String, mono: FontFamily, color: Color) =
    Text(text, color = color, fontSize = 13.sp, fontFamily = mono, modifier = Modifier.padding(top = 2.dp))

@Composable
private fun Labeled(label: String, value: String, mono: FontFamily, color: Color) {
    Text(label, color = Colors.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    Box(Modifier.fillMaxWidth().background(Colors.panel, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(value, color = color, fontSize = 13.sp, fontFamily = mono)
    }
}

@Composable
private fun ErrorBox(text: String, mono: FontFamily) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, Colors.bad, RoundedCornerShape(6.dp)).padding(8.dp)) {
        Text(text, color = Colors.bad, fontSize = 12.sp, fontFamily = mono)
    }
}
