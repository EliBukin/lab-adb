package bukin.labadb

import android.app.job.JobParameters
import android.app.job.JobService
import bukin.labadb.core.HintKind
import java.util.concurrent.ConcurrentHashMap

/**
 * One hint burst or heartbeat ([HintSender]), kept alive by the job until it ends (see
 * [Hints.schedule]). Not exported: only the JobScheduler can bind to it.
 */
class HintJob : JobService() {

    private val running = ConcurrentHashMap<Int, StopSignal>()

    override fun onStartJob(params: JobParameters): Boolean {
        val name = params.extras.getString(Hints.EXTRA_KIND)
        val kind = HintKind.entries.firstOrNull { it.name == name }
        if (kind == null || kind == HintKind.MANUAL) return false
        val stop = StopSignal()
        running[params.jobId] = stop
        val app = applicationContext
        Hints.execute {
            try {
                HintSender(app).run(kind, stop)
            } finally {
                running.remove(params.jobId, stop)
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Replaced by a newer burst, or stopped by Android: end the packets now, no reschedule. */
    override fun onStopJob(params: JobParameters): Boolean {
        running[params.jobId]?.stop()
        return false
    }
}
