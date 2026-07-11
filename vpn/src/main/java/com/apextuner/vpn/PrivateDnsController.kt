package com.apextuner.vpn

import android.content.Context
import android.content.Intent
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
 * Writing `Settings.Global.PRIVATE_DNS_*` requires WRITE_SECURE_SETTINGS
 * (root, Shizuku, or `adb grant`). Without that, [apply] returns false and
 * the UI should fall back to VPN DNS-only mode or [openSystemPrivateDnsSettings].
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

    /**
     * Opens the system network/wireless settings so the user can set Private
     * DNS manually — the no-root path for system-wide DNS.
     */
    fun openSystemPrivateDnsSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.PRIVATE_DNS_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    suspend fun apply(mode: NetworkConfig.PrivateDnsMode, specifier: String): Boolean {
        val modeArg = when (mode) {
            NetworkConfig.PrivateDnsMode.OFF -> "off"
            NetworkConfig.PrivateDnsMode.AUTO -> "auto"
            NetworkConfig.PrivateDnsMode.STRICT, NetworkConfig.PrivateDnsMode.HOSTNAME -> "hostname"
        }

        // 1) Direct ContentResolver write — works if WRITE_SECURE_SETTINGS already granted via ADB.
        val directOk = runCatching {
            val cr = context.contentResolver
            Settings.Global.putString(cr, "private_dns_mode", modeArg)
            if (mode == NetworkConfig.PrivateDnsMode.STRICT || mode == NetworkConfig.PrivateDnsMode.HOSTNAME) {
                Settings.Global.putString(cr, "private_dns_specifier", specifier)
            }
            true
        }.getOrDefault(false)
        if (directOk) {
            logs.log(TunerLog.Level.INFO, TunerLog.Category.DNS, "Private DNS set to $mode ($specifier) via Settings.Global")
            return true
        }

        // 2) Root / Shizuku shell
        val shell = selector.bestForSecureSettings() ?: return false
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
