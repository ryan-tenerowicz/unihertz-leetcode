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

    // ---- rev 38: Jedi ----------------------------------------------------------------

    @Test fun completionSite() {
        assertEquals(dev.titan.code.editor.CompletionSite("", true, 0, 10, "list_name", ""), ed("list_name.|").second.completionSite())
        assertEquals(dev.titan.code.editor.CompletionSite("ap", true, 0, 7, "nums", ")"), ed("nums.ap|)").second.completionSite())
        assertEquals(dev.titan.code.editor.CompletionSite("x", false, 0, 5, "y = ", ""), ed("y = x|").second.completionSite())
        assertEquals(null, ed("y = |").second.completionSite())
        assertEquals(null, ed("y = 1.|").second.completionSite())
        assertEquals(null, ed("s = 'a.|").second.completionSite())
        assertEquals(null, ed("# nums.|").second.completionSite())
        assertEquals(null, ed("nums.ap|pend").second.completionSite())
    }

    @Test fun bracketsForCalls() {
        fun ins(line: String, name: String, kind: Completer.Kind, args: Int?): String {
            val site = ed(line).second.completionSite()!!
            return Completer.insertFor(Completer.Item(name, name, kind, args), site)
        }
        val F = Completer.Kind.FUNCTION
        val C = Completer.Kind.CLASS
        assertEquals("append(\$0)", ins("nums.ap|", "append", F, 1))
        assertEquals("pop(\$0)", ins("nums.p|", "pop", F, 1))
        assertEquals("clear()\$0", ins("seen.cl|", "clear", F, 0))
        assertEquals("heappush(\$0)", ins("    heap|", "heappush", F, null))
        assertEquals("Counter(\$0)", ins("c = Cou|", "Counter", C, 1))
        assertEquals("deque()\$0", ins("q = de|", "deque", C, 0))
        // A bare name where it's not a call.
        assertEquals("append", ins("nums.ap|(x)", "append", F, 1))
        assertEquals("cache", ins("@ca|", "cache", F, 1))
        assertEquals("len", ins("nums.sort(key=le|)", "len", F, 1))
        assertEquals("int", ins("a = list(map(in|", "int", C, 1))
        assertEquals("ListNode", ins("    def f(self, head: Optional[ListN|", "ListNode", C, 2))
        assertEquals("ListNode", ins("    def f(self) -> ListN|", "ListNode", C, 2))
        assertEquals("TreeNode", ins("    def f(self, root: TreeN|", "TreeNode", C, 3))
        assertEquals("int", ins("    d: Dict[str, in|", "int", C, 1))
        assertEquals("ValueError", ins("except Valu|", "ValueError", C, 1))
        assertEquals("ListNode", ins("if isinstance(x, ListN|", "ListNode", C, 2))
        assertEquals("deque", ins("from collections import de|", "deque", C, 0))
        assertEquals("val", ins("node.va|", "val", Completer.Kind.VALUE, null))
    }

    @Test fun completesWithCursorInsideBrackets() {
        val (b, e) = ed("nums.ap|")
        e.complete(2, "append(\$0)")
        assertEquals("nums.append(|)", b.show())
    }

    @Test fun parsesJedi() {
        val items = Completer.parse("""{"items":[{"name":"append","type":"function","args":1},{"name":"val","type":"statement"}],"ms":40}""")!!
        assertEquals(listOf("append", "val"), items.map { it.label })
        assertEquals(listOf(Completer.Kind.FUNCTION, Completer.Kind.VALUE), items.map { it.kind })
        assertEquals(listOf(1, null), items.map { it.args })
        assertEquals(null, Completer.parse("""{"items":[],"error":"not ready"}"""))
        assertEquals(null, Completer.parse("garbage"))
    }

    @Test fun mergesJediWithBufferWords() {
        val jedi = listOf(Completer.Item("height", "height", Completer.Kind.VALUE))
        val plain = dev.titan.code.editor.CompletionSite("he", false, 0, 2)
        assertEquals(listOf("height", "helper"), Completer.merge(jedi, plain, setOf("helper", "height", "he")).map { it.label })
        val dot = dev.titan.code.editor.CompletionSite("", true, 0, 5)
        assertEquals(listOf("height"), Completer.merge(jedi, dot, setOf("helper")).map { it.label })
    }
}
