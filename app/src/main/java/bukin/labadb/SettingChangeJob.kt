package bukin.labadb

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import bukin.labadb.core.Trigger

/**
 * Runs about 1-5 s after adb_wifi_enabled changes. It covers:
 * - leaving the lab: Android writes 0, no Wi-Fi now, so the Wi-Fi trigger is armed for the next join;
 * - roaming or a band switch at the lab, or the owner switching it off by hand: lab Wi-Fi is
 *   still there, so it is switched back on at once.
 *
 * Our own write of 1 also fires it: the check finds "already on" and does nothing. A refused
 * write (Android puts 0 back) fires it too: the failed-connection rule skips it. Checks run one
 * at a time on [Worker], so this one waits behind a running write-and-verify.
 */
class SettingChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Worker.execute {
            try {
                Checker(app).run(Trigger.SETTING_CHANGED)
            } finally {
                // Renew in place of jobFinished (see Arming.sync): this ends the current run.
                val renewed = try {
                    Arming.sync(app, replaceSettingJob = true).settingJob
                } catch (e: RuntimeException) {
                    Log.e(Store.TAG, "Re-arming failed", e)
                    false
                }
                if (!renewed) jobFinished(params, false)
            }
        }
        return true
    }

    /** Stopped early (for example by our own renewal): the worker still finishes and re-arms. */
    override fun onStopJob(params: JobParameters): Boolean = false
}
