package com.apextuner.data

import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.data.model.DisplayConfig
import com.apextuner.data.model.GpuConfig
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.Profile
import com.apextuner.data.model.ProfileSerializer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Round-trip serialization tests for [ProfileSerializer]. These guard the
 * stable JSON shape used by the import/export feature — any field rename or
 * removal must be accompanied by a migration test.
 */
class ProfileSerializerTest {

    @Test
    fun `empty profile round-trips`() {
        val p = Profile(id = 1, name = "Test", description = "desc")
        val json = ProfileSerializer.encode(p)
        val back = ProfileSerializer.decode(json)
        assertThat(back).isEqualTo(p)
    }

    @Test
    fun `profile with cpu gpu display network round-trips`() {
        val p = Profile(
            id = 42,
            name = "Gaming",
            description = "Max perf",
            iconKey = "rocket",
            cpu = listOf(
                CpuClusterConfig(
                    clusterId = 0, label = "Little", cores = listOf(0, 1, 2, 3),
                    governor = "schedutil", minFreqKHz = 300_000L, maxFreqKHz = 1_800_000L,
                    onlineMask = listOf(true, true, true, true),
                    availableGovernors = listOf("performance", "schedutil"),
                    availableFrequencies = listOf(300_000L, 1_800_000L)
                )
            ),
            gpu = GpuConfig(
                socFamily = "adreno", sysfsRoot = "/sys/class/kgsl/kgsl-3d0",
                governor = "msm-adreno-tz", minClockMhz = 0, maxClockMhz = 850
            ),
            display = DisplayConfig(
                modeId = 2, refreshRateHz = 120f, forcePeakHz = true,
                adaptive = false, batterySaverHz = false
            ),
            network = NetworkConfig(
                vpnMode = NetworkConfig.VpnMode.DNS_ONLY,
                dnsProvider = NetworkConfig.DnsProvider.CLOUDFLARE,
                killSwitch = true,
                privateDnsMode = NetworkConfig.PrivateDnsMode.STRICT,
                privateDnsSpecifier = "cloudflare-dns.com"
            ),
            thermalPolicy = Profile.ThermalPolicy.MAX_PERFORMANCE,
            triggerPackages = listOf("com.example.game")
        )
        val json = ProfileSerializer.encode(p)
        val back = ProfileSerializer.decode(json)
        assertThat(back).isEqualTo(p)
    }

    @Test
    fun `list round-trips`() {
        val list = listOf(
            Profile(id = 1, name = "A"),
            Profile(id = 2, name = "B"),
            Profile(id = 3, name = "C")
        )
        val json = ProfileSerializer.encodeList(list)
        val back = ProfileSerializer.decodeList(json)
        assertThat(back).isEqualTo(list)
    }

    @Test
    fun `unknown keys are ignored on decode`() {
        val base = Profile(id = 1, name = "Test")
        val json = ProfileSerializer.encode(base)
            .replace("\"name\":\"Test\"", "\"name\":\"Test\",\"futureField\":\"ignored\"")
        val back = ProfileSerializer.decode(json)
        assertThat(back.name).isEqualTo("Test")
    }

    @Test
    fun `all thermal policies serialize`() {
        Profile.ThermalPolicy.values().forEach { policy ->
            val p = Profile(id = 0, name = policy.name, thermalPolicy = policy)
            val back = ProfileSerializer.decode(ProfileSerializer.encode(p))
            assertThat(back.thermalPolicy).isEqualTo(policy)
        }
    }
}
