package com.apextuner.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level facade the UI calls. Translates a [NetworkConfig] into:
 *  1. persisting the chosen mode/provider to DataStore
 *  2. starting or stopping [ApexVpnService]
 *  3. pushing the DNS specifier to Android Private DNS via [PrivateDnsController]
 *
 * VPN / DNS-via-VPN works without root. System Private DNS write still needs
 * root, Shizuku, or WRITE_SECURE_SETTINGS (ADB). Soft kill switch needs no root.
 *
 * The UI must call [VpnService.prepare] before [apply] when enabling a tunnel.
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val logs: LogRepository,
    private val privateDns: PrivateDnsController
) {

    suspend fun apply(cfg: NetworkConfig) {
        settings.setVpnLastMode(cfg.vpnMode)
        settings.setDnsProvider(cfg.dnsProvider)
        settings.setCustomDohUrl(cfg.customDohUrl)
        settings.setVpnKillSwitch(cfg.killSwitch)
        settings.setVpnPerAppPackages(cfg.perAppPackages)
        settings.setVpnWireGuardConfig(cfg.wireGuardConfig)
        settings.setPrivateDnsMode(cfg.privateDnsMode)
        settings.setPrivateDnsSpecifier(cfg.privateDnsSpecifier)

        // System Private DNS — best-effort; VPN DNS path does not need this.
        if (cfg.privateDnsMode != NetworkConfig.PrivateDnsMode.OFF) {
            val ok = privateDns.apply(cfg.privateDnsMode, cfg.privateDnsSpecifier)
            if (!ok) logs.log(
                TunerLog.Level.WARN, TunerLog.Category.DNS,
                "Private DNS write skipped — use VPN DNS mode, or grant WRITE_SECURE_SETTINGS via ADB/Shizuku/root"
            )
        }

        when (cfg.vpnMode) {
            NetworkConfig.VpnMode.OFF -> {
                val stop = Intent(context, ApexVpnService::class.java).apply {
                    action = ApexVpnService.ACTION_STOP
                }
                runCatching { context.startService(stop) }
                runCatching { context.stopService(Intent(context, ApexVpnService::class.java)) }
            }
            NetworkConfig.VpnMode.FULL_TUNNEL, NetworkConfig.VpnMode.DNS_ONLY -> {
                val intent = Intent(context, ApexVpnService::class.java).apply {
                    action = ApexVpnService.ACTION_START
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }.onFailure {
                    logs.log(TunerLog.Level.ERROR, TunerLog.Category.VPN, "startService failed", it.message)
                }
            }
        }
    }

    suspend fun snapshot(): NetworkConfig {
        val s = settings.snapshot.first()
        return NetworkConfig(
            vpnMode = runCatching { NetworkConfig.VpnMode.valueOf(s.vpnLastMode) }
                .getOrDefault(NetworkConfig.VpnMode.OFF),
            dnsProvider = runCatching { NetworkConfig.DnsProvider.valueOf(s.dnsProvider) }
                .getOrDefault(NetworkConfig.DnsProvider.CLOUDFLARE),
            customDohUrl = s.customDohUrl,
            killSwitch = s.vpnKillSwitch,
            perAppPackages = s.vpnPerAppPackages,
            wireGuardConfig = s.vpnWireGuardConfig,
            privateDnsMode = runCatching { NetworkConfig.PrivateDnsMode.valueOf(s.privateDnsMode) }
                .getOrDefault(NetworkConfig.PrivateDnsMode.AUTO),
            privateDnsSpecifier = s.privateDnsSpecifier
        )
    }
}
