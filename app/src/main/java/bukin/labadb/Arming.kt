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
import java.util.concurrent.TimeUnit

/**
 * The two triggers: Android's "Wi-Fi available" callback (to [NetworkReceiver]) and the
 * 15-minute backup check ([BackupCheckJob]). Armed only while the switch is on and a lab
 * subnet is saved.
 */
object Arming {

    private const val JOB_ID = 1
    private val PERIOD_MS = TimeUnit.MINUTES.toMillis(15)

    /** Arms or disarms to match the settings. Returns true when armed. */
    fun sync(context: Context): Boolean {
        val app = context.applicationContext
        val store = Store(app)
        val armed = store.enabled && store.labConfig() != null
        if (armed) arm(app) else disarm(app)
        return armed
    }

    /**
     * Never call this from [NetworkReceiver]: Android answers a registration by sending
     * "available" for the current Wi-Fi again, which would loop.
     */
    private fun arm(context: Context) {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // A lab Wi-Fi without internet access must still match.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // Registering the same PendingIntent again replaces the earlier request (documented),
        // so this is safe to repeat.
        context.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, wifiIntent(context))

        val jobs = context.getSystemService(JobScheduler::class.java)
        val pending = jobs.getPendingJob(JOB_ID)
        // Rescheduling restarts the 15-minute clock, so only schedule when missing or changed.
        if (pending == null || pending.intervalMillis != PERIOD_MS) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, BackupCheckJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build()
            jobs.schedule(job)
        }
    }

    fun disarm(context: Context) {
        context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiIntent(context))
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    private fun wifiIntent(context: Context): PendingIntent {
        // Explicit (our own receiver, which is not exported), so no other app can receive it.
        // Mutable, so Android can add EXTRA_NETWORK when it sends it.
        val intent = Intent(context, NetworkReceiver::class.java).setAction(NetworkReceiver.ACTION_WIFI_AVAILABLE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
