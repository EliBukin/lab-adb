package bukin.labadb

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import bukin.labadb.core.Ipv4
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Looks for the phone's own Wireless debugging port. adbd announces it over mDNS as
 * "_adb-tls-connect._tcp" (through mdnsd), and Android's network service discovery (NsdManager)
 * sees the phone's own announcement on the lab Wi-Fi. Only a service at the phone's own lab IPv4
 * counts: other phones at the lab may announce adb too. The app opens no socket for this:
 * NsdManager does the mDNS inside the system.
 *
 * Android 14+ only (discovery scoped to one network, and ServiceInfoCallback); callers check.
 * It stops by itself once it has the port or after the timeout, and on [close].
 * It never logs service names: adbd's name holds the phone's serial number.
 *
 * Lint prefers @RequiresApi, but that one comes from AndroidX, which this app doesn't use.
 */
@SuppressLint("UseRequiresApi")
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PortFinder private constructor(
    private val nsd: NsdManager,
    private val network: Network,
    private val ownAddress: InetAddress,
) : AutoCloseable {

    private val lock = Any()
    private val done = CountDownLatch(1)

    @Volatile
    private var found: Int? = null
    private var closed = false
    private var discovering = false
    private val watching = ArrayList<NsdManager.ServiceInfoCallback>()
    private var timeout: ScheduledFuture<*>? = null

    /** The port found so far, or null. Never blocks. */
    val port: Int? get() = found

    /** Waits up to [timeoutMs] for the port, or until the search has ended. */
    fun await(timeoutMs: Long): Int? {
        done.await(timeoutMs, TimeUnit.MILLISECONDS)
        return found
    }

    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.d(Store.TAG, "Port search: discovery failed ($errorCode)")
            synchronized(lock) { discovering = false }
            close()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) = watch(serviceInfo)
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    private fun begin(timeoutMs: Long) {
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, network, CALLBACKS, discovery)
        } catch (e: RuntimeException) {
            Log.d(Store.TAG, "Port search: can't start (${e.javaClass.simpleName})")
            close()
            return
        }
        val stopNow = synchronized(lock) {
            if (!closed) {
                discovering = true
                timeout = CALLBACKS.schedule({ close() }, timeoutMs, TimeUnit.MILLISECONDS)
            }
            closed
        }
        // Closed while starting (start failed at once): make sure discovery is not left running.
        if (stopNow) quietly { nsd.stopServiceDiscovery(discovery) }
    }

    /** Resolves one found service. At most [MAX_WATCHED], so a crowded lab can't use up NSD's limit. */
    private fun watch(service: NsdServiceInfo) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = check(serviceInfo)
            override fun onServiceLost() = Unit
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        synchronized(lock) {
            if (closed || watching.size >= MAX_WATCHED) return
            try {
                nsd.registerServiceInfoCallback(service, CALLBACKS, callback)
                watching += callback
            } catch (e: RuntimeException) {
                Log.d(Store.TAG, "Port search: can't resolve (${e.javaClass.simpleName})")
            }
        }
    }

    private fun check(info: NsdServiceInfo) {
        val on = info.network
        if (on != null && on != network) return
        if (info.hostAddresses.none { it == ownAddress }) return
        val port = info.port
        if (port !in 1..65535) return
        if (found == null) found = port
        close()
    }

    /** Stops discovery and every resolve. Safe to call more than once, from any thread. */
    override fun close() {
        val stopDiscovery: Boolean
        val callbacks: List<NsdManager.ServiceInfoCallback>
        synchronized(lock) {
            if (closed) return
            closed = true
            stopDiscovery = discovering
            discovering = false
            callbacks = watching.toList()
            watching.clear()
            timeout?.cancel(false)
        }
        done.countDown()
        if (stopDiscovery) quietly { nsd.stopServiceDiscovery(discovery) }
        for (callback in callbacks) quietly { nsd.unregisterServiceInfoCallback(callback) }
    }

    /** NsdManager throws IllegalArgumentException for a listener it already dropped: harmless here. */
    private inline fun quietly(action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            Log.d(Store.TAG, "Port search: stop: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val SERVICE_TYPE = "_adb-tls-connect._tcp"
        private const val MAX_WATCHED = 4

        /** NSD callbacks and the timeout run here, never on the main thread. */
        private val CALLBACKS = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "LabAdb-nsd") }

        /** Starts a search on [network] for the service at [ownIpv4]. Android 14+ only. */
        fun start(context: Context, network: Network, ownIpv4: Int, timeoutMs: Long): PortFinder? {
            val nsd = context.getSystemService(NsdManager::class.java) ?: return null
            val finder = PortFinder(nsd, network, InetAddress.getByAddress(Ipv4.toBytes(ownIpv4)))
            finder.begin(timeoutMs)
            return finder
        }
    }
}
