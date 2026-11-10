package bukin.labadb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import bukin.labadb.core.CheckInputs
import bukin.labadb.core.CheckResult
import bukin.labadb.core.Decider
import bukin.labadb.core.Decision
import bukin.labadb.core.NetworkSnapshot
import bukin.labadb.core.Trigger

/**
 * One check: gather the facts, let [Decider] decide, and switch Wireless debugging on if it says
 * so. Blocking (up to about 8 s when it writes): run it on [Worker] only.
 */
class Checker(context: Context) {

    private val app = context.applicationContext
    private val store = Store(app)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    /**
     * @param joined the network from Android's "Wi-Fi available" broadcast, if that started it.
     * @param manual true for "Test now", which may retry once on a connection where it failed.
     */
    fun run(trigger: Trigger, joined: Network? = null, manual: Boolean = false): CheckResult {
        // Android re-sends "available" for the current Wi-Fi each time the trigger is re-armed.
        val effectiveTrigger =
            if (joined != null && joined.networkHandle == store.lastSeenHandle) Trigger.WIFI_RECHECK else trigger
        val networks = if (joined != null) listOfNotNull(waitForAddress(joined)) else WifiNetworks.connected(connectivity)
        val inputs = CheckInputs(
            enabled = store.enabled,
            config = store.labConfig(),
            permissionGranted = AdbWifiSetting.isPermissionGranted(app),
            networks = networks,
            adbWifiValue = AdbWifiSetting.read(app.contentResolver),
            failedHandle = store.failedHandle,
            manual = manual,
        )
        val decision = Decider.decide(inputs)
        decision.network?.let { store.lastSeenHandle = it.handle }
        val result = when (decision) {
            is Decision.Skip -> decision.result
            is Decision.Write -> switchOnAndVerify(decision.network)
        }
        val text = Decider.eventText(effectiveTrigger, result)
        if (text != null) store.log(text) else Log.d(Store.TAG, "${effectiveTrigger.label}: ${result.text} (not listed)")
        return result
    }

    /** Right after Wi-Fi connects, its IPv4 address can lag a moment behind: retry briefly. */
    private fun waitForAddress(network: Network): NetworkSnapshot? {
        val deadline = SystemClock.elapsedRealtime() + ADDRESS_WAIT_MS
        while (true) {
            val snapshot = WifiNetworks.snapshot(connectivity, network) ?: return null
            if (snapshot.ipv4Addresses.isNotEmpty() || SystemClock.elapsedRealtime() >= deadline) return snapshot
            SystemClock.sleep(ADDRESS_RETRY_MS)
        }
    }

    /**
     * Writes 1, then re-reads after a few seconds. Android puts the value back to 0 when it
     * refuses, most likely an access point not yet approved (it shows its own "Allow" box then).
     * That connection is remembered so the backup check doesn't pop the box again every 15 min.
     */
    private fun switchOnAndVerify(lab: NetworkSnapshot): CheckResult {
        try {
            AdbWifiSetting.switchOn(app.contentResolver)
        } catch (e: SecurityException) {
            return CheckResult.NO_PERMISSION
        }
        SystemClock.sleep(VERIFY_DELAY_MS)
        val result = Decider.resultAfterWrite(AdbWifiSetting.read(app.contentResolver))
        store.failedHandle = if (result == CheckResult.DID_NOT_STICK) lab.handle else null
        return result
    }

    private companion object {
        const val ADDRESS_WAIT_MS = 3_000L
        const val ADDRESS_RETRY_MS = 500L
        const val VERIFY_DELAY_MS = 5_000L
    }
}
