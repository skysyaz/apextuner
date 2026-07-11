package com.apextuner.vpn

import android.content.Context
import android.provider.Settings
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.root.ShellSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Toggles Android's system-wide Private DNS setting.
 *
 *  - [NetworkConfig.PrivateDnsMode.OFF]      → opportunistic (lets system decide)
 *  - [NetworkConfig.PrivateDnsMode.AUTO]     → "Automatic" (uses network-provided DoT)
 *  - [NetworkConfig.PrivateDnsMode.STRICT]   → "Strict" with [specifier] as hostname
 *  - [NetworkConfig.PrivateDnsMode.HOSTNAME] → same as STRICT (legacy name)
 *
 * Writing `Settings.Global.PRIVATE_DNS_*` requires the WRITE_SECURE_SETTINGS
 * permission, which we obtain via root (libsu) or via Shizuku. On devices
 * with neither, [apply] returns false and the UI shows a permission banner.
 *
 * Reading the current state is always possible (the keys are world-readable).
 */
@Singleton
class PrivateDnsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selector: ShellSelector,
    private val logs: LogRepository
) {
    fun readCurrent(): PrivateDnsState {
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrDefault(null) ?: "off"
        val specifier = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        }.getOrDefault(null) ?: ""
        return PrivateDnsState(
            modeRaw = mode,
            specifier = specifier,
            mapped = when (mode.lowercase()) {
                "off", "opportunistic" -> NetworkConfig.PrivateDnsMode.OFF
                "auto" -> NetworkConfig.PrivateDnsMode.AUTO
                "strict", "hostname", "provider_hostname" -> NetworkConfig.PrivateDnsMode.STRICT
                else -> NetworkConfig.PrivateDnsMode.OFF
            }
        )
    }

    suspend fun apply(mode: NetworkConfig.PrivateDnsMode, specifier: String): Boolean {
        val shell = selector.bestForSecureSettings() ?: return false
        val modeArg = when (mode) {
            NetworkConfig.PrivateDnsMode.OFF -> "off"
            NetworkConfig.PrivateDnsMode.AUTO -> "auto"
            NetworkConfig.PrivateDnsMode.STRICT, NetworkConfig.PrivateDnsMode.HOSTNAME -> "hostname"
        }
        val script = buildString {
            append("settings put global private_dns_mode $modeArg;")
            if (mode == NetworkConfig.PrivateDnsMode.STRICT || mode == NetworkConfig.PrivateDnsMode.HOSTNAME) {
                append(" settings put global private_dns_specifier '$specifier';")
            } else {
                append(" settings delete global private_dns_specifier;")
            }
            append(" exit 0")
        }
        val result = shell.execScript(script)
        val ok = result.isSuccess
        logs.log(
            level = if (ok) TunerLog.Level.INFO else TunerLog.Level.ERROR,
            category = TunerLog.Category.DNS,
            message = if (ok) "Private DNS set to $mode ($specifier)"
                      else "Failed to set Private DNS: ${result.stderrText}",
            detail = script
        )
        return ok
    }
}

data class PrivateDnsState(
    val modeRaw: String,
    val specifier: String,
    val mapped: NetworkConfig.PrivateDnsMode
)
