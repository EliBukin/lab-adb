package bukin.labadb

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import bukin.labadb.core.ArmPlan
import bukin.labadb.core.ArmRule
import java.util.concurrent.TimeUnit

/**
 * The three triggers, armed by [ArmRule]:
 * 1. [SettingChangeJob]: runs whenever adb_wifi_enabled changes (leaving the lab, roaming,
 *    a band switch, switched off by hand).
 * 2. [BackupCheckJob]: every 15 minutes.
 * 3. Android's "Wi-Fi available" trigger to [NetworkReceiver]. It is one-shot: Android drops it
 *    about 5 s after sending it, and sends it at once for a Wi-Fi already connected. So it is
 *    armed only while no Wi-Fi is connected, to catch the NEXT join.
 */
object Arming {

    private const val BACKUP_JOB_ID = 1
    private const val SETTING_JOB_ID = 2
    private val BACKUP_PERIOD_MS = TimeUnit.MINUTES.toMillis(15)
    private const val SETTING_UPDATE_DELAY_MS = 1_000L
    private const val SETTING_MAX_DELAY_MS = 5_000L

    /**
     * Arms or disarms everything to match the settings and the current Wi-Fi state.
     *
     * Never call this from [NetworkReceiver]: Wi-Fi is connected there, so there is nothing to arm,
     * and re-arming from a trigger is how loops start.
     *
     * @param replaceSettingJob true only at the end of a [SettingChangeJob] run. Scheduling the
     *   same job id stops the running job; that is how a content-trigger job renews itself, and
     *   Android carries over any change it saw during the run (AOSP JobInfo docs). Everywhere
     *   else a pending or running setting job is left alone.
     */
    fun sync(context: Context, replaceSettingJob: Boolean = false): ArmPlan {
        val app = context.applicationContext
        val store = Store(app)
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val jobs = app.getSystemService(JobScheduler::class.java)
        val plan = ArmRule.plan(store.enabled, store.labConfig(), WifiNetworks.connected(connectivity))

        if (plan.backupJob) scheduleBackupJob(app, jobs) else jobs.cancel(BACKUP_JOB_ID)
        if (plan.wifiTrigger) {
            // Registering the same PendingIntent again replaces the earlier request (documented).
            connectivity.registerNetworkCallback(wifiRequest(), wifiIntent(app))
        } else {
            connectivity.unregisterNetworkCallback(wifiIntent(app))
        }
        Log.d(Store.TAG, "Armed: $plan")
        // Last: renewing the setting job from inside its own run ends that run.
        if (plan.settingJob) scheduleSettingJob(app, jobs, replaceSettingJob) else jobs.cancel(SETTING_JOB_ID)
        return plan
    }

    fun disarm(context: Context) {
        val app = context.applicationContext
        val jobs = app.getSystemService(JobScheduler::class.java)
        jobs.cancel(BACKUP_JOB_ID)
        jobs.cancel(SETTING_JOB_ID)
        app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiIntent(app))
        Log.d(Store.TAG, "Disarmed")
    }

    /** Rescheduling a periodic job restarts its clock, so only schedule when missing or changed. */
    private fun scheduleBackupJob(context: Context, jobs: JobScheduler) {
        val pending = jobs.getPendingJob(BACKUP_JOB_ID)
        if (pending != null && pending.intervalMillis == BACKUP_PERIOD_MS) return
        val job = JobInfo.Builder(BACKUP_JOB_ID, ComponentName(context, BackupCheckJob::class.java))
            .setPeriodic(BACKUP_PERIOD_MS)
            .setPersisted(true)
            .build()
        jobs.schedule(job)
    }

    /**
     * A content-trigger job can be neither periodic nor persisted (Android refuses both), so it is
     * one-shot: it renews itself at the end of each run, and boot re-creates it.
     */
    private fun scheduleSettingJob(context: Context, jobs: JobScheduler, replace: Boolean) {
        if (!replace && jobs.getPendingJob(SETTING_JOB_ID) != null) return
        val job = JobInfo.Builder(SETTING_JOB_ID, ComponentName(context, SettingChangeJob::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(AdbWifiSetting.uri(), 0))
            .setTriggerContentUpdateDelay(SETTING_UPDATE_DELAY_MS)
            .setTriggerContentMaxDelay(SETTING_MAX_DELAY_MS)
            .build()
        jobs.schedule(job)
    }

    private fun wifiRequest(): NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        // A lab Wi-Fi without internet access must still match.
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private fun wifiIntent(context: Context): PendingIntent {
        // Explicit (our own receiver, which is not exported), so no other app can receive it.
        // Mutable, so Android can add EXTRA_NETWORK when it sends it.
        val intent = Intent(context, NetworkReceiver::class.java).setAction(NetworkReceiver.ACTION_WIFI_AVAILABLE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
