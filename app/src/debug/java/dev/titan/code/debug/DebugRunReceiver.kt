package dev.titan.code.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.titan.code.runner.RunnerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only (rev 24): run one harness request on the phone's Python without touching the
 * editor or any draft, for checking the runner on the device.
 *
 * Push the request JSON to the app's cache (`run-as dev.titan.code` …/cache/debug-run.json),
 * then: `adb shell am broadcast -n dev.titan.code/.debug.DebugRunReceiver --el limit 30000`.
 * The result goes to cache/debug-run.out.json, and its first 3000 characters to logcat
 * (tag TitanDebugRun).
 */
class DebugRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val dir = context.cacheDir
        val request = File(dir, "debug-run.json").takeIf { it.exists() }?.readText()
        if (request == null) { Log.w(TAG, "no cache/debug-run.json"); pending.finish(); return }
        val limit = intent.getLongExtra("limit", 30_000L)
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val client = RunnerClient(context)
            client.bind()
            val t = System.currentTimeMillis()
            val out = try {
                client.run(request, limit)
            } catch (e: Exception) {
                """{"debugError":${org.json.JSONObject.quote(e.toString())}}"""
            }
            File(dir, "debug-run.out.json").writeText(out)
            Log.i(TAG, "done in ${System.currentTimeMillis() - t} ms: ${out.take(3000)}")
            pending.finish()
        }
    }

    private companion object { const val TAG = "TitanDebugRun" }
}
