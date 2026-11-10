package bukin.labadb

import android.app.job.JobParameters
import android.app.job.JobService
import bukin.labadb.core.Trigger

/**
 * The 15-minute backup check, then the arm rule. It covers what the other triggers can miss, for
 * example: the phone joins a non-lab Wi-Fi (that uses up the one-shot Wi-Fi trigger), then leaves
 * it with no setting change; this run re-arms the Wi-Fi trigger. It also restores the setting job
 * if Android dropped it. In Doze it still runs, because the app is on the battery allowlist.
 */
class BackupCheckJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Worker.execute {
            try {
                Checker(app).run(Trigger.BACKUP_CHECK)
                Arming.sync(app)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** The next periodic run comes anyway: no need to reschedule this one. */
    override fun onStopJob(params: JobParameters): Boolean = false
}
