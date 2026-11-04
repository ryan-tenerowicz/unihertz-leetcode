package dev.titan.code.data

import dev.titan.code.data.db.TimerRow

/**
 * The practice timer (PLAN §14.2 stage 4, rev 24): a quiet per-problem stopwatch. It starts on
 * the first edit, counts only while the app is in front with that problem open, stops when a
 * submission is accepted, and its reading is saved with every submission.
 *
 * [now] is a monotonic clock in ms (SystemClock.elapsedRealtime on the phone).
 */
class PracticeTimer(private val now: () -> Long) {
    var slug: String? = null
        private set
    var started = false
        private set
    var done = false
        private set
    private var startedAt = 0L
    private var doneAt: Long? = null
    private var base = 0L
    private var since: Long? = null
    private var foreground = true

    val running get() = since != null

    /** Switch to [slug], with its saved state (null: never started). */
    fun load(slug: String, row: TimerRow?) {
        this.slug = slug
        base = row?.elapsedMs ?: 0L
        started = row != null
        doneAt = row?.doneAt
        done = doneAt != null
        startedAt = row?.startedAt ?: 0L
        since = if (started && !done && foreground) now() else null
    }

    /** The draft was edited; [wallMs] is the time of day, saved as when practice started. */
    fun edited(wallMs: Long) {
        if (slug == null || started || done) return
        started = true
        startedAt = wallMs
        if (foreground) since = now()
    }

    /** The app came to the front or went to the background. */
    fun foreground(on: Boolean) {
        if (on == foreground) return
        foreground = on
        if (on) { if (started && !done) since = now() } else stop()
    }

    private fun stop() {
        since?.let { base += now() - it }
        since = null
    }

    fun elapsedMs(): Long = base + (since?.let { now() - it } ?: 0L)

    /** An accepted submission: the time is final. */
    fun finish(wallMs: Long) {
        if (!started) return
        stop()
        done = true
        doneAt = wallMs
    }

    /** Start over (Settings): not started until the next edit. */
    fun reset() {
        base = 0L
        since = null
        started = false
        done = false
        doneAt = null
    }

    /** What to save; null when there's nothing (never started, or reset). */
    fun row(provider: String): TimerRow? {
        val s = slug ?: return null
        return if (!started) null else TimerRow(provider, s, elapsedMs(), startedAt, doneAt)
    }

    companion object {
        /** 754 000 → "12:34"; an hour or more → "1:02:03". */
        fun format(ms: Long): String {
            val t = ms / 1000
            val h = t / 3600
            val m = (t / 60) % 60
            val s = t % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
