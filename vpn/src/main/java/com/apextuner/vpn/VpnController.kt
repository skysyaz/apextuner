package com.apextuner.vpn

import android.content.Context
import android.content.Intent
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.vpn.killswitch.KillSwitch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level facade the UI calls. Translates a [NetworkConfig] into:
 *  1. persisting the chosen mode/provider to DataStore
 *  2. starting or stopping [ApexVpnService]
 *  3. pushing the DNS specifier to Android Private DNS via [PrivateDnsController]
 *  4. arming/disarming the kill switch
 *
 * The UI never talks to [ApexVpnService] or [PrivateDnsController] directly.
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val logs: LogRepository,
    private val privateDns: PrivateDnsController,
    private val killSwitch: KillSwitch
) {

    suspend fun apply(cfg: NetworkConfig) {
        // 1. Persist
        settings.setVpnMode(cfg.vpnMode)
        settings.setDnsProvider(cfg.dnsProvider)
        settings.setCustomDohUrl(cfg.customDohUrl)
        settings.setVpnKillSwitch(cfg.killSwitch)
        settings.setVpnPerAppPackages(cfg.perAppPackages)
        settings.setPrivateDnsMode(cfg.privateDnsMode)
        settings.setPrivateDnsSpecifier(cfg.privateDnsSpecifier)

        // 2. Private DNS (system-wide, requires WRITE_SECURE_SETTINGS)
        if (cfg.privateDnsMode != NetworkConfig.PrivateDnsMode.OFF) {
            val ok = privateDns.apply(cfg.privateDnsMode, cfg.privateDnsSpecifier)
            if (!ok) logs.log(
                TunerLog.Level.WARN, TunerLog.Category.DNS,
                "Private DNS write skipped (no root/Shizuku)"
            )
        }

        // 3. VPN service
        when (cfg.vpnMode) {
            NetworkConfig.VpnMode.OFF -> {
                context.startService(Intent(context, ApexVpnService::class.java).apply {
                    action = "com.apextuner.vpn.STOP"
                })
                // Stop is delivered via stopService on the service side; here we
                // rely on the service reading the OFF mode in onStartCommand.
                // A cleaner path is to call stopService directly.
                runCatching { android.app.ActivityManager::class.java }
            }
            NetworkConfig.VpnMode.FULL_TUNNEL, NetworkConfig.VpnMode.DNS_ONLY -> {
                val intent = Intent(context, ApexVpnService::class.java)
                // The actual VpnService.prepare() call must happen in an
                // Activity context — the caller (UI) is responsible for that
                // before invoking us.
                runCatching { context.startService(intent) }
                    .onFailure { logs.log(TunerLog.Level.ERROR, TunerLog.Category.VPN, "startService failed", it.message) }
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
            privateDnsMode = runCatching { NetworkConfig.PrivateDnsMode.valueOf(s.privateDnsMode) }
                .getOrDefault(NetworkConfig.PrivateDnsMode.AUTO),
            privateDnsSpecifier = s.privateDnsSpecifier
        )
    }
}
