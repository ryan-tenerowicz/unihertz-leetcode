package dev.titan.code

import dev.titan.code.editor.Completer
import dev.titan.code.editor.PyEdit
import dev.titan.code.editor.Pos
import org.junit.Assert.assertEquals
import org.junit.Test

class CompleterTest {
    private fun ed(text: String) = FakeBuffer(text).let { it to PyEdit(it) }

    @Test fun ordersBufferThenNames() {
        val items = Completer.suggest("de", setOf("depth", "dest_node"))
        assertEquals(listOf("depth", "dest_node", "def", "del", "deque", "defaultdict"), items.map { it.label })
        assertEquals(Completer.Kind.NAME, Completer.suggest("bf", emptySet()).firstOrNull()?.kind ?: Completer.Kind.NAME)
    }

    @Test fun noSnippets() = assertEquals(emptyList<String>(), Completer.suggest("bfs", emptySet()).map { it.label })

    @Test fun needsTwoCharacters() = assertEquals(0, Completer.suggest("d", setOf("depth")).size)

    @Test fun skipsTheWordItself() = assertEquals(listOf("heappop", "heappush"), Completer.suggest("heapp", setOf("heapp")).map { it.label }.take(2))

    @Test fun completesWord() {
        val (b, e) = ed("x = defa|")
        e.complete(4, "defaultdict")
        assertEquals("x = defaultdict|", b.show())
    }

    @Test fun wordPrefixOnlyInCode() {
        assertEquals("heap", ed("x = heap|").second.wordPrefix())
        assertEquals("", ed("x = 'heap|").second.wordPrefix())
        assertEquals("", ed("x = 12|").second.wordPrefix())
        assertEquals("", ed("x = he|ap").second.wordPrefix())
    }

    @Test fun wordsFromBuffer() {
        val (_, e) = ed("seen = set()\nfor node in seen: x|")
        assertEquals(setOf("seen", "set", "for", "node"), e.words())
    }
}
