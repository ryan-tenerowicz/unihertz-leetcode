package dev.titan.code.runner

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.RandomAccessFile

/**
 * Starts Chaquopy in this process (rev 38). The runner and the completer each have their own
 * process and Python; on the first launch after an install both unpack Python's files, so a
 * file lock keeps them from unpacking into the same folder at once.
 */
object PythonStart {
    fun ensure(context: Context) {
        if (Python.isStarted()) return
        RandomAccessFile(File(context.filesDir, "python-start.lock"), "rw").use { f ->
            f.channel.lock().use { Python.start(AndroidPlatform(context)) }
        }
    }
}
