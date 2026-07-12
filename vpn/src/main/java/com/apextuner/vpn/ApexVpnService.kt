package com.apextuner.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.vpn.dns.DnsProviderPreset
import com.apextuner.vpn.dns.DnsTunForwarder
import com.apextuner.vpn.dns.resolverFor
import com.apextuner.vpn.killswitch.KillSwitch
import com.apextuner.vpn.wireguard.ParsedWireGuard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject

/**
 * ApexTuner [VpnService]. Works without root:
 *
 *  - [NetworkConfig.VpnMode.DNS_ONLY] — routes only configured DNS server /32s
 *    into the tun and forwards UDP/53 via [DnsTunForwarder]. Changes system DNS.
 *  - [NetworkConfig.VpnMode.FULL_TUNNEL] — if a WireGuard config is present,
 *    configures the builder for a full tunnel (packet engine still best-effort);
 *    otherwise falls back to DNS-only behaviour so Apply still does something useful.
 *
 * Soft kill switch (`allowBypass=false`) works on all devices. Hard iptables
 * kill switch still requires root via [KillSwitch].
 */
@AndroidEntryPoint
class ApexVpnService : VpnService() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var killSwitch: KillSwitch

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private var dnsForwarder: DnsTunForwarder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("ApexTuner VPN starting…"))
        scope.launch { startTunnel() }
        return START_STICKY
    }

    private suspend fun startTunnel() {
        teardownTunnelOnly()

        val snap = settings.snapshot.first()
        val mode = runCatching { NetworkConfig.VpnMode.valueOf(snap.vpnLastMode) }
            .getOrDefault(NetworkConfig.VpnMode.OFF)
        if (mode == NetworkConfig.VpnMode.OFF) {
            stopSelf(); return
        }

        val dnsProvider = runCatching { NetworkConfig.DnsProvider.valueOf(snap.dnsProvider) }
            .getOrDefault(NetworkConfig.DnsProvider.CLOUDFLARE)
        val networkCfg = NetworkConfig(
            vpnMode = mode,
            dnsProvider = dnsProvider,
            customDohUrl = snap.customDohUrl,
            killSwitch = snap.vpnKillSwitch,
            allowBypass = false,
            perAppPackages = snap.vpnPerAppPackages,
            wireGuardConfig = snap.vpnWireGuardConfig
        )

        val dnsServers = resolveDnsServers(networkCfg)
        if (dnsServers.isEmpty()) {
            logs.log(TunerLog.Level.ERROR, TunerLog.Category.DNS, "No DNS servers resolved for provider=$dnsProvider")
            stopSelf(); return
        }

        // Full WireGuard packet engine is not linked yet. Routing 0.0.0.0/0
        // without a forwarder blackholes the internet — always use DNS-only
        // path (works without root). Keep WG config persisted for a future engine.
        val useFullTunnel = false
        if (mode == NetworkConfig.VpnMode.FULL_TUNNEL && networkCfg.wireGuardConfig.isNotBlank()) {
            logs.log(
                TunerLog.Level.WARN, TunerLog.Category.VPN,
                "Full WireGuard tunnel not yet available — applying DNS-only VPN instead"
            )
        }

        val builder = Builder()
            .setSession(SESSION)
            .setMtu(1500)
            .allowFamily(android.system.OsConstants.AF_INET)

        builder.addAddress("10.111.222.1", 32)
        dnsServers.forEach { addr ->
            runCatching { builder.addDnsServer(addr) }
        }

        if (useFullTunnel) {
            builder.addRoute("0.0.0.0", 0)
            runCatching { builder.allowFamily(android.system.OsConstants.AF_INET6) }
            builder.addRoute("::", 0)
            val wg = ParsedWireGuard.fromConfig(networkCfg)
            wg?.parsed?.dnsServers?.forEach { dns ->
                runCatching { builder.addDnsServer(InetAddress.getByName(dns)) }
            }
        } else {
            // DNS-only: only pull packets destined to the DNS servers into the tun.
            dnsServers.forEach { addr ->
                val host = addr.hostAddress ?: return@forEach
                if (':' in host) return@forEach // skip v6 in light forwarder
                runCatching { builder.addRoute(host, 32) }
            }
        }

        resolverFor(networkCfg)?.let {
            logs.log(
                TunerLog.Level.INFO, TunerLog.Category.DNS,
                "DNS upstream selected: ${networkCfg.dnsProvider}"
            )
        }

        when (networkCfg.perAppMode) {
            NetworkConfig.PerAppMode.WHITELIST ->
                networkCfg.perAppPackages.forEach { runCatching { builder.addAllowedApplication(it) } }
            NetworkConfig.PerAppMode.BLACKLIST ->
                networkCfg.perAppPackages.forEach { runCatching { builder.addDisallowedApplication(it) } }
            else -> {}
        }

        // Soft kill switch — works without root.
        if (!networkCfg.killSwitch) builder.allowBypass()

        try {
            val fd = builder.establish()
            if (fd == null) {
                logs.log(
                    TunerLog.Level.ERROR, TunerLog.Category.VPN,
                    "VpnService.establish() returned null — grant VPN permission first"
                )
                stopSelf(); return
            }
            tunFd = fd
            if (!useFullTunnel) {
                dnsForwarder = DnsTunForwarder(this, fd, dnsServers).also { it.start() }
            }
            logs.log(
                TunerLog.Level.INFO, TunerLog.Category.VPN,
                "Tunnel established (mode=$mode, dns=${dnsServers.joinToString { it.hostAddress ?: "?" }})"
            )
            startForeground(NOTIFICATION_ID, buildNotification(
                if (useFullTunnel) "Full tunnel active" else "DNS: ${dnsProvider.name}"
            ))
            if (networkCfg.killSwitch) {
                val endpoint = ParsedWireGuard.fromConfig(networkCfg)?.parsed?.peers
                    ?.firstOrNull()?.endpoint
                killSwitch.start(endpoint)
            }
        } catch (t: Throwable) {
            logs.log(TunerLog.Level.ERROR, TunerLog.Category.VPN, "Tunnel establish failed", t.message)
            teardown()
            stopSelf()
        }
    }

    private suspend fun resolveDnsServers(cfg: NetworkConfig): List<InetAddress> {
        val preset = DnsProviderPreset.fromProvider(cfg.dnsProvider)
        val hosts = when {
            cfg.dnsProvider == NetworkConfig.DnsProvider.CUSTOM && cfg.customDohUrl.isNotBlank() ->
                DnsProviderPreset.CLOUDFLARE.plainServers
            preset != null -> preset.plainServers
            else -> DnsProviderPreset.CLOUDFLARE.plainServers
        }
        val resolved = mutableListOf<InetAddress>()
        val failed = mutableListOf<String>()
        for (host in hosts) {
            runCatching { InetAddress.getByName(host) }
                .onSuccess { addr ->
                    if (addr.address.size == 4) {
                        resolved.add(addr)
                    } else {
                        logs.log(TunerLog.Level.WARN, TunerLog.Category.DNS, "Skipped IPv6 DNS server: $host")
                    }
                }
                .onFailure {
                    failed.add(host)
                    logs.log(TunerLog.Level.WARN, TunerLog.Category.DNS, "Failed to resolve DNS server: $host", it.message)
                }
        }
        if (resolved.isNotEmpty() && failed.isNotEmpty()) {
            logs.log(
                TunerLog.Level.WARN, TunerLog.Category.DNS,
                "Some DNS servers failed to resolve (using ${resolved.size}/${hosts.size})",
                "failed: ${failed.joinToString()}"
            )
        }
        return resolved
    }

    override fun onRevoke() {
        scope.launch { logs.log(TunerLog.Level.WARN, TunerLog.Category.VPN, "VPN revoked by system/user") }
        teardown()
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        teardownTunnelOnly()
        killSwitch.stop()
    }

    private fun teardownTunnelOnly() {
        dnsForwarder?.stop()
        dnsForwarder = null
        runCatching { tunFd?.close() }
        tunFd = null
    }

    private fun buildNotification(text: String): Notification {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID, "ApexTuner VPN",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification while VPN tunnel is active"
                    setShowBadge(false)
                }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ApexTuner VPN")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 0xA011
        const val CHANNEL_ID = "apextuner.vpn"
        const val SESSION = "ApexTunerVpn"
        const val ACTION_STOP = "com.apextuner.vpn.STOP"
        const val ACTION_START = "com.apextuner.vpn.START"
    }
}
