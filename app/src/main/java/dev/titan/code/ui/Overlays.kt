package dev.titan.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titan.code.input.InputRouter
import dev.titan.code.input.Leader

/** Symbols the key map lacks, in the `fn` palette (PLAN §5.3). */
val PALETTE_SYMBOLS = listOf("=", "(", ")", "[", "]", "{", "}", "<", ">", "|", ";", "~", "%")

/** The `fn` symbol palette: a compact strip above the status line (PLAN §5.3). */
@Composable
fun PaletteStrip(mono: FontFamily, onSymbol: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Colors.panel).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PALETTE_SYMBOLS.forEach { s ->
            Box(
                Modifier.weight(1f).height(40.dp)
                    .border(1.dp, Colors.divider, RoundedCornerShape(5.dp))
                    .clickable { onSymbol(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s, color = Colors.accent, fontSize = 18.sp, fontFamily = mono)
            }
        }
    }
}

/**
 * The completion strip (PLAN §5.4, M5): above the status line, the first item highlighted.
 * `sym+Space` accepts the highlighted item; tap accepts any.
 */
@Composable
fun CompletionStrip(mono: FontFamily, items: List<dev.titan.code.editor.Completer.Item>, onPick: (dev.titan.code.editor.Completer.Item) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Colors.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            Box(
                Modifier.height(34.dp)
                    .border(1.dp, if (i == 0) Colors.accent else Colors.divider, RoundedCornerShape(5.dp))
                    .clickable { onPick(item) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, color = Colors.text, fontSize = 14.sp, fontFamily = mono, maxLines = 1)
                    // Rev 38: methods and functions show "()" so a name's kind is visible at a glance.
                    if (item.kind == dev.titan.code.editor.Completer.Kind.FUNCTION) {
                        Text("()", color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
                    }
                }
            }
        }
        Text("sym+Space", color = Colors.dim, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** `sym+H`: generated from the keymap, so it can't drift from the real bindings (PLAN §5.4). */
@Composable
fun CheatSheet(mono: FontFamily, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Colors.background)
            .clickable(onClick = onClose)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Heading("Hold sym + key")
        Leader.entries.forEach { Row2(mono, "sym+${it.keyLabel}", it.label) }
        Heading("Alt+Shift (on the 4–7 keys)")
        InputRouter.ALT_SHIFT.forEach { (code, sym) ->
            val key = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            Row2(mono, "Alt+Shift+$key", sym)
        }
        Heading("fn: symbol palette (tap)")
        Text(PALETTE_SYMBOLS.joinToString("  "), color = Colors.text, fontSize = 15.sp, fontFamily = mono)
        Heading("Completion")
        Row2(mono, "sym+Space", "Take the first suggestion (or indent)")
        Heading("Other keys")
        Row2(mono, "Shift+Backspace", "Delete forward")
        Row2(mono, "Red button", "Problem ↔ Code")
        Row2(mono, "Back", "Close overlay; post → list; Results/Comments → Code; statement ↔ list")
        Row2(mono, "Swipe on keys", "Move cursor (Shift: select)")
        Text("Back, sym+H or a tap closes this.", color = Colors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text, color = Colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun Row2(mono: FontFamily, key: String, label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(key, color = Colors.text, fontSize = 13.sp, fontFamily = mono, modifier = Modifier.width(150.dp))
        Text(label, color = Colors.dim, fontSize = 13.sp)
    }
}
