package com.apextuner.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Central registry of DataStore keys. Keeping them in one object avoids the
 * classic "two keys with the same name" footgun and makes audits easy.
 */
object PreferenceKeys {
    // Onboarding / first-run
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding.complete")
    val ROOT_GRANTED = booleanPreferencesKey("root.granted")
    val SHIZUKU_GRANTED = booleanPreferencesKey("shizuku.granted")

    // Active state
    val ACTIVE_PROFILE_ID = longPreferencesKey("active.profile.id")
    val GAMING_MODE_ACTIVE = booleanPreferencesKey("gaming.mode.active")
    val LAST_SAFE_PROFILE_ID = longPreferencesKey("last.safe.profile.id")

    // Thermal safety
    val CPU_TEMP_THRESHOLD_C = intPreferencesKey("thermal.cpu.threshold.c")     // default 75
    val GPU_TEMP_THRESHOLD_C = intPreferencesKey("thermal.gpu.threshold.c")     // default 85
    val AUTO_REVERT_ON_THERMAL = booleanPreferencesKey("thermal.auto.revert")
    val WATCHDOG_ENABLED = booleanPreferencesKey("thermal.watchdog.enabled")

    // Boot behavior
    val APPLY_ON_BOOT = booleanPreferencesKey("boot.apply")
    val BOOT_PROFILE_ID = longPreferencesKey("boot.profile.id")

    // VPN
    val VPN_AUTO_CONNECT = booleanPreferencesKey("vpn.auto.connect")
    val VPN_KILL_SWITCH = booleanPreferencesKey("vpn.kill.switch")
    val VPN_LAST_MODE = stringPreferencesKey("vpn.last.mode")
    val VPN_PER_APP_PACKAGES = stringSetPreferencesKey("vpn.per.app.packages")

    // DNS
    val DNS_PROVIDER = stringPreferencesKey("dns.provider")
    val CUSTOM_DOH_URL = stringPreferencesKey("dns.custom.doh.url")
    val PRIVATE_DNS_MODE = stringPreferencesKey("private.dns.mode")
    val PRIVATE_DNS_SPECIFIER = stringPreferencesKey("private.dns.specifier")

    // Display
    val FORCE_PEAK_HZ = booleanPreferencesKey("display.force.peak.hz")
    val ADAPTIVE_REFRESH = booleanPreferencesKey("display.adaptive.refresh")
    val PER_APP_REFRESH_PACKAGES = stringSetPreferencesKey("display.per.app.refresh")

    // UI
    val THEME_MODE = stringPreferencesKey("ui.theme.mode")              // system|light|dark|oled
    val DYNAMIC_COLOR = booleanPreferencesKey("ui.dynamic.color")
    val HAPTICS_ENABLED = booleanPreferencesKey("ui.haptics.enabled")

    // Polling
    val POLL_INTERVAL_MS = intPreferencesKey("poll.interval.ms")        // default 1000
    val CHART_HISTORY_POINTS = intPreferencesKey("chart.history.points") // default 60

    // Logging
    val LOG_RETENTION_DAYS = intPreferencesKey("log.retention.days")    // default 14
    val LOG_LEVEL = stringPreferencesKey("log.level")                   // DEBUG|INFO|WARN|ERROR
}
