package bukin.labadb

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import bukin.labadb.core.Ipv4
import bukin.labadb.core.NetworkSnapshot
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Reads the phone's networks through ConnectivityManager. Needs only ACCESS_NETWORK_STATE:
 * no location, no Wi-Fi names, IPv4 only.
 */
object WifiNetworks {

    /** One network as plain values, or null when it is already gone. */
    fun snapshot(connectivity: ConnectivityManager, network: Network): NetworkSnapshot? {
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        val link = connectivity.getLinkProperties(network)
        return NetworkSnapshot(
            handle = network.networkHandle,
            isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            ipv4Addresses = link?.linkAddresses.orEmpty().mapNotNull { ipv4(it.address) },
            ipv4DefaultGateways = link?.routes.orEmpty()
                .filter { it.isDefaultRoute && it.hasGateway() }
                .mapNotNull { ipv4(it.gateway) },
            isLocalOnly = isLocalOnly(caps, link),
        )
    }

    /**
     * Every connected real Wi-Fi network (local-only links left out). Not just the default
     * network: a lab Wi-Fi without internet is usually not the default while mobile data is on,
     * but Wireless debugging still uses it.
     */
    fun connected(connectivity: ConnectivityManager): List<NetworkSnapshot> {
        // Deprecated in favour of callbacks since API 31, but still supported, and it gives a
        // one-shot answer without registering anything.
        @Suppress("DEPRECATION")
        val all = connectivity.allNetworks
        return all.mapNotNull { snapshot(connectivity, it) }.filter { it.isRealWifi }
    }

    /**
     * A link the phone hosts itself: Wi-Fi Direct as group owner (car Android Auto, screen
     * mirroring, Quick Share) or its own hotspot. From Android 15 these are registered as
     * TRANSPORT_WIFI + NET_CAPABILITY_LOCAL_NETWORK. A Wi-Fi the phone joins as a client never has
     * that capability, with or without internet access, so a lab Wi-Fi is never dropped by it.
     * Second signal, in case a build registers a Wi-Fi Direct link without it: Wi-Fi Direct
     * interfaces are named "p2p…"; a client Wi-Fi interface never is.
     */
    private fun isLocalOnly(caps: NetworkCapabilities, link: LinkProperties?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
        ) {
            return true
        }
        return link?.interfaceName?.startsWith(P2P_INTERFACE_PREFIX) == true
    }

    private const val P2P_INTERFACE_PREFIX = "p2p"

    private fun ipv4(address: InetAddress?): Int? = (address as? Inet4Address)?.let { Ipv4.fromBytes(it.address) }
}
