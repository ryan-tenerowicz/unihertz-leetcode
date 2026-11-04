package dev.titan.code.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.webkit.WebSettings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.titan.code.TLog
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Background sync (PLAN §8.4, M4) through WorkManager: pack downloads and a weekly refresh of the
 * problem list. Wi-Fi only unless Settings allows mobile data. Both are resumable: a stopped
 * worker retries with backoff and skips what's already saved.
 */
object Sync {
    const val TAG_PACK = "pack"
    const val KEY_PACK = "packId"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_PHASE = "phase"
    const val KEY_FAILED = "failed"
    private const val CHANNEL = "downloads"

    private fun constraints(mobileData: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .setRequiresStorageNotLow(true)
        .build()

    fun downloadPack(context: Context, packId: Long, mobileData: Boolean) {
        val req = OneTimeWorkRequestBuilder<PackWorker>()
            .setInputData(workDataOf(KEY_PACK to packId))
            .setConstraints(constraints(mobileData))
            // Rev 25: a long download is stopped and resumed many times; keep the waits short.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(TAG_PACK)
            .addTag("pack-id-$packId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("pack-$packId", ExistingWorkPolicy.KEEP, req)
    }

    fun cancelPack(context: Context, packId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pack-$packId")
    }

    /**
     * Rev 25: pack downloads run as a foreground job (a "Saving problems" notification with a
     * Stop button), so a download of thousands isn't cut off every 10 minutes. Android 15 allows
     * such jobs about 6 hours a day; past that the download pauses and resumes the next day.
     */
    fun foregroundInfo(context: Context, workId: java.util.UUID, packId: Long, title: String, text: String, done: Int, total: Int): ForegroundInfo {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Offline downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Saving $title")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        return ForegroundInfo((1000 + packId).toInt(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    fun scheduleWeeklyRefresh(context: Context, mobileData: Boolean) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints(mobileData))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("weekly-refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun repository(context: Context): Repository {
        val auth = AuthStore(context)
        val ua = WebSettings.getDefaultUserAgent(context)
        return Repository(context, LeetCodeHttp(auth::cookies, ua, minGapMs = 300),
            dev.titan.code.data.neetcode.NeetCodeHttp(auth, ua), dev.titan.code.data.codeforces.CodeforcesWeb(context))
    }
}

class PackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(Sync.KEY_PACK, -1)
        if (id < 0) return Result.failure()
        val repo = Sync.repository(applicationContext)
        val title = repo.packs().firstOrNull { it.id == id }?.title ?: return Result.success()
        var shownAt = 0L
        suspend fun show(text: String, done: Int, total: Int) {
            // Not allowed from the background on some starts: then it runs as ordinary work.
            runCatching { setForeground(Sync.foregroundInfo(applicationContext, getId(), id, title, text, done, total)) }
                .onFailure { if (shownAt == 0L) TLog.w("pack $id: no foreground: $it") }
        }
        show("Starting…", 0, 0)
        return try {
            val outcome = repo.downloadPack(id) { p ->
                setProgress(workDataOf(Sync.KEY_PACK to id, Sync.KEY_DONE to p.done, Sync.KEY_TOTAL to p.total,
                    Sync.KEY_PHASE to p.phase, Sync.KEY_FAILED to p.failed))
                val now = System.currentTimeMillis()
                if (now - shownAt > 5_000) {
                    shownAt = now
                    show("${p.phase.replaceFirstChar(Char::uppercase)} ${p.done} / ${p.total}" + if (p.failed > 0) " · ${p.failed} skipped" else "", p.done, p.total)
                }
            }
            TLog.d("pack $id: done, ${outcome.saved} new, ${outcome.failed} not saved")
            Result.success(workDataOf(Sync.KEY_PACK to id, Sync.KEY_FAILED to outcome.failed))
        } catch (e: IOException) {
            // Network, rate limit, browser check: resume later, however many times it takes.
            TLog.w("pack $id: $e; will retry")
            Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TLog.w("pack $id failed: $e")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repo = Sync.repository(applicationContext)
        repo.refreshList()
        TLog.d("weekly refresh: done")
        Result.success()
    } catch (e: Exception) {
        TLog.w("weekly refresh: $e")
        Result.retry()
    }
}
