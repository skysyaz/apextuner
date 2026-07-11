package com.apextuner.data.repository

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.datastore.SettingsSnapshot
import com.apextuner.data.model.NetworkConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over [SettingsDataStore] exposing typed enums where useful.
 * ViewModels depend on this rather than the raw DataStore so we can swap the
 * backing store in tests.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore
) {
    val snapshot: Flow<SettingsSnapshot> = store.snapshot

    suspend fun setOnboardingComplete(v: Boolean) = store.setOnboardingComplete(v)
    suspend fun setRootGranted(v: Boolean) = store.setRootGranted(v)
    suspend fun setShizukuGranted(v: Boolean) = store.setShizukuGranted(v)
    suspend fun setActiveProfileId(id: Long) = store.setActiveProfileId(id)
    suspend fun setGamingModeActive(v: Boolean) = store.setGamingModeActive(v)
    suspend fun setLastSafeProfileId(id: Long) = store.setLastSafeProfileId(id)

    suspend fun setCpuTempThreshold(c: Int) = store.setCpuTempThreshold(c)
    suspend fun setGpuTempThreshold(c: Int) = store.setGpuTempThreshold(c)
    suspend fun setAutoRevertOnThermal(v: Boolean) = store.setAutoRevertOnThermal(v)
    suspend fun setWatchdogEnabled(v: Boolean) = store.setWatchdogEnabled(v)

    suspend fun setApplyOnBoot(v: Boolean) = store.setApplyOnBoot(v)
    suspend fun setBootProfileId(id: Long) = store.setBootProfileId(id)

    suspend fun setVpnAutoConnect(v: Boolean) = store.setVpnAutoConnect(v)
    suspend fun setVpnKillSwitch(v: Boolean) = store.setVpnKillSwitch(v)
    suspend fun setVpnMode(mode: NetworkConfig.VpnMode) = store.setVpnLastMode(mode)
    suspend fun setVpnPerAppPackages(pkgs: List<String>) = store.setVpnPerAppPackages(pkgs)

    suspend fun setDnsProvider(p: NetworkConfig.DnsProvider) = store.setDnsProvider(p)
    suspend fun setCustomDohUrl(url: String) = store.setCustomDohUrl(url)
    suspend fun setPrivateDnsMode(m: NetworkConfig.PrivateDnsMode) = store.setPrivateDnsMode(m)
    suspend fun setPrivateDnsSpecifier(s: String) = store.setPrivateDnsSpecifier(s)

    suspend fun setForcePeakHz(v: Boolean) = store.setForcePeakHz(v)
    suspend fun setAdaptiveRefresh(v: Boolean) = store.setAdaptiveRefresh(v)
    suspend fun setPerAppRefreshPackages(pkgs: List<String>) = store.setPerAppRefreshPackages(pkgs)

    suspend fun setThemeMode(mode: String) = store.setThemeMode(mode)
    suspend fun setDynamicColor(v: Boolean) = store.setDynamicColor(v)
    suspend fun setHapticsEnabled(v: Boolean) = store.setHapticsEnabled(v)
    suspend fun setPollIntervalMs(ms: Int) = store.setPollIntervalMs(ms)
    suspend fun setChartHistoryPoints(n: Int) = store.setChartHistoryPoints(n)
    suspend fun setLogRetentionDays(d: Int) = store.setLogRetentionDays(d)
    suspend fun setLogLevel(level: String) = store.setLogLevel(level)
}
