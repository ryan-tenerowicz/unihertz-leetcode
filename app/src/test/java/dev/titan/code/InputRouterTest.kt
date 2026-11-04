package dev.titan.code

import android.view.KeyEvent
import dev.titan.code.editor.Clip
import dev.titan.code.editor.EditorActions
import dev.titan.code.input.InputRouter
import dev.titan.code.input.InputRouter.Key
import dev.titan.code.input.Leader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The KeymapEngine tests of PLAN §10: routing, Alt+Shift symbols, leader commands, key repeat. */
class InputRouterTest {

    private val log = mutableListOf<String>()

    private val actions = object : EditorActions {
        override fun insert(text: String) { log += "insert $text" }
        override fun insertPlain(text: String) { log += "plain $text" }
        override fun backspace() { log += "backspace" }
        override fun deleteForward() { log += "deleteForward" }
        override fun newline() { log += "newline" }
        override fun indent() { log += "indent" }
        override fun move(direction: EditorActions.Direction, extend: Boolean, withinLine: Boolean) { log += "move $direction $extend" }
        override fun saveSelection() = EditorActions.Mark(0, 0, 0, 0, 0, 0)
        override fun restoreSelection(mark: EditorActions.Mark) {}
        override fun undo() { log += "undo" }
        override fun redo() { log += "redo" }
        override fun selectLine() { log += "selectLine" }
        override fun deleteLine() { log += "deleteLine" }
        override fun duplicateLine() { log += "duplicateLine" }
        override fun toggleComment() { log += "toggleComment" }
        override fun copy() = Clip("x\n", true).also { log += "copy" }
        override fun cut() = Clip("x\n", true).also { log += "cut" }
        override fun paste(clip: Clip) { log += "paste ${clip.text.trim()}" }
        override fun toggleSoftWrap() = true.also { log += "wrap" }
        override var indentWidth = 4
    }

    private var palette = false

    private val shell = object : InputRouter.Shell {
        override fun onBack() { log += "back" }
        override fun onLeader(command: Leader) { log += "leader $command" }
        override fun onSymHeld(held: Boolean) {}
        override fun onSoftWrapChanged(on: Boolean) {}
        override fun onFn() { palette = !palette; log += "fn" }
        override fun closePalette() = palette.also { palette = false }
        override fun closeCheatSheet() = false
        override var clip: Clip? = null
        override val textInput get() = text
        override fun onTypingKey(code: Int, down: Boolean, timeMs: Long) { typing += "$code ${if (down) "down" else "up"}" }
    }
    private val typing = mutableListOf<String>()

    private var text: InputRouter.TextInput? = null

    /** A fake clock: tasks run when [advance] passes their time. */
    private var now = 0L
    private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
    private val scheduler = InputRouter.Scheduler { delay, task ->
        val entry = (now + delay) to task
        tasks += entry
        ({ tasks.remove(entry) })
    }
    private fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { it.first <= end }.minByOrNull { it.first } ?: break
            tasks.remove(next)
            now = next.first
            next.second()
        }
        now = end
    }

    private val router = InputRouter(actions, shell, scheduler)

    private fun press(code: Int, char: Int = 0, shift: Boolean = false, alt: Boolean = false, sym: Boolean = false) {
        router.editorKey(Key(code, true, shift, alt, sym, char))
        router.editorKey(Key(code, false, shift, alt, sym, char))
    }

    @Test fun typingKeysReachTheGestureEngineButShiftAndArrowsDont() {
        router.editorKey(Key(KeyEvent.KEYCODE_SHIFT_LEFT, true, shift = true))
        press(KeyEvent.KEYCODE_A, 'A'.code, shift = true)
        router.editorKey(Key(KeyEvent.KEYCODE_SHIFT_LEFT, false))
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        press(InputRouter.KEYCODE_SWIPE_MARKER)
        press(KeyEvent.KEYCODE_SPACE, ' '.code)
        val a = KeyEvent.KEYCODE_A; val sp = KeyEvent.KEYCODE_SPACE
        assertEquals(listOf("$a down", "$a up", "$sp down", "$sp up"), typing)
    }

    @Test fun lettersAndAltCharactersInsert() {
        press(KeyEvent.KEYCODE_A, 'a'.code)
        press(KeyEvent.KEYCODE_Q, '#'.code, alt = true)
        assertEquals(listOf("insert a", "insert #"), log)
    }

    @Test fun altShiftDigitKeysGiveMissingSymbols() {
        for (code in listOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_Z)) {
            press(code, 0, shift = true, alt = true)
        }
        press(KeyEvent.KEYCODE_Q, 0, shift = true, alt = true) // undefined: dropped
        assertEquals(listOf("insert $", "insert %", "insert ^", "insert &"), log)
    }

    @Test fun altSpaceIsDropped() {
        press(KeyEvent.KEYCODE_SPACE, InputRouter.ALT_SPACE_PRIVATE_USE, alt = true)
        assertTrue(log.isEmpty())
    }

    @Test fun shiftBackspaceDeletesForward() {
        press(KeyEvent.KEYCODE_DEL, shift = true)
        press(KeyEvent.KEYCODE_DEL)
        assertEquals(listOf("deleteForward", "backspace"), log)
    }

    @Test fun everyLeaderFiresExactlyOneAction() {
        for (cmd in Leader.entries) {
            log.clear()
            router.editorKey(Key(KeyEvent.KEYCODE_SYM, true))
            press(cmd.keyCode, 0, sym = true)
            router.editorKey(Key(KeyEvent.KEYCODE_SYM, false))
            if (cmd == Leader.PASTE) assertTrue(log.size <= 1) else assertEquals("$cmd -> $log", 1, log.size)
        }
    }

    @Test fun pasteUsesTheCopiedClip() {
        press(KeyEvent.KEYCODE_C, sym = true)
        press(KeyEvent.KEYCODE_V, sym = true)
        assertEquals(listOf("copy", "paste x"), log)
    }

    @Test fun shellPaneIgnoresEditingKeys() {
        router.shellKey(Key(KeyEvent.KEYCODE_A, true, char = 'a'.code))
        router.shellKey(Key(KeyEvent.KEYCODE_U, true, sym = true))
        router.shellKey(Key(KeyEvent.KEYCODE_T, true, sym = true))
        assertEquals(listOf("leader RESULTS"), log)
    }

    @Test fun systemKeysAndRedButtonPassThrough() {
        assertFalse(router.editorKey(Key(KeyEvent.KEYCODE_HOME, true)))
        assertFalse(router.editorKey(Key(InputRouter.KEYCODE_RED_BUTTON, true)))
        assertTrue(router.editorKey(Key(InputRouter.KEYCODE_SWIPE_MARKER, true)))
    }

    // E13: repeat starts after 400 ms, then every 40 ms, until key-up.
    @Test fun heldKeyRepeats() {
        router.editorKey(Key(KeyEvent.KEYCODE_DEL, true))
        advance(399)
        assertEquals(1, log.size)
        advance(1)
        assertEquals(2, log.size)
        advance(400)
        assertEquals(12, log.size)
        router.editorKey(Key(KeyEvent.KEYCODE_DEL, false))
        advance(1000)
        assertEquals(12, log.size)
    }

    @Test fun symChordsAndEnterDoNotRepeat() {
        router.editorKey(Key(KeyEvent.KEYCODE_D, true, sym = true))
        router.editorKey(Key(KeyEvent.KEYCODE_ENTER, true))
        advance(2000)
        assertEquals(listOf("deleteLine", "newline"), log)
    }

    @Test fun anotherKeyStopsTheRepeat() {
        router.editorKey(Key(KeyEvent.KEYCODE_A, true, char = 'a'.code))
        router.editorKey(Key(KeyEvent.KEYCODE_B, true, char = 'b'.code))
        advance(450)
        assertEquals(listOf("insert a", "insert b", "insert b", "insert b"), log)
    }

    @Test fun typingClosesPaletteAndIsTyped() {
        press(InputRouter.KEYCODE_FN)
        assertTrue(palette)
        press(KeyEvent.KEYCODE_A, 'a'.code)
        assertFalse(palette)
        assertEquals(listOf("fn", "insert a"), log)
    }

    @Test fun shellTextInputTakesKeys() {
        val got = mutableListOf<String>()
        text = object : InputRouter.TextInput {
            override fun type(text: String) { got += text }
            override fun backspace() { got += "<" }
            override fun enter() { got += "enter" }
            override fun move(delta: Int) { got += "move $delta" }
        }
        router.shellKey(Key(KeyEvent.KEYCODE_A, true, char = 'a'.code))
        router.shellKey(Key(KeyEvent.KEYCODE_DEL, true))
        router.shellKey(Key(KeyEvent.KEYCODE_DPAD_DOWN, true))
        router.shellKey(Key(KeyEvent.KEYCODE_ENTER, true))
        router.shellKey(Key(KeyEvent.KEYCODE_SYM, true))
        router.shellKey(Key(KeyEvent.KEYCODE_P, true, sym = true, char = 'p'.code))
        assertEquals(listOf("a", "<", "move 1", "enter"), got)
        assertEquals(listOf("leader PROBLEMS"), log)
    }

    /**
     * Synthetic Cursor Assistant run (PLAN §10): DPAD DOWN/UP pairs from device `touchPad`,
     * 25–85 ms apart, in all four directions. Replace with a real recording when one exists.
     */
    @Test fun cursorAssistantArrowsMoveTheCursor() {
        val run = listOf(
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT,
        )
        val gaps = listOf(25L, 40L, 85L, 60L, 30L, 50L)
        run.forEachIndexed { i, code ->
            router.editorKey(Key(code, true, desc = "touchPad"))
            advance(gaps[i] / 2)
            router.editorKey(Key(code, false, desc = "touchPad"))
            advance(gaps[i] / 2)
        }
        assertEquals(
            listOf("move DOWN false", "move DOWN false", "move RIGHT false", "move UP false", "move LEFT false", "move LEFT false"),
            log,
        )
    }

    @Test fun shiftArrowsExtendSelection() {
        router.editorKey(Key(KeyEvent.KEYCODE_DPAD_RIGHT, true, shift = true))
        assertEquals(listOf("move RIGHT true"), log)
    }
}
