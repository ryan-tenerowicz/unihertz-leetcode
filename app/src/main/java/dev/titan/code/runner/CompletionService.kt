package dev.titan.code.runner

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.chaquo.python.Python
import java.util.concurrent.atomic.AtomicInteger

/**
 * Type-aware completion (PLAN §5.4, rev 38): Jedi (`titan_complete.py`) in its own process
 * (`android:process=":complete"`), so it never waits behind a run, a runner restart can't drop
 * it, and its memory stays out of the editor's process.
 *
 * Requests are answered on one worker thread, newest first: while typing, a request that a
 * newer one has replaced is skipped, not analysed.
 */
class CompletionService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger
    private val newest = AtomicInteger()

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("python-complete").apply { start() }
        val handler = object : Handler(worker.looper) {
            override fun handleMessage(msg: Message) = handle(msg)
        }
        messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == MSG_COMPLETE) newest.set(msg.arg1)
            handler.sendMessage(Message.obtain(msg))
            true
        })
        handler.post {
            val t = System.currentTimeMillis()
            try {
                PythonStart.ensure(this)
                val warm = Python.getInstance().getModule("titan_complete").callAttr("init", cacheDir.path).toInt()
                Log.i(TAG, "jedi ready in ${System.currentTimeMillis() - t} ms (warm-up $warm ms)")
            } catch (e: Throwable) {
                Log.w(TAG, "jedi failed to start: $e")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(msg: Message) {
        if (msg.what != MSG_COMPLETE) return
        val stale = msg.arg1 != newest.get()
        val result = if (stale) {
            """{"items":[],"error":"stale"}"""
        } else {
            try {
                Python.getInstance().getModule("titan_complete")
                    .callAttr("complete", msg.data.getString(KEY_REQUEST).orEmpty()).toString()
            } catch (e: Throwable) {
                """{"items":[],"error":${org.json.JSONObject.quote(e.toString())}}"""
            }
        }
        val reply = Message.obtain(null, MSG_COMPLETE).apply {
            arg1 = msg.arg1
            data = Bundle().apply { putString(KEY_RESULT, result) }
        }
        try {
            msg.replyTo?.send(reply)
        } catch (e: Exception) {
            Log.w(TAG, "reply failed: $e")
        }
    }

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val TAG = "TitanComplete"
        const val MSG_COMPLETE = 1
        const val KEY_REQUEST = "request"
        const val KEY_RESULT = "result"
    }
}
