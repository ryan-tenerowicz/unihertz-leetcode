package dev.titan.code.input

import android.view.KeyEvent
import dev.titan.code.TLog
import dev.titan.code.editor.Clip
import dev.titan.code.editor.EditorActions
import dev.titan.code.editor.EditorActions.Direction

/**
 * Turns hardware keys into editor actions and shell commands (PLAN §5.1–§5.4). Two entry points:
 * - [onEditorKey]: the Code pane, from `TitanEditor.dispatchKeyEventPreIme()`.
 * - [onShellKey]: the Problem, Results and Comments panes, from `MainActivity.dispatchKeyEvent()`.
 *   Only non-editing leader commands and Back work there; every other key is swallowed so it
 *   never reaches a WebView.
 *
 * Both return false only for keys the system owns and for the red button (handled by
 * MainActivity on every pane).
 *
 * The logic works on [Key], a plain snapshot of a KeyEvent, so it runs in JVM unit tests.
 */
class InputRouter(
    private val actions: EditorActions,
    private val shell: Shell,
    private val scheduler: Scheduler,
    private val repeatDelayMs: Long = 400,
    private val repeatIntervalMs: Long = 40,
) {

    /** What the router needs from the app shell. */
    interface Shell {
        /** Back key-up. The shell closes an open overlay first (PLAN §5.1). */
        fun onBack()
        /** A leader command the router doesn't handle itself. */
        fun onLeader(command: Leader)
        fun onSymHeld(held: Boolean)
        fun onSoftWrapChanged(on: Boolean)
        fun onKeyLogged(description: String) {}
        /**
         * A keyboard key went down or up (not Shift, Back, the swipe marker or system keys), so the
         * keyboard-surface gesture engine can tell typing from swiping.
         */
        fun onTypingKey(code: Int, down: Boolean, timeMs: Long) {}
        /** `fn` pressed: open or close the symbol palette. */
        fun onFn()
        /** Close the palette if it's open; true if it was. */
        fun closePalette(): Boolean
        /** Close the cheat sheet if it's open; true if it was. */
        fun closeCheatSheet(): Boolean
        var clip: Clip?
        /** Some editing key reached the buffer (resets pending confirmations like `sym+N`). */
        fun onEdited() {}
        /** `sym+Space` with the completion popup open: accept the highlighted item (PLAN §5.4). */
        fun acceptCompletion(): Boolean = false
        /** A non-editor text target (the problem list's filter) while it's showing; null otherwise. */
        val textInput: TextInput? get() = null
    }

    /** Keys for a non-editor pane that takes typing: characters, Backspace, Enter, up/down. */
    interface TextInput {
        fun type(text: String)
        fun backspace()
        fun enter()
        fun move(delta: Int)
    }

    fun interface Scheduler {
        /** Run [task] after [delayMs]; the returned function cancels it. */
        fun schedule(delayMs: Long, task: () -> Unit): () -> Unit
    }

    /** The parts of a KeyEvent the router uses. */
    data class Key(
        val code: Int,
        val down: Boolean,
        val shift: Boolean = false,
        val alt: Boolean = false,
        val sym: Boolean = false,
        /** `getUnicodeChar(metaState)`: 0 if the key map has no character. */
        val char: Int = 0,
        val canceled: Boolean = false,
        val desc: String = "",
        /** `eventTime`, on the same clock as touch events. */
        val time: Long = 0,
    ) {
        companion object {
            fun from(e: KeyEvent) = Key(
                code = e.keyCode,
                down = e.action == KeyEvent.ACTION_DOWN,
                shift = e.isShiftPressed,
                alt = e.isAltPressed,
                sym = e.isSymPressed,
                char = e.getUnicodeChar(e.metaState),
                canceled = e.isCanceled,
                time = e.eventTime,
                desc = "${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")} " +
                    "meta=0x${Integer.toHexString(e.metaState)} dev=${e.device?.name}",
            )
        }
    }

    private var symHeld = false
    private var repeatingCode = 0
    private var cancelRepeat: (() -> Unit)? = null

    /** Forget held modifiers and stop key repeat (e.g. when the window loses focus mid-chord). */
    fun reset() {
        setSym(false)
        stopRepeat()
    }

    fun onEditorKey(e: KeyEvent) = editorKey(Key.from(e))
    fun onShellKey(e: KeyEvent) = shellKey(Key.from(e))

    fun editorKey(k: Key): Boolean {
        common(k)?.let { return it }
        if (!k.down) {
            if (k.code == repeatingCode) stopRepeat()
            return true
        }
        stopRepeat()

        if (symHeld || k.sym) {
            val command = Leader.forKey(k.code)
            if (command != Leader.CHEAT_SHEET) shell.closeCheatSheet()
            command?.let(::runLeader)
            return true
        }
        if (k.code == KEYCODE_FN) {
            shell.onFn()
            return true
        }
        if (shell.closeCheatSheet()) return true
        // Typing any other key closes the palette and is typed as normal (PLAN §5.3).
        shell.closePalette()

        val repeatable = editorKeyAction(k) ?: return true
        repeatable()
        startRepeat(k.code, repeatable)
        return true
    }

    /**
     * Handles one-shot keys (arrows, Enter) directly and returns null. For repeatable keys
     * (Backspace, forward delete, characters) returns the action, which the caller runs once and
     * then repeats while the key is held.
     */
    private fun editorKeyAction(k: Key): (() -> Unit)? {
        val dir = when (k.code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
            else -> null
        }
        if (dir != null) {
            actions.move(dir, k.shift)
            return null
        }
        when (k.code) {
            KeyEvent.KEYCODE_DEL -> return if (k.shift) edited { actions.deleteForward() } else edited { actions.backspace() }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                edited { actions.newline() }()
                return null
            }
        }
        val text = characterFor(k) ?: return null
        return edited { actions.insert(text) }
    }

    private fun edited(block: () -> Unit): () -> Unit = {
        block()
        shell.onEdited()
    }

    /** The character a key types, or null (PLAN §5.2, §5.3). */
    fun characterFor(k: Key): String? {
        if (k.alt && k.shift) {
            val c = ALT_SHIFT[k.code]
            if (c == null) TLog.d("Alt+Shift+${k.desc} dropped (only S/D/F/Z are defined)")
            return c
        }
        if (k.char <= 0 || k.char == ALT_SPACE_PRIVATE_USE) return null
        if (Character.isISOControl(k.char)) return null
        return String(Character.toChars(k.char))
    }

    fun shellKey(k: Key): Boolean {
        common(k)?.let { return it }
        if (k.down && (symHeld || k.sym)) {
            val command = Leader.forKey(k.code)
            if (command != Leader.CHEAT_SHEET) shell.closeCheatSheet()
            command?.takeIf { !it.editsText }?.let(shell::onLeader)
        } else if (k.down) {
            if (shell.closeCheatSheet()) return true
            shell.textInput?.let { textKey(it, k) }
        }
        return true
    }

    private fun textKey(t: TextInput, k: Key) {
        when (k.code) {
            KeyEvent.KEYCODE_DEL -> t.backspace()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> t.enter()
            KeyEvent.KEYCODE_DPAD_UP -> t.move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> t.move(1)
            else -> characterFor(k)?.let(t::type)
        }
    }

    /** Handling shared by both panes. Returns a result if the key is fully handled here. */
    private fun common(k: Key): Boolean? {
        if (k.down) {
            TLog.d("key ${k.desc}")
            shell.onKeyLogged(k.desc)
        }
        when (k.code) {
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KEYCODE_RED_BUTTON -> return false
        }
        if (k.code !in NOT_TYPING) shell.onTypingKey(k.code, k.down, k.time)
        when (k.code) {
            KeyEvent.KEYCODE_SYM -> {
                setSym(k.down)
                if (k.down) stopRepeat()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!k.down && !k.canceled) shell.onBack()
                return true
            }
            KEYCODE_SWIPE_MARKER -> return true
            // Modifier keys alone do nothing, and don't stop a repeat in progress.
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> return true
        }
        return null
    }

    private fun setSym(held: Boolean) {
        if (symHeld == held) return
        symHeld = held
        shell.onSymHeld(held)
    }

    private fun runLeader(command: Leader) {
        when (command) {
            Leader.UNDO -> actions.undo()
            Leader.REDO -> actions.redo()
            Leader.SOFT_WRAP -> shell.onSoftWrapChanged(actions.toggleSoftWrap())
            Leader.SELECT_LINE -> actions.selectLine()
            Leader.DELETE_LINE -> edited { actions.deleteLine() }()
            Leader.DUPLICATE_LINE -> edited { actions.duplicateLine() }()
            Leader.TOGGLE_COMMENT -> edited { actions.toggleComment() }()
            Leader.INDENT -> if (!shell.acceptCompletion()) edited { actions.indent() }()
            Leader.COPY -> shell.clip = actions.copy()
            Leader.CUT -> { shell.clip = actions.cut(); shell.onEdited() }
            Leader.PASTE -> shell.clip?.let { edited { actions.paste(it) }() }
            else -> shell.onLeader(command)
        }
    }

    // ---- key repeat (PLAN §5.1, K10: the keyboard never repeats) ---------------------------

    private fun startRepeat(code: Int, action: () -> Unit) {
        repeatingCode = code
        cancelRepeat = scheduler.schedule(repeatDelayMs) { repeatTick(code, action) }
    }

    private fun repeatTick(code: Int, action: () -> Unit) {
        if (repeatingCode != code) return
        shell.onKeyLogged("REPEAT $code")
        action()
        cancelRepeat = scheduler.schedule(repeatIntervalMs) { repeatTick(code, action) }
    }

    private fun stopRepeat() {
        cancelRepeat?.invoke()
        cancelRepeat = null
        repeatingCode = 0
    }

    companion object {
        /** Red side button with "PTT app = Titan Code" (PLAN §4.1). */
        const val KEYCODE_RED_BUTTON = 401
        /** `fn` key, FUNC3 (PLAN §5.1). */
        const val KEYCODE_FN = 403
        /** Sent after every keyboard-surface swipe; meaningless to us (PLAN §5.1). */
        const val KEYCODE_SWIPE_MARKER = 404
        /** Alt+Space produces this private-use character; dropped (K03). */
        const val ALT_SPACE_PRIVATE_USE = 0xEF01

        /** Keys that don't mean a finger is typing on the keyboard surface. */
        private val NOT_TYPING = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BACK,
            KEYCODE_SWIPE_MARKER, KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        )

        /** Alt+Shift on the digit keys 4–7 (Alt+S/D/F/Z) gives `$ % ^ &` (PLAN §5.3). */
        val ALT_SHIFT = mapOf(
            KeyEvent.KEYCODE_S to "$",
            KeyEvent.KEYCODE_D to "%",
            KeyEvent.KEYCODE_F to "^",
            KeyEvent.KEYCODE_Z to "&",
        )
    }
}
