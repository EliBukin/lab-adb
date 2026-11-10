package bukin.labadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bukin.labadb.core.Trigger

/**
 * Restart (BOOT_COMPLETED, sent after the first unlock) and app update (MY_PACKAGE_REPLACED):
 * Android drops the Wi-Fi trigger then, so re-arm and run one check. Not exported: only the
 * system sends these.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Trigger.RESTART
            Intent.ACTION_MY_PACKAGE_REPLACED -> Trigger.APP_UPDATED
            else -> return
        }
        val app = context.applicationContext
        val pending = goAsync()
        Worker.execute {
            try {
                val store = Store(app)
                if (trigger == Trigger.RESTART) store.forgetConnections()
                if (Arming.sync(app)) {
                    store.log("${trigger.label}: re-armed")
                    Checker(app).run(trigger)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
