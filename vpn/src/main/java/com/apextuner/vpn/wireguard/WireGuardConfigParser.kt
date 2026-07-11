package com.apextuner.vpn.wireguard

import com.apextuner.data.model.NetworkConfig

/**
 * One parsed WireGuard peer. Matches the `[Peer]` section of wg-quick configs.
 */
data class WireGuardPeer(
    val publicKey: String,
    val preSharedKey: String? = null,
    val allowedIps: List<String>,
    val endpoint: String? = null,
    val keepaliveSeconds: Int? = null
)

/**
 * One parsed WireGuard interface. Matches the `[Interface]` section plus the
 * list of `[Peer]` sections.
 */
data class WireGuardConfigParsed(
    val privateKey: String,
    val address: List<String>,
    val dnsServers: List<String>,
    val mtu: Int = 1280,
    val peers: List<WireGuardPeer>
) {
    companion object {
        val EMPTY = WireGuardConfigParsed(
            privateKey = "",
            address = emptyList(),
            dnsServers = emptyList(),
            mtu = 1280,
            peers = emptyList()
        )
    }
}

/**
 * Parses a wg-quick INI-style config into a typed structure. The format is:
 *
 * ```
 * [Interface]
 * PrivateKey = ...
 * Address = 10.0.0.2/32, fd00::2/128
 * DNS = 1.1.1.1
 * MTU = 1280
 *
 * [Peer]
 * PublicKey = ...
 * AllowedIPs = 0.0.0.0/0, ::/0
 * Endpoint = vpn.example.com:51820
 * PersistentKeepalive = 25
 * ```
 *
 * This parser is intentionally permissive — it lower-cases keys, strips
 * comments, and splits comma-separated values. It does NOT validate key
 * formats; that is the responsibility of the WireGuard userspace the
 * production app would invoke.
 */
object WireGuardConfigParser {

    fun parse(raw: String): WireGuardConfigParsed {
        if (raw.isBlank()) return WireGuardConfigParsed.EMPTY

        var privateKey = ""
        val addresses = mutableListOf<String>()
        val dnsServers = mutableListOf<String>()
        var mtu = 1280
        val peers = mutableListOf<WireGuardPeer>()

        var section = ""
        var curPeer: WireGuardPeerBuilder? = null

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Flush any peer being built.
                curPeer?.build()?.let { peers.add(it) }
                curPeer = null
                section = trimmed.removeSurrounding("[", "]").trim()
                if (section.equals("Peer", ignoreCase = true)) curPeer = WireGuardPeerBuilder()
                continue
            }
            val eq = trimmed.indexOf('=')
            if (eq < 0) continue
            val key = trimmed.substring(0, eq).trim().lowercase()
            val value = trimmed.substring(eq + 1).trim()

            when (section.lowercase()) {
                "interface" -> when (key) {
                    "privatekey" -> privateKey = value
                    "address" -> addresses.addAll(splitCsv(value))
                    "dns" -> dnsServers.addAll(splitCsv(value))
                    "mtu" -> mtu = value.toIntOrNull() ?: 1280
                }
                "peer" -> curPeer?.apply {
                    when (key) {
                        "publickey" -> publicKey = value
                        "presharedkey" -> preSharedKey = value
                        "allowedips" -> allowedIps.addAll(splitCsv(value))
                        "endpoint" -> endpoint = value
                        "persistentkeepalive" -> keepalive = value.toIntOrNull()
                    }
                }
            }
        }
        curPeer?.build()?.let { peers.add(it) }

        return WireGuardConfigParsed(privateKey, addresses, dnsServers, mtu, peers)
    }

    /** Re-serialize to canonical wg-quick text for export / audit log. */
    fun serialize(cfg: WireGuardConfigParsed): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${cfg.privateKey}")
        if (cfg.address.isNotEmpty()) appendLine("Address = ${cfg.address.joinToString(", ")}")
        if (cfg.dnsServers.isNotEmpty()) appendLine("DNS = ${cfg.dnsServers.joinToString(", ")}")
        appendLine("MTU = ${cfg.mtu}")
        for (p in cfg.peers) {
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${p.publicKey}")
            p.preSharedKey?.let { appendLine("PresharedKey = $it") }
            appendLine("AllowedIPs = ${p.allowedIps.joinToString(", ")}")
            p.endpoint?.let { appendLine("Endpoint = $it") }
            p.keepaliveSeconds?.let { appendLine("PersistentKeepalive = $it") }
        }
    }

    private fun splitCsv(s: String): List<String> =
        s.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }

    private class WireGuardPeerBuilder {
        var publicKey: String = ""
        var preSharedKey: String? = null
        val allowedIps = mutableListOf<String>()
        var endpoint: String? = null
        var keepalive: Int? = null

        fun build(): WireGuardPeer? {
            if (publicKey.isBlank() && allowedIps.isEmpty()) return null
            return WireGuardPeer(publicKey, preSharedKey, allowedIps.toList(), endpoint, keepalive)
        }
    }
}

/**
 * Sentinel wrapper so callers can carry both the raw text and the parsed form.
 */
data class ParsedWireGuard(val raw: String, val parsed: WireGuardConfigParsed) {
    companion object {
        fun fromConfig(cfg: NetworkConfig): ParsedWireGuard? {
            if (cfg.wireGuardConfig.isBlank()) return null
            return ParsedWireGuard(cfg.wireGuardConfig, WireGuardConfigParser.parse(cfg.wireGuardConfig))
        }
    }
}
