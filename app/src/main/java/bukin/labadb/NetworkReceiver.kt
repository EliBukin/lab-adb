package bukin.labadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import bukin.labadb.core.Trigger

/**
 * Android sends this (through our own one-shot PendingIntent) when a Wi-Fi network becomes
 * available. Not exported. The network in the intent is only a hint: the check reads the real
 * state itself. It never re-arms: Wi-Fi is connected now, and the setting-change and backup
 * jobs arm the trigger again once Wi-Fi is gone.
 */
class NetworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIFI_AVAILABLE) return
        val app = context.applicationContext
        if (!Store(app).enabled) {
            // A leftover registration: drop it. Never re-arm from here (see Arming.sync).
            Arming.disarm(app)
            return
        }
        val network = networkFrom(intent)
        val pending = goAsync()
        Worker.execute {
            try {
                Checker(app).run(Trigger.WIFI_JOINED, joined = network)
            } finally {
                pending.finish()
            }
        }
    }

    private fun networkFrom(intent: Intent): Network? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
        }

    companion object {
        const val ACTION_WIFI_AVAILABLE = "bukin.labadb.action.WIFI_AVAILABLE"
    }
}
