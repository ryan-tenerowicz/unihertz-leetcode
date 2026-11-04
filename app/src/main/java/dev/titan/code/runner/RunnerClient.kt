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
import android.os.Process
import dev.titan.code.TLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** The run took longer than its limit; the runner process was killed and is restarting. */
class RunnerTimeoutException(val limitMs: Long) : Exception("Time limit exceeded (${limitMs / 1000.0} s locally)")

/** The runner process died during a run (out of memory or a native crash); it restarts by itself. */
class RunnerCrashedException(message: String) : Exception(message)

/**
 * The app's side of the local runner (PLAN §7). Binds [RunnerService] at launch (warm spare),
 * sends one JSON request per run, and kills the runner process on a timeout or crash; binding
 * with `BIND_AUTO_CREATE` brings a fresh one straight back.
 */
class RunnerClient(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var service: Messenger? = null
    private var connected = CompletableDeferred<Messenger>()
    private var pid = 0
    private val ids = AtomicInteger()
    private val pending = mutableMapOf<Int, CompletableDeferred<String>>()

    private val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
        val p = msg.data.getInt(RunnerService.KEY_PID)
        if (p != 0) pid = p
        if (msg.what == RunnerService.MSG_RUN) pending.remove(msg.arg1)?.complete(msg.data.getString(RunnerService.KEY_RESULT).orEmpty())
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val m = Messenger(binder)
            service = m
            m.send(Message.obtain(null, RunnerService.MSG_HELLO).apply { replyTo = replies })
            connected.complete(m)
            TLog.d("runner: connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            TLog.w("runner: process gone; restarting")
            service = null
            connected = CompletableDeferred()
            pending.values.forEach { it.completeExceptionally(RunnerCrashedException("out of memory or a native crash")) }
            pending.clear()
        }
    }

    fun bind() {
        app.bindService(Intent(app, RunnerService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    /** Run [requestJson]; must be called on the main thread. */
    suspend fun run(requestJson: String, limitMs: Long): String {
        val m = withTimeout(STARTUP_LIMIT_MS) { connected.await() }
        val id = ids.incrementAndGet()
        val done = CompletableDeferred<String>()
        pending[id] = done
        m.send(Message.obtain(null, RunnerService.MSG_RUN).apply {
            arg1 = id
            replyTo = replies
            data = Bundle().apply { putString(RunnerService.KEY_REQUEST, requestJson) }
        })
        return try {
            withTimeout(limitMs) { done.await() }.also { r ->
                // A case got stuck in a call the harness couldn't interrupt; its thread is still running.
                if (runCatching { org.json.JSONObject(r).optBoolean("restart") }.getOrDefault(false)) restart()
            }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            restart()
            throw RunnerTimeoutException(limitMs)
        }
    }

    /** Kill the runner process (a hung or runaway run); the binding restarts it. */
    fun restart() {
        val p = pid
        if (p != 0 && p != Process.myPid()) {
            TLog.w("runner: killing pid $p")
            Process.killProcess(p)
            pid = 0
        }
        main.post { if (service == null) bind() }
    }

    private companion object {
        /** First launch unpacks Python, which takes a few seconds once. */
        const val STARTUP_LIMIT_MS = 30_000L
    }
}
