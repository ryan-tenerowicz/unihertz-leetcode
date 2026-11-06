package dev.titan.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titan.code.data.Solution

/**
 * The Solutions tab (owner, rev 10): one row per post, like LeetCode's list: title, author,
 * votes, comment count and a short summary. Tap, or highlight with swipes/arrows and press
 * Enter, to open the post.
 */
@Composable
fun SolutionList(
    solutions: List<Solution>,
    highlight: Int,
    status: String?,
    mono: FontFamily,
    onOpen: (Solution) -> Unit,
    /** Reading size (rev 28): the whole list scales together. */
    textScale: Float = 1f,
) = ReadingScale(textScale) {
    Column(Modifier.fillMaxSize().background(Colors.background)) {
        status?.let {
            Text(it, color = Colors.accent, fontSize = 12.sp, fontFamily = mono, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        if (solutions.isEmpty()) {
            if (status == null) Text("No Python solutions saved for this problem.", color = Colors.dim, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            return@Column
        }
        val state = rememberLazyListState()
        LaunchedEffect(highlight) {
            val visible = state.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (highlight < first || highlight > last - 1) state.scrollToItem((highlight - 1).coerceAtLeast(0))
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(solutions, key = { _, s -> s.topicId }) { i, s ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (i == highlight) Colors.panel else Colors.background)
                        .clickable { onOpen(s) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(s.title, color = Colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        // NeetCode's article (topicId 0, rev 13) has no votes or comments.
                        (if (s.topicId == 0L || s.html) s.author else "${s.author} · ▲ ${s.votes} · 💬 ${s.commentCount}") + if (s.body != null) " · ⤓" else "",
                        color = Colors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                    if (s.summary.isNotEmpty()) {
                        Text(s.summary, color = Colors.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Colors.divider))
            }
        }
    }
}
