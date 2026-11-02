package dev.titan.code

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.titan.code.editor.EditorActions
import dev.titan.code.editor.TitanEditor
import dev.titan.code.input.GestureEngine
import dev.titan.code.input.InputRouter

/**
 * The single activity (PLAN §3).
 *
 * **M1 step 2 version:** a full-screen [TitanEditor] with a debug status line, to check that
 * Sora + TYPE_NULL + pre-IME routing work on the device before building the shell (step 3).
 */
class MainActivity : ComponentActivity() {

    private lateinit var editor: TitanEditor
    private lateinit var gestures: GestureEngine
    private var lastKey by mutableStateOf("—")
    private var position by mutableStateOf("Ln 1 Col 1")
    /** Camera cutout in px (left-top corner square on the Titan 2), read at runtime (PLAN §4.1). */
    private var cutoutPx by mutableStateOf(android.graphics.Rect())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = Prefs(this)
        editor = TitanEditor(this).apply {
            setWordwrap(prefs.softWrap, true)
            setText(SAMPLE)
            setSelection(2, 8)
        }
        val router = InputRouter(editor.actions)
        router.onEvent = { lastKey = it }
        router.onSoftWrapChanged = { prefs.softWrap = it; TLog.i("soft wrap -> $it") }
        editor.keyHandler = router::onKey
        gestures = GestureEngine(object : GestureEngine.Sink {
            private var mark: EditorActions.Mark? = null
            override fun beginStroke() { mark = editor.actions.saveSelection() }
            override fun move(direction: EditorActions.Direction, steps: Int, extend: Boolean) {
                repeat(steps) { editor.actions.move(direction, extend, withinLine = true) }
            }
            override fun abortStroke() {
                mark?.let { editor.actions.restoreSelection(it) }
                TLog.d("touchPad long slide dropped")
            }
            override fun strokeSummary(summary: String) {
                TLog.d(summary)
                lastKey = summary
            }
        })
        editor.subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent::class.java) { _, _ ->
            val c = editor.cursor
            position = "Ln ${c.leftLine + 1} Col ${c.leftColumn + 1}" + if (c.isSelected) " · sel" else ""
        }

        setContent { Screen() }

        window.decorView.post {
            window.decorView.rootWindowInsets?.displayCutout?.boundingRects
                ?.firstOrNull { it.top == 0 && it.left == 0 && !it.isEmpty }
                ?.let { cutoutPx = it }
        }
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) TLog.d("activity ${KeyEvent.keyCodeToString(event.keyCode)}")
        return super.dispatchKeyEvent(event)
    }

    /**
     * Keyboard-surface touches (device `touchPad`) are screen-coordinate touch events and must
     * never reach a view (PLAN §5.6). They all go to the GestureEngine and are consumed.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.device?.name != TOUCHPAD_DEVICE) return super.dispatchTouchEvent(ev)
        val shift = (ev.metaState and KeyEvent.META_SHIFT_ON) != 0
        val action = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> GestureEngine.Action.DOWN
            MotionEvent.ACTION_MOVE -> GestureEngine.Action.MOVE
            MotionEvent.ACTION_UP -> GestureEngine.Action.UP
            MotionEvent.ACTION_CANCEL -> GestureEngine.Action.CANCEL
            else -> return true
        }
        if (action == GestureEngine.Action.MOVE) {
            for (i in 0 until ev.historySize) {
                gestures.onSample(action, ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i), shift)
            }
        }
        gestures.onSample(action, ev.x, ev.y, ev.eventTime, shift)
        return true
    }

    @Composable
    private fun Screen() {
        val density = LocalDensity.current
        val bandHeight = with(density) { maxOf(cutoutPx.bottom, 1).toDp() }.coerceAtLeast(40.dp)
        val bandStart = with(density) { cutoutPx.right.toDp() }
        Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(bandHeight).padding(start = bandStart + 8.dp), contentAlignment = Alignment.CenterStart) {
                Text("Titan Code · M1 step 2 check", color = Color(0xFFB0B0B0), fontSize = 13.sp)
            }
            AndroidView(
                factory = { editor.also { it.post { it.requestFocus() } } },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                "$position · ${editor.foreignEdits} foreign · $lastKey",
                color = Color(0xFFB0B0B0), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    private companion object {
        const val TOUCHPAD_DEVICE = "touchPad"
        val SAMPLE = """
            class Solution:
                def twoSum(self, nums: List[int], target: int) -> List[int]:
                    
            
            # Step 2 check: type letters, Alt symbols and digits, Enter, Backspace, Shift+Backspace, and Cursor Assistant arrows. Watch "foreign" below stay 0. This comment is deliberately long, so it wraps when soft wrap is on; sym+W turns wrapping off and on.
        """.trimIndent()
    }
}
