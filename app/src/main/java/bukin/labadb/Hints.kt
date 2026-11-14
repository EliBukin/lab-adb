package bukin.labadb

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import bukin.labadb.core.HintKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs hint sends ([HintSender]) one at a time, on their own thread, so a burst never holds up a
 * check on [Worker].
 */
object Hints {

    private const val BURST_JOB_ID = 3
    private const val SINGLE_JOB_ID = 4
    const val EXTRA_KIND = "kind"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "LabAdb-hint") }

    fun execute(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (e: RuntimeException) {
                Log.e(Store.TAG, "Hint failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Burst and heartbeat run in [HintJob]. A running job keeps the process alive for the whole
     * burst (a receiver's process, or one whose screen was just closed, can be frozen or killed
     * after a few seconds), and Android runs it in Doze because the app is on the battery
     * allowlist. Expedited first (Android 12+): it starts at once. If the expedited quota is used
     * up, schedule() refuses it and a regular job is used, as on Android 11: an allowlisted app is
     * in the EXEMPTED standby bucket, whose jobs are neither batched nor held back in Doze.
     *
     * A new burst replaces one still running (same job id); a heartbeat has its own id.
     * Never throws: a hint must never break the check that asked for it.
     */
    fun schedule(context: Context, kind: HintKind) {
        try {
            val id = if (kind == HintKind.BURST) BURST_JOB_ID else SINGLE_JOB_ID
            val extras = PersistableBundle().apply { putString(EXTRA_KIND, kind.name) }
            val component = ComponentName(context, HintJob::class.java)
            val jobs = context.getSystemService(JobScheduler::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val expedited = JobInfo.Builder(id, component).setExtras(extras).setExpedited(true).build()
                if (jobs.schedule(expedited) == JobScheduler.RESULT_SUCCESS) return
            }
            if (jobs.schedule(JobInfo.Builder(id, component).setExtras(extras).build()) != JobScheduler.RESULT_SUCCESS) {
                Log.w(Store.TAG, "Couldn't schedule the hint")
            }
        } catch (e: RuntimeException) {
            Log.w(Store.TAG, "Couldn't schedule the hint: ${e.javaClass.simpleName}")
        }
    }

    /** Send now: the screen is open, so it runs here directly. [onDone] runs on the hint thread. */
    fun sendNow(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        execute {
            try {
                HintSender(app).run(HintKind.MANUAL, StopSignal())
            } finally {
                onDone()
            }
        }
    }
}

/** Lets a job end a running hint early (onStopJob). */
class StopSignal {

    private val latch = CountDownLatch(1)

    fun stop() = latch.countDown()

    /** Sleeps until [elapsedRealtime] (SystemClock) unless stopped first. True when stopped. */
    fun waitUntil(elapsedRealtime: Long): Boolean {
        val waitMs = elapsedRealtime - SystemClock.elapsedRealtime()
        return if (waitMs > 0) latch.await(waitMs, TimeUnit.MILLISECONDS) else latch.count == 0L
    }
}
