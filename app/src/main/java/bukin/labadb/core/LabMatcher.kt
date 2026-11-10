package bukin.labadb.core

/** What the app knows about one connected network, reduced to plain values (IPv4 only). */
data class NetworkSnapshot(
    /** Android's Network.networkHandle: unique per connection, new after every reconnect. */
    val handle: Long,
    val isWifi: Boolean,
    val isVpn: Boolean,
    val ipv4Addresses: List<Int>,
    /** Gateways of the IPv4 default routes. */
    val ipv4DefaultGateways: List<Int>,
)

enum class MatchResult { MATCH, NOT_WIFI, NO_IPV4_ADDRESS, ADDRESS_OUTSIDE_SUBNET, GATEWAY_MISMATCH }

object LabMatcher {

    /**
     * A network is the lab network when it is Wi-Fi (not a VPN), holds an IPv4 address inside the
     * lab subnet, and, when a gateway is set, has an IPv4 default route via that gateway.
     */
    fun match(config: LabConfig, network: NetworkSnapshot): MatchResult {
        if (!network.isWifi || network.isVpn) return MatchResult.NOT_WIFI
        if (network.ipv4Addresses.isEmpty()) return MatchResult.NO_IPV4_ADDRESS
        if (network.ipv4Addresses.none { config.subnet.contains(it) }) return MatchResult.ADDRESS_OUTSIDE_SUBNET
        val gateway = config.gateway
        if (gateway != null && gateway !in network.ipv4DefaultGateways) return MatchResult.GATEWAY_MISMATCH
        return MatchResult.MATCH
    }
}
