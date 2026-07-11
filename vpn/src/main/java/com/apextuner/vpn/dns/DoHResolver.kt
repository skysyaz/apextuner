package com.apextuner.vpn.dns

import com.apextuner.data.model.NetworkConfig
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * Minimal RFC 8484 DNS-over-HTTPS client. Sends a single DNS wire-format
 * query (binary, NOT JSON) via HTTP POST and returns the raw wire-format
 * response. The caller is responsible for parsing the 12-byte header + RRs.
 *
 * This is intentionally tiny — it does not implement caching, prefetching, or
 * DNSSEC validation. The expectation is that callers use it for low-volume
 * bootstrap lookups inside the DNS-only VPN path. For high-throughput
 * resolution the real production app would link BoringDNS or AdGuard's
 * <code>dnslookup</code>; that is out of scope for this skeleton.
 */
class DoHResolver(private val endpointUrl: String) : DnsResolver {

    override fun resolve(query: ByteArray, queryId: Short): ByteArray? {
        if (endpointUrl.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
                doOutput = true
            }
            conn.outputStream.use { it.write(query) }
            if (conn.responseCode != 200) return null
            conn.inputStream.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

/** Trait shared by DoH, DoT, and plain-UDP resolvers. */
interface DnsResolver {
    /**
     * @param query wire-format DNS query (12-byte header + question).
     * @param queryId the DNS transaction id, for log correlation.
     * @return wire-format DNS response, or null on failure.
     */
    fun resolve(query: ByteArray, queryId: Short): ByteArray?
}

/**
 * Pick a resolver based on the user's [NetworkConfig.DnsProvider]. Returns
 * null for [NetworkConfig.DnsProvider.NONE] — callers should fall back to the
 * system default resolver in that case.
 */
fun resolverFor(cfg: NetworkConfig): DnsResolver? = when (cfg.dnsProvider) {
    NetworkConfig.DnsProvider.NONE -> null
    NetworkConfig.DnsProvider.CUSTOM -> if (cfg.customDohUrl.isNotBlank())
        DoHResolver(cfg.customDohUrl) else null
    else -> DnsProviderPreset.fromProvider(cfg.dnsProvider)?.let { DoHResolver(it.dohUrl) }
}

@Suppress("unused")
private val bufferMarker: ByteBuffer? = null
