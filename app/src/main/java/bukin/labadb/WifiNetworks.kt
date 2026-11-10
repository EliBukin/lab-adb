package bukin.labadb

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
        )
    }

    /**
     * Every connected Wi-Fi network. Not just the default network: a lab Wi-Fi without internet
     * is usually not the default while mobile data is on, but Wireless debugging still uses it.
     */
    fun connected(connectivity: ConnectivityManager): List<NetworkSnapshot> {
        // Deprecated in favour of callbacks since API 31, but still supported, and it gives a
        // one-shot answer without registering anything.
        @Suppress("DEPRECATION")
        val all = connectivity.allNetworks
        return all.mapNotNull { snapshot(connectivity, it) }.filter { it.isWifi && !it.isVpn }
    }

    private fun ipv4(address: InetAddress?): Int? = (address as? Inet4Address)?.let { Ipv4.fromBytes(it.address) }
}
