package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * VPN + DNS configuration. The VPN half is mutually exclusive: either a full
 * WireGuard tunnel, a DNS-only tunnel, or VPN off. The DNS half controls the
 * upstream resolver used inside the tunnel and/or Android Private DNS.
 */
@Serializable
data class NetworkConfig(
    val vpnMode: VpnMode = VpnMode.OFF,
    val dnsProvider: DnsProvider = DnsProvider.NONE,
    val customDohUrl: String = "",       // when [dnsProvider] == CUSTOM
    val killSwitch: Boolean = true,      // block traffic when tunnel drops
    val allowBypass: Boolean = false,    // VpnService.Builder.allowBypass
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: List<String> = emptyList(),
    val wireGuardConfig: String = "",    // raw wg-quick config the user imported
    val privateDnsMode: PrivateDnsMode = PrivateDnsMode.OFF,
    val privateDnsSpecifier: String = "" // hostname for PRIVATE_DNS_MODE_PROVIDER_HOSTNAME
) {
    enum class VpnMode { OFF, FULL_TUNNEL, DNS_ONLY }
    enum class PrivateDnsMode { OFF, AUTO, STRICT, HOSTNAME }
    enum class PerAppMode { OFF, WHITELIST, BLACKLIST }
    enum class DnsProvider {
        NONE, GOOGLE, CLOUDFLARE, QUAD9, ADGUARD, NEXTDNS, CUSTOM
    }
}
