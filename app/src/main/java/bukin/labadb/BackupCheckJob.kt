package bukin.labadb

import android.app.job.JobParameters
import android.app.job.JobService
import bukin.labadb.core.Trigger

/**
 * The 15-minute backup check. It covers what the Wi-Fi trigger can miss: moving to another access
 * point of the same Wi-Fi (Android switches Wireless debugging off, but the network stays the same)
 * and the app's process being killed. Android may delay it while the phone dozes.
 */
class BackupCheckJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Worker.execute {
            try {
                Checker(app).run(Trigger.BACKUP_CHECK)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** The next periodic run comes anyway: no need to reschedule this one. */
    override fun onStopJob(params: JobParameters): Boolean = false
}
