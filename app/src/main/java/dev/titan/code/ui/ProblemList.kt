package dev.titan.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titan.code.data.Daily
import dev.titan.code.data.ProblemFilter
import dev.titan.code.data.ProblemSummary

/**
 * The problem list's state (PLAN §4.3). Typing goes to [filter] text; up/down (keyboard-surface
 * swipes or Cursor Assistant arrows) move [highlight]; Enter opens the highlighted row.
 */
class ProblemListModel {
    /** Which site's list this is (rev 13). */
    var platform by mutableStateOf(dev.titan.code.data.Platform.LEETCODE)
    var all by mutableStateOf<List<ProblemSummary>>(emptyList())
    var saved by mutableStateOf<Set<String>>(emptySet())
    var drafts by mutableStateOf<Set<String>>(emptySet())
    var daily by mutableStateOf<Daily?>(null)
    /** Curated lists (NeetCode 150, Blind 75), M6. */
    var collections by mutableStateOf<List<dev.titan.code.data.ProblemCollection>>(emptyList())
    var filter by mutableStateOf(ProblemFilter())
        private set
    var highlight by mutableIntStateOf(0)
        private set
    /** "Downloading list 300 / 3682", an error, or null. */
    var status by mutableStateOf<String?>(null)
    var updatedAt by mutableStateOf(0L)

    /**
     * Rev 22 (owner): problems that need a paid subscription (LeetCode Premium 🔒, NeetCode Pro)
     * are never listed, on any site, since they can't be opened without one.
     */
    val available by derivedStateOf { all.filterNot { it.paidOnly } }
    val shown by derivedStateOf { filter.apply(available, saved) }

    /**
     * A random problem from the current view (rev 22): the site, list, chips and typed filter
     * all apply. Prefers problems not solved yet, unless the view has nothing else.
     */
    fun random(rng: kotlin.random.Random = kotlin.random.Random.Default): ProblemSummary? {
        val pool = shown.filter { ProblemFilter.statusOf(it) != "solved" }.ifEmpty { shown }
        return pool.randomOrNull(rng)
    }

    fun updateFilter(f: ProblemFilter) {
        filter = f
        highlight = 0
    }

    fun move(delta: Int) {
        highlight = (highlight + delta).coerceIn(0, (shown.size - 1).coerceAtLeast(0))
    }

    /** Highlight [slug] if it's in the current view. */
    fun focus(slug: String) {
        val i = shown.indexOfFirst { it.slug == slug }
        if (i >= 0) highlight = i
    }

    fun highlighted(): ProblemSummary? = shown.getOrNull(highlight)

    /** The [n] rows after [slug] in the current view, for save-on-open (PLAN §8.4). */
    fun after(slug: String, n: Int): List<String> {
        val i = shown.indexOfFirst { it.slug == slug }
        return if (i < 0) emptyList() else shown.drop(i + 1).take(n).map { it.slug }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProblemList(
    model: ProblemListModel,
    mono: FontFamily,
    online: Boolean,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlatform: (dev.titan.code.data.Platform) -> Unit,
    onRandom: () -> Unit = {},
    /** Reading size (rev 28): the whole list, site buttons and filters included, scales together. */
    textScale: Float = 1f,
) = ReadingScale(textScale) {
    val shown = model.shown
    Column(Modifier.fillMaxSize().background(Colors.background)) {
        // Rev 13: one list per site; the editor and panes are the same for all three.
        // Rev 28: chip rows wrap when the reading size makes them wider than the screen.
        FlowRow(Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dev.titan.code.data.Platform.entries.forEach { p ->
                Chip(p.title, model.platform == p) { if (model.platform != p) onPlatform(p) }
            }
        }
        // Filter line: what you type shows here (PLAN §4.3).
        Row(
            Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = model.filter.text
            Text(
                if (text.isEmpty()) "Type to filter: words, number or #tag" else "$text▌",
                color = if (text.isEmpty()) Colors.dim else Colors.text, fontSize = 14.sp, fontFamily = mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("${shown.size}/${model.available.size}", color = Colors.dim, fontSize = 12.sp, fontFamily = mono)
            Box(
                Modifier.padding(start = 8.dp).height(30.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp))
                    .clickable(enabled = shown.isNotEmpty(), onClick = onRandom).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("🎲", fontSize = 14.sp) }
            Box(
                Modifier.padding(start = 8.dp).height(30.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp))
                    .clickable(onClick = onRefresh).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("⟳", color = if (online) Colors.text else Colors.dim, fontSize = 15.sp) }
        }
        FilterChips(model)
        model.status?.let {
            Text(it, color = Colors.accent, fontSize = 12.sp, fontFamily = mono, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        model.daily?.takeIf { model.platform == dev.titan.code.data.Platform.LEETCODE }?.let { d ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(d.slug) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daily ${d.date.takeLast(5)}", color = Colors.accent, fontSize = 12.sp, fontFamily = mono, modifier = Modifier.width(96.dp))
                Text("${d.frontendId}. ${d.title}", color = Colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Colors.divider))
        if (model.all.isEmpty()) {
            Text(
                if (!online) "No ${model.platform.title} list saved yet. Connect to the internet and tap ⟳."
                else if (model.platform == dev.titan.code.data.Platform.CODEFORCES) "No Codeforces list saved yet. Tap ⟳ to download it (one request, about 2 MB)."
                else "No problem list saved yet. Tap ⟳ to download it (about 40 requests).",
                color = Colors.dim, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        val listState = rememberLazyListState()
        LaunchedEffect(model.highlight, shown) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (model.highlight < first || model.highlight > last - 1) {
                listState.scrollToItem((model.highlight - 3).coerceAtLeast(0))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(shown, key = { _, p -> p.slug }) { i, p ->
                val c = model.filter.collection
                val pattern = c?.patterns?.get(p.slug)
                if (pattern != null && (i == 0 || c.patterns[shown[i - 1].slug] != pattern)) {
                    Text(pattern, color = Colors.accent, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 12.dp, vertical = 4.dp))
                }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == model.highlight) Colors.panel else Colors.background)
                        .clickable { onOpen(p.slug) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.number, color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1, modifier = Modifier.width(if (model.platform == dev.titan.code.data.Platform.CODEFORCES) 54.dp else 44.dp))
                    Text(
                        (if (p.paidOnly) "🔒 " else "") + p.title,
                        color = if (i == model.highlight) Colors.text else Color2.listText, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    val marks = buildString {
                        if (p.slug in model.drafts) append("✎")
                        if (p.slug in model.saved) append("⤓")
                        when (ProblemFilter.statusOf(p)) { "solved" -> append("✓"); "tried" -> append("◐") }
                    }
                    Text(marks, color = Colors.good, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    Text(
                        shortDifficulty(p.difficulty.ifEmpty { "—" }), fontSize = 11.sp, fontFamily = mono, modifier = Modifier.width(36.dp),
                        color = when (ProblemFilter.bucket(p)) { "Easy" -> Colors.good; "Hard" -> Colors.bad; else -> Colors.accent },
                    )
                }
            }
        }
    }
}

private fun shortDifficulty(d: String) = if (d == "Medium") "Med" else d

private object Color2 { val listText = androidx.compose.ui.graphics.Color(0xFFC8C8C8) }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(model: ProblemListModel) {
    val f = model.filter
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Easy", "Medium", "Hard").forEach { d ->
            Chip(shortDifficulty(d), f.difficulty == d) { model.updateFilter(f.copy(difficulty = if (f.difficulty == d) null else d)) }
        }
        Box(Modifier.width(6.dp))
        listOf("todo" to "Todo", "tried" to "Tried", "solved" to "Solved").forEach { (k, label) ->
            Chip(label, f.status == k) { model.updateFilter(f.copy(status = if (f.status == k) null else k)) }
        }
        Box(Modifier.width(6.dp))
        Chip("Saved", f.savedOnly) { model.updateFilter(f.copy(savedOnly = !f.savedOnly)) }
    }
    if (model.collections.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip("All", f.collection == null) { model.updateFilter(f.copy(collection = null)) }
            model.collections.forEach { c ->
                Chip(c.title, f.collection?.id == c.id) { model.updateFilter(f.copy(collection = if (f.collection?.id == c.id) null else c)) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp)
            .border(1.dp, if (on) Colors.accent else Colors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) Colors.text else Colors.dim, fontSize = 12.sp) }
}
