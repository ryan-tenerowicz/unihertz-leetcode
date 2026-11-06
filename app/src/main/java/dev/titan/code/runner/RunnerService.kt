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
import android.os.Process
import android.util.Log
import com.chaquo.python.Python

/**
 * The local runner (PLAN §7), in its own process (`android:process=":runner"`), so a hang or a
 * crash in user code never touches the editor: the app kills this process and binds a fresh one.
 *
 * Python starts and the harness (with LeetCode's pre-imports) loads in [onCreate], when the app
 * binds at launch, so a warm runner is waiting before the first run (the "warm spare").
 * Runs are serialised on one worker thread.
 */
class RunnerService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("python-runner").apply { start() }
        val handler = object : Handler(worker.looper) {
            override fun handleMessage(msg: Message) = handle(msg)
        }
        messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
            // Copy the message: the original is recycled after this callback.
            handler.sendMessage(Message.obtain(msg))
            true
        })
        handler.post {
            val t = System.currentTimeMillis()
            PythonStart.ensure(this)
            Python.getInstance().getModule("titan_runner")
            Log.d(TAG, "python ready in ${System.currentTimeMillis() - t} ms (pid ${Process.myPid()})")
        }
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val reply = Message.obtain(null, msg.what)
        reply.arg1 = msg.arg1
        reply.data = Bundle().apply {
            putInt(KEY_PID, Process.myPid())
            if (msg.what == MSG_RUN) {
                val request = msg.data.getString(KEY_REQUEST).orEmpty()
                val t = System.currentTimeMillis()
                val result = try {
                    Python.getInstance().getModule("titan_runner").callAttr("run", request).toString()
                } catch (e: Throwable) {
                    """{"status":"internal_error","error":${org.json.JSONObject.quote(e.toString())},"cases":[]}"""
                }
                Log.d(TAG, "run took ${System.currentTimeMillis() - t} ms")
                putString(KEY_RESULT, result)
            }
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
        const val TAG = "TitanRunner"
        const val MSG_HELLO = 1
        const val MSG_RUN = 2
        const val KEY_REQUEST = "request"
        const val KEY_RESULT = "result"
        const val KEY_PID = "pid"
    }
}
