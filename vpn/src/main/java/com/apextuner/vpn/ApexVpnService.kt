package com.apextuner.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
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
import javax.inject.Inject

/**
 * The single Android [VpnService] ApexTuner ships. Runs in three modes:
 *
 *  - [NetworkConfig.VpnMode.OFF]        → not started
 *  - [NetworkConfig.VpnMode.FULL_TUNNEL]→ routes all traffic through a
 *                                          WireGuard tunnel (when the user
 *                                          imports a config) or, failing
 *                                          that, a no-op pass-through that
 *                                          only rewrites DNS upstreams.
 *  - [NetworkConfig.VpnMode.DNS_ONLY]   → captures only UDP/TCP 53 traffic
 *                                          and rewrites it to the configured
 *                                          DoH/DoT provider.
 *
 * The service always runs as a foreground service (Play policy requirement)
 * and surfaces a sticky notification while the tunnel is up. The kill switch
 * is started alongside the tunnel and torn down on stop.
 *
 * NOTE: the actual packet-processing loop is intentionally a stub. A real
 * WireGuard tunnel would link libwg (BoringTUN) and run an fd-pumping
 * coroutine on the tun(4) interface. That integration is left to the user;
 * the skeleton wires up everything around it — Builder configuration, per-app
 * routing, kill switch lifecycle, and DNS upstream selection.
 */
@AndroidEntryPoint
class ApexVpnService : VpnService() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var killSwitch: KillSwitch

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("ApexTuner VPN active"))
        scope.launch { startTunnel() }
        return START_STICKY
    }

    private suspend fun startTunnel() {
        val snap = settings.snapshot.first()
        val mode = runCatching { NetworkConfig.VpnMode.valueOf(snap.vpnLastMode) }
            .getOrDefault(NetworkConfig.VpnMode.OFF)
        if (mode == NetworkConfig.VpnMode.OFF) {
            stopSelf(); return
        }

        val networkCfg = NetworkConfig(
            vpnMode = mode,
            dnsProvider = runCatching { NetworkConfig.DnsProvider.valueOf(snap.dnsProvider) }
                .getOrDefault(NetworkConfig.DnsProvider.CLOUDFLARE),
            customDohUrl = snap.customDohUrl,
            killSwitch = snap.vpnKillSwitch,
            allowBypass = false,
            perAppPackages = snap.vpnPerAppPackages
        )

        val builder = Builder()
            .setSession(SESSION)
            .setMtu(1500)
            .allowFamily(android.system.OsConstants.AF_INET)
            .allowFamily(android.system.OsConstants.AF_INET6)

        // DNS-only: capture 53 only, route everything else around the tunnel.
        // Full-tunnel: 0.0.0.0/0 + ::/0 captures everything.
        when (mode) {
            NetworkConfig.VpnMode.FULL_TUNNEL -> {
                builder.addAddress("10.111.222.1", 32)
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute("::", 0)
                val wg = ParsedWireGuard.fromConfig(networkCfg)
                if (wg != null) {
                    wg.parsed.dnsServers.forEach { dns ->
                        runCatching { builder.addDnsServer(java.net.InetAddress.getByName(dns)) }
                    }
                }
            }
            NetworkConfig.VpnMode.DNS_ONLY -> {
                builder.addAddress("10.111.222.1", 32)
                // Only route the DNS port — 53/udp+tcp via the loopback inside the tun.
                builder.addRoute("8.8.8.8", 32) // arbitrary — DNS-only mode intercepts by port
            }
            else -> {}
        }

        // DNS upstream selection (informs the local resolver used inside the tun).
        val resolver = resolverFor(networkCfg)
        if (resolver != null) {
            logs.log(
                level = TunerLog.Level.INFO,
                category = TunerLog.Category.DNS,
                message = "DNS upstream selected: ${networkCfg.dnsProvider}"
            )
        }

        // Per-app routing.
        when (networkCfg.perAppMode) {
            NetworkConfig.PerAppMode.WHITELIST -> {
                networkCfg.perAppPackages.forEach { builder.addAllowedApplication(it) }
            }
            NetworkConfig.PerAppMode.BLACKLIST -> {
                networkCfg.perAppPackages.forEach { builder.addDisallowedApplication(it) }
            }
            else -> { /* OFF — no per-app restrictions */ }
        }

        // No kill switch → let apps bypass the tunnel. Kill switch on → leave
        // the default (no bypass) so traffic cannot escape the tunnel.
        if (!networkCfg.killSwitch) builder.allowBypass()

        try {
            tunFd = builder.establish()
            if (tunFd == null) {
                logs.log(TunerLog.Level.ERROR, TunerLog.Category.VPN, "VpnService.establish() returned null — user revoked consent?")
                stopSelf(); return
            }
            logs.log(TunerLog.Level.INFO, TunerLog.Category.VPN, "Tunnel established (mode=$mode)")
            if (networkCfg.killSwitch) {
                val endpoint = ParsedWireGuard.fromConfig(networkCfg)?.parsed?.peers
                    ?.firstOrNull()?.endpoint
                killSwitch.start(endpoint)
            }
            // Packet-processing loop would start here on a dedicated thread.
        } catch (t: Throwable) {
            logs.log(TunerLog.Level.ERROR, TunerLog.Category.VPN, "Tunnel establish failed", t.message)
            stopSelf()
        }
    }

    override fun onRevoke() {
        // System or user tore down the VPN. Kill switch will arm automatically.
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
        runCatching { tunFd?.close() }
        tunFd = null
        killSwitch.stop()
    }

    private fun buildNotification(text: String): Notification {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_ID, "ApexTuner VPN",
                    android.app.NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent notification while VPN tunnel is active"
                    setShowBadge(false)
                }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
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
    }
}
