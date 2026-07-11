package com.apextuner.vpn.dns

import com.apextuner.data.model.NetworkConfig

/**
 * Catalogue of preset DNS providers. The DNS-only VPN mode uses these to
 * resolve A/AAAA queries via DNS-over-HTTPS, while the Private DNS path uses
 * the [hostname] field as the `PRIVATE_DNS_SPECIFIER`.
 */
enum class DnsProviderPreset(
    val provider: NetworkConfig.DnsProvider,
    val displayName: String,
    val dohUrl: String,
    val doTHostname: String,
    val plainServers: List<String>
) {
    GOOGLE(
        NetworkConfig.DnsProvider.GOOGLE, "Google Public DNS",
        "https://dns.google/dns-query",
        "dns.google",
        listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")
    ),
    CLOUDFLARE(
        NetworkConfig.DnsProvider.CLOUDFLARE, "Cloudflare 1.1.1.1",
        "https://cloudflare-dns.com/dns-query",
        "cloudflare-dns.com",
        listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111")
    ),
    QUAD9(
        NetworkConfig.DnsProvider.QUAD9, "Quad9",
        "https://dns.quad9.net/dns-query",
        "dns.quad9.net",
        listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe")
    ),
    ADGUARD(
        NetworkConfig.DnsProvider.ADGUARD, "AdGuard DNS",
        "https://dns.adguard-dns.com/dns-query",
        "dns.adguard-dns.com",
        listOf("94.140.14.14", "94.140.15.15")
    ),
    NEXTDNS(
        NetworkConfig.DnsProvider.NEXTDNS, "NextDNS",
        "https://dns.nextdns.io/dns-query", // user appends /<config-id>
        "dns.nextdns.io",
        listOf("45.90.28.0", "45.90.30.0")
    );

    companion object {
        fun fromProvider(p: NetworkConfig.DnsProvider): DnsProviderPreset? =
            values().firstOrNull { it.provider == p }
    }
}
