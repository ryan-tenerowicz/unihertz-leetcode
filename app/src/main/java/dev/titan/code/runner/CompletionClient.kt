package dev.titan.code.runner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import dev.titan.code.TLog

/**
 * The app's side of [CompletionService] (rev 38). [request] sends the buffer and cursor; only the
 * answer to the newest request is delivered, on the main thread. Nothing is delivered while the
 * service is starting or if it dies (it restarts itself); the caller shows its fallback then.
 */
class CompletionClient(context: Context) {
    private val app = context.applicationContext
    private var service: Messenger? = null
    private var newest = 0
    private var onResult: ((String) -> Unit)? = null

    private val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == CompletionService.MSG_COMPLETE && msg.arg1 == newest) {
            val r = msg.data.getString(CompletionService.KEY_RESULT).orEmpty()
            onResult?.also { onResult = null }?.invoke(r)
        }
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            TLog.d("complete: connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            TLog.w("complete: process gone; restarting")
            service = null
        }
    }

    fun bind() {
        app.bindService(Intent(app, CompletionService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    /** Ask for completions; [done] gets the JSON result, unless a newer request replaces this one. Main thread. */
    fun request(requestJson: String, done: (String) -> Unit): Boolean {
        val m = service ?: return false
        newest++
        onResult = done
        return try {
            m.send(Message.obtain(null, CompletionService.MSG_COMPLETE).apply {
                arg1 = newest
                replyTo = replies
                data = Bundle().apply { putString(CompletionService.KEY_REQUEST, requestJson) }
            })
            true
        } catch (e: Exception) {
            onResult = null
            false
        }
    }

    /** Drop any answer still on its way (the suggestions were closed). */
    fun cancel() {
        newest++
        onResult = null
    }
}
