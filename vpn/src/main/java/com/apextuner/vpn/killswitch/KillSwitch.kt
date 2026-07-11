package com.apextuner.vpn.killswitch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.root.ShellSelector
import com.apextuner.engine.root.ShellExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The kill switch is implemented in two layers:
 *
 *  1. **Soft layer** (always available): sets `VpnService.Builder.allowBypass(false)`
 *     so apps cannot escape the tunnel while it is up. This is done by
 *     [ApexVpnService] when it builds its Builder — no separate code path.
 *
 *  2. **Hard layer** (root only): when the tunnel drops unexpectedly, we
 *     install iptables/nftables rules that reject all OUTPUT traffic except
 *     to the WireGuard endpoint. This catches the race between the VPN dying
 *     and the system re-routing traffic in the clear. Rules are torn down on
 *     the next successful tunnel establish or explicit disable.
 *
 * The watchdog polls connectivity every 1 s and arms the hard layer the moment
 * it detects that the active network has no VPN transport.
 */
@Singleton
class KillSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selector: ShellSelector,
    private val logs: LogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _armed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _armed.asStateFlow()
    private var running = false
    private var wireGuardEndpoint: String? = null

    fun start(wireGuardEndpoint: String?) {
        if (running) return
        running = true
        this.wireGuardEndpoint = wireGuardEndpoint
        scope.launch { loop() }
    }

    fun stop() {
        running = false
        scope.launch { disarmHardLayer() }
    }

    private suspend fun loop() {
        while (running) {
            val hasVpn = hasVpnTransport()
            if (!hasVpn && !_armed.value) {
                armHardLayer()
            } else if (hasVpn && _armed.value) {
                disarmHardLayer()
            }
            delay(1000L)
        }
        disarmHardLayer()
    }

    private fun hasVpnTransport(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private suspend fun armHardLayer() {
        val shell = selector.bestForSysfsWrite() ?: return // root-only feature
        val endpoint = wireGuardEndpoint?.substringBefore(':') ?: return
        val script = buildScript(arm = true, endpoint = endpoint)
        val result = shell.execScript(script)
        if (result.isSuccess) {
            _armed.value = true
            logs.log(
                level = TunerLog.Level.WARN,
                category = TunerLog.Category.VPN,
                message = "Kill switch armed (VPN dropped)",
                detail = "endpoint=$endpoint"
            )
        }
    }

    private suspend fun disarmHardLayer() {
        if (!_armed.value) return
        val shell = selector.bestForSysfsWrite() ?: run { _armed.value = false; return }
        val result = shell.execScript(buildScript(arm = false, endpoint = null))
        if (result.isSuccess) {
            _armed.value = false
            logs.log(
                level = TunerLog.Level.INFO,
                category = TunerLog.Category.VPN,
                message = "Kill switch disarmed"
            )
        }
    }

    /**
     * iptables/nftables script. Uses the ApexTuner chain so we never touch the
     * user's existing rules. The arm script: create chain → jump from OUTPUT →
     * allow loopback + the WG endpoint → reject everything else.
     */
    private fun buildScript(arm: Boolean, endpoint: String?): String = if (arm) {
        """
        iptables -N ApexTuner-KS 2>/dev/null
        iptables -C OUTPUT -j ApexTuner-KS 2>/dev/null || iptables -A OUTPUT -j ApexTuner-KS
        iptables -F ApexTuner-KS
        iptables -A ApexTuner-KS -o lo -j RETURN
        iptables -A ApexTuner-KS -d $endpoint -j RETURN
        iptables -A ApexTuner-KS -j REJECT --reject-with icmp-admin-prohibited
        ip6tables -N ApexTuner-KS6 2>/dev/null
        ip6tables -C OUTPUT -j ApexTuner-KS6 2>/dev/null || ip6tables -A OUTPUT -j ApexTuner-KS6
        ip6tables -F ApexTuner-KS6
        ip6tables -A ApexTuner-KS6 -o lo -j RETURN
        ip6tables -A ApexTuner-KS6 -j REJECT
        exit 0
        """.trimIndent()
    } else {
        """
        iptables -D OUTPUT -j ApexTuner-KS 2>/dev/null
        iptables -F ApexTuner-KS 2>/dev/null
        iptables -X ApexTuner-KS 2>/dev/null
        ip6tables -D OUTPUT -j ApexTuner-KS6 2>/dev/null
        ip6tables -F ApexTuner-KS6 2>/dev/null
        ip6tables -X ApexTuner-KS6 2>/dev/null
        exit 0
        """.trimIndent()
    }

    @Suppress("unused")
    private val shellMarker: ShellExecutor? = null
}
