package bukin.labadb

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import bukin.labadb.core.HintBlock
import bukin.labadb.core.HintInputs
import bukin.labadb.core.HintKind
import bukin.labadb.core.HintMessage
import bukin.labadb.core.HintRule
import bukin.labadb.core.HintTarget
import bukin.labadb.core.Ipv4
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends the "I'm here" hint to ANXEL ([HintMessage]). Every packet is one UDP datagram from a
 * socket bound to the lab Wi-Fi network, so it can never go out over mobile data, a VPN or
 * another Wi-Fi. Send-only: it never listens and never reads a reply. On a send error it lists
 * one event and stops: no retries.
 *
 * Blocking: every kind first waits up to [HintRule.PORT_SEARCH_MS] (5 s) for the port, then a
 * burst takes 8 s more. Run it on [Hints] only.
 */
class HintSender(context: Context) {

    private val app = context.applicationContext
    private val store = Store(app)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    fun run(kind: HintKind, stop: StopSignal) {
        var sent = 0
        var lastPort = HintMessage.UNKNOWN_PORT
        var failed = false
        var block: HintBlock? = null
        var finder: PortFinder? = null
        try {
            when (val first = check(sameConnection = null)) {
                is Check.Blocked -> block = first.block
                is Check.Ready -> {
                    first.key.fill(0)
                    val handle = first.lab.snapshot.handle
                    finder = startPortSearch(first.lab)
                    // Offsets count from after this wait, so packet 1 already has the port if found.
                    if (kind.waitsForPort) finder?.await(HintRule.PORT_SEARCH_MS)
                    val start = SystemClock.elapsedRealtime()
                    for (offset in kind.offsetsMs) {
                        if (stop.waitUntil(start + offset)) break
                        // Everything again before each packet: the phone may have left, or the
                        // switch may be off by now. Only the same lab connection counts.
                        val ready = when (val now = check(sameConnection = handle)) {
                            is Check.Blocked -> {
                                block = now.block
                                null
                            }
                            is Check.Ready -> now
                        } ?: break
                        val port = HintMessage.portOrUnknown(finder?.port)
                        if (!send(ready, port)) {
                            failed = true
                            break
                        }
                        sent++
                        lastPort = port
                        store.recordHintSent(System.currentTimeMillis(), handle, port)
                    }
                }
            }
        } finally {
            finder?.close()
        }
        val outcome = HintRule.outcome(sent, lastPort, failed, block) ?: return
        val event = HintRule.eventFor(kind, outcome, store.lastHintOutcome)
        if (event != null) store.log(event) else Log.d(Store.TAG, "$outcome (not listed)")
        store.lastHintOutcome = outcome
    }

    private sealed interface Check {
        class Ready(val lab: LabLink, val target: HintTarget, val key: ByteArray) : Check
        class Blocked(val block: HintBlock) : Check
    }

    /** Gathers every condition fresh. A Ready result holds the key: zero it once used. */
    private fun check(sameConnection: Long?): Check {
        val lab = store.labConfig()
            ?.let { WifiNetworks.lab(connectivity, it) }
            ?.takeIf { sameConnection == null || it.snapshot.handle == sameConnection }
        val value = AdbWifiSetting.read(app.contentResolver)
        val target = store.hintTarget()
        val key = HintKeyFile.read(app)
        val inputs = HintInputs(
            enabled = store.enabled,
            onLab = lab != null,
            debuggingOn = value != null && value != 0,
            keySet = key != null,
            targetSet = target != null,
        )
        val block = HintRule.block(inputs)
        if (block != null || lab == null || target == null || key == null) {
            key?.fill(0)
            return Check.Blocked(block ?: HintBlock.NOT_ON_LAB)
        }
        return Check.Ready(lab, target, key)
    }

    private fun startPortSearch(lab: LabLink): PortFinder? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return try {
            PortFinder.start(app, lab.network, lab.address, HintRule.PORT_SEARCH_MS)
        } catch (e: RuntimeException) {
            Log.d(Store.TAG, "Port search: ${e.javaClass.simpleName}")
            null
        }
    }

    /** One datagram with a fresh t and mac. Never logs the payload, the key or the mac. */
    private fun send(ready: Check.Ready, port: Int): Boolean {
        val payload = try {
            HintMessage.build(ready.key, Build.MODEL, port, ready.lab.address, System.currentTimeMillis())
        } finally {
            ready.key.fill(0)
        }
        return try {
            DatagramSocket().use { socket ->
                // Bound to the lab Wi-Fi: if that network is gone, this fails instead of
                // sending the packet some other way.
                ready.lab.network.bindSocket(socket)
                val to = InetAddress.getByAddress(Ipv4.toBytes(ready.target.address))
                socket.send(DatagramPacket(payload, payload.size, to, ready.target.port))
            }
            true
        } catch (e: IOException) {
            Log.w(Store.TAG, "Hint send failed: ${e.javaClass.simpleName}")
            false
        }
    }
}
