package dev.titan.code

import android.util.Log

/** Logcat under the tag `TitanCode` (PLAN §3). `scripts/logcat.sh app` filters on it. */
object TLog {
    const val TAG = "TitanCode"
    fun d(msg: String) { Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
}
