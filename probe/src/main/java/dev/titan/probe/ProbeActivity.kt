package dev.titan.probe

import android.app.Activity
import android.hardware.input.InputManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout

/** Base activity: logs the Activity-level input stages, focus, lifecycle and device changes. */
abstract class ProbeActivity : Activity(), InputManager.InputDeviceListener {

    /** Whether this screen's input is logged (the main menu turns it off). */
    open val logInput: Boolean get() = true

    /** Full-screen layout that uses the top line beside the camera: the status bar is hidden
     *  (swipe down shows it briefly), content is drawn into the cutout band, and only the first
     *  line of each screen is indented past the top-left camera cutout (123x123 px on the Titan 2)
     *  and made at least as tall as it.
     *  Navigation bar and IME still pad the bottom. */
    override fun setContentView(view: View) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val frame = FrameLayout(this)
        frame.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime())
            v.setPadding(i.left, 0, i.right, i.bottom)
            // Indent the first line past a cutout that touches the top-left corner.
            val cut = insets.displayCutout?.boundingRects?.firstOrNull { it.top == 0 && it.left == 0 && !it.isEmpty }
            firstLine(view)?.let { line ->
                val base = (line.getTag(R.id.base_padding_left) as? Int) ?: line.paddingLeft.also { line.setTag(R.id.base_padding_left, it) }
                line.setPadding(base + (cut?.right ?: 0), line.paddingTop, line.paddingRight, line.paddingBottom)
                // The first line fills the whole camera band, so nothing below it slides under the camera.
                line.minimumHeight = cut?.bottom ?: 0
                if (line is TextView) line.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            WindowInsets.CONSUMED
        }
        super.setContentView(frame)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** The view drawn on the screen's first line: descend through first children until a leaf or a row. */
    private fun firstLine(root: View): View? {
        var v = root
        while (v is ViewGroup && v.childCount > 0 && !(v is LinearLayout && v.orientation == LinearLayout.HORIZONTAL)) v = v.getChildAt(0)
        return v
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (logInput) EventLog.key("activity", event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (logInput) EventLog.motion("touch", ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (logInput) EventLog.motion("generic", ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchTrackballEvent(ev: MotionEvent): Boolean {
        if (logInput) EventLog.motion("trackball", ev)
        return super.dispatchTrackballEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (logInput) EventLog.note("focus", "window focus ${if (hasFocus) "gained" else "LOST"} (${javaClass.simpleName})")
    }

    override fun onResume() {
        super.onResume()
        if (logInput) EventLog.note("lifecycle", "resume ${javaClass.simpleName}")
        getSystemService(InputManager::class.java).registerInputDeviceListener(this, null)
    }

    override fun onPause() {
        super.onPause()
        if (logInput) EventLog.note("lifecycle", "pause ${javaClass.simpleName}")
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        EventLog.note("device", "added #$deviceId ${Names.device(deviceId)}")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        EventLog.note("device", "removed #$deviceId")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        EventLog.note("device", "changed #$deviceId ${Names.device(deviceId)}")
    }
}
