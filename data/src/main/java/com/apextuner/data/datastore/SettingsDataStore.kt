package com.apextuner.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.apextuner.data.model.NetworkConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apexDataStore: DataStore<Preferences> by preferencesDataStore(name = "apextuner_prefs")

/**
 * Typed wrapper around the Preferences DataStore. Exposes a small typed
 * [SettingsSnapshot] Flow plus granular setters. The snapshot is consumed by
 * the dashboard and the foreground watchdog service.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val store get() = context.apexDataStore

    val snapshot: Flow<SettingsSnapshot> = store.data.map { p ->
        SettingsSnapshot(
            onboardingComplete = p[PreferenceKeys.ONBOARDING_COMPLETE] ?: false,
            rootGranted = p[PreferenceKeys.ROOT_GRANTED] ?: false,
            shizukuGranted = p[PreferenceKeys.SHIZUKU_GRANTED] ?: false,
            activeProfileId = p[PreferenceKeys.ACTIVE_PROFILE_ID] ?: 0L,
            gamingModeActive = p[PreferenceKeys.GAMING_MODE_ACTIVE] ?: false,
            lastSafeProfileId = p[PreferenceKeys.LAST_SAFE_PROFILE_ID] ?: Profile_default,
            cpuTempThresholdC = p[PreferenceKeys.CPU_TEMP_THRESHOLD_C] ?: 75,
            gpuTempThresholdC = p[PreferenceKeys.GPU_TEMP_THRESHOLD_C] ?: 85,
            autoRevertOnThermal = p[PreferenceKeys.AUTO_REVERT_ON_THERMAL] ?: true,
            watchdogEnabled = p[PreferenceKeys.WATCHDOG_ENABLED] ?: true,
            applyOnBoot = p[PreferenceKeys.APPLY_ON_BOOT] ?: false,
            bootProfileId = p[PreferenceKeys.BOOT_PROFILE_ID] ?: Profile_default,
            vpnAutoConnect = p[PreferenceKeys.VPN_AUTO_CONNECT] ?: false,
            vpnKillSwitch = p[PreferenceKeys.VPN_KILL_SWITCH] ?: true,
            vpnLastMode = p[PreferenceKeys.VPN_LAST_MODE] ?: NetworkConfig.VpnMode.OFF.name,
            vpnPerAppPackages = p[PreferenceKeys.VPN_PER_APP_PACKAGES]?.toList() ?: emptyList(),
            dnsProvider = p[PreferenceKeys.DNS_PROVIDER] ?: NetworkConfig.DnsProvider.CLOUDFLARE.name,
            customDohUrl = p[PreferenceKeys.CUSTOM_DOH_URL] ?: "",
            privateDnsMode = p[PreferenceKeys.PRIVATE_DNS_MODE] ?: NetworkConfig.PrivateDnsMode.AUTO.name,
            privateDnsSpecifier = p[PreferenceKeys.PRIVATE_DNS_SPECIFIER] ?: "cloudflare-dns.com",
            forcePeakHz = p[PreferenceKeys.FORCE_PEAK_HZ] ?: false,
            adaptiveRefresh = p[PreferenceKeys.ADAPTIVE_REFRESH] ?: true,
            perAppRefreshPackages = p[PreferenceKeys.PER_APP_REFRESH_PACKAGES]?.toList() ?: emptyList(),
            themeMode = p[PreferenceKeys.THEME_MODE] ?: "system",
            dynamicColor = p[PreferenceKeys.DYNAMIC_COLOR] ?: true,
            hapticsEnabled = p[PreferenceKeys.HAPTICS_ENABLED] ?: true,
            pollIntervalMs = p[PreferenceKeys.POLL_INTERVAL_MS] ?: 1000,
            chartHistoryPoints = p[PreferenceKeys.CHART_HISTORY_POINTS] ?: 60,
            logRetentionDays = p[PreferenceKeys.LOG_RETENTION_DAYS] ?: 14,
            logLevel = p[PreferenceKeys.LOG_LEVEL] ?: "INFO"
        )
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        store.edit { mut -> block(mut); mut }
    }

    suspend fun setOnboardingComplete(v: Boolean) = update { it[PreferenceKeys.ONBOARDING_COMPLETE] = v }
    suspend fun setRootGranted(v: Boolean) = update { it[PreferenceKeys.ROOT_GRANTED] = v }
    suspend fun setShizukuGranted(v: Boolean) = update { it[PreferenceKeys.SHIZUKU_GRANTED] = v }
    suspend fun setActiveProfileId(id: Long) = update { it[PreferenceKeys.ACTIVE_PROFILE_ID] = id }
    suspend fun setGamingModeActive(v: Boolean) = update { it[PreferenceKeys.GAMING_MODE_ACTIVE] = v }
    suspend fun setLastSafeProfileId(id: Long) = update { it[PreferenceKeys.LAST_SAFE_PROFILE_ID] = id }
    suspend fun setCpuTempThreshold(c: Int) = update { it[PreferenceKeys.CPU_TEMP_THRESHOLD_C] = c }
    suspend fun setGpuTempThreshold(c: Int) = update { it[PreferenceKeys.GPU_TEMP_THRESHOLD_C] = c }
    suspend fun setAutoRevertOnThermal(v: Boolean) = update { it[PreferenceKeys.AUTO_REVERT_ON_THERMAL] = v }
    suspend fun setWatchdogEnabled(v: Boolean) = update { it[PreferenceKeys.WATCHDOG_ENABLED] = v }
    suspend fun setApplyOnBoot(v: Boolean) = update { it[PreferenceKeys.APPLY_ON_BOOT] = v }
    suspend fun setBootProfileId(id: Long) = update { it[PreferenceKeys.BOOT_PROFILE_ID] = id }
    suspend fun setVpnAutoConnect(v: Boolean) = update { it[PreferenceKeys.VPN_AUTO_CONNECT] = v }
    suspend fun setVpnKillSwitch(v: Boolean) = update { it[PreferenceKeys.VPN_KILL_SWITCH] = v }
    suspend fun setVpnLastMode(mode: NetworkConfig.VpnMode) = update { it[PreferenceKeys.VPN_LAST_MODE] = mode.name }
    suspend fun setVpnPerAppPackages(pkgs: List<String>) = update {
        it[PreferenceKeys.VPN_PER_APP_PACKAGES] = pkgs.toSet()
    }
    suspend fun setDnsProvider(p: NetworkConfig.DnsProvider) = update { it[PreferenceKeys.DNS_PROVIDER] = p.name }
    suspend fun setCustomDohUrl(url: String) = update { it[PreferenceKeys.CUSTOM_DOH_URL] = url }
    suspend fun setPrivateDnsMode(m: NetworkConfig.PrivateDnsMode) = update {
        it[PreferenceKeys.PRIVATE_DNS_MODE] = m.name
    }
    suspend fun setPrivateDnsSpecifier(s: String) = update { it[PreferenceKeys.PRIVATE_DNS_SPECIFIER] = s }
    suspend fun setForcePeakHz(v: Boolean) = update { it[PreferenceKeys.FORCE_PEAK_HZ] = v }
    suspend fun setAdaptiveRefresh(v: Boolean) = update { it[PreferenceKeys.ADAPTIVE_REFRESH] = v }
    suspend fun setPerAppRefreshPackages(pkgs: List<String>) = update {
        it[PreferenceKeys.PER_APP_REFRESH_PACKAGES] = pkgs.toSet()
    }
    suspend fun setThemeMode(mode: String) = update { it[PreferenceKeys.THEME_MODE] = mode }
    suspend fun setDynamicColor(v: Boolean) = update { it[PreferenceKeys.DYNAMIC_COLOR] = v }
    suspend fun setHapticsEnabled(v: Boolean) = update { it[PreferenceKeys.HAPTICS_ENABLED] = v }
    suspend fun setPollIntervalMs(ms: Int) = update { it[PreferenceKeys.POLL_INTERVAL_MS] = ms }
    suspend fun setChartHistoryPoints(n: Int) = update { it[PreferenceKeys.CHART_HISTORY_POINTS] = n }
    suspend fun setLogRetentionDays(d: Int) = update { it[PreferenceKeys.LOG_RETENTION_DAYS] = d }
    suspend fun setLogLevel(level: String) = update { it[PreferenceKeys.LOG_LEVEL] = level }

    companion object {
        // Use Balanced profile as the safe default for boot/rollback.
        const val Profile_default: Long = -2L
    }
}

/**
 * Immutable snapshot of all persistent settings. UI and services read this;
 * they mutate settings via the typed setters on [SettingsDataStore].
 */
data class SettingsSnapshot(
    val onboardingComplete: Boolean,
    val rootGranted: Boolean,
    val shizukuGranted: Boolean,
    val activeProfileId: Long,
    val gamingModeActive: Boolean,
    val lastSafeProfileId: Long,
    val cpuTempThresholdC: Int,
    val gpuTempThresholdC: Int,
    val autoRevertOnThermal: Boolean,
    val watchdogEnabled: Boolean,
    val applyOnBoot: Boolean,
    val bootProfileId: Long,
    val vpnAutoConnect: Boolean,
    val vpnKillSwitch: Boolean,
    val vpnLastMode: String,
    val vpnPerAppPackages: List<String>,
    val dnsProvider: String,
    val customDohUrl: String,
    val privateDnsMode: String,
    val privateDnsSpecifier: String,
    val forcePeakHz: Boolean,
    val adaptiveRefresh: Boolean,
    val perAppRefreshPackages: List<String>,
    val themeMode: String,
    val dynamicColor: Boolean,
    val hapticsEnabled: Boolean,
    val pollIntervalMs: Int,
    val chartHistoryPoints: Int,
    val logRetentionDays: Int,
    val logLevel: String
)
