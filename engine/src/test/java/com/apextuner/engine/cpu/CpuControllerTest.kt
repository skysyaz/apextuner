package com.apextuner.engine.cpu

import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.data.model.Profile
import com.apextuner.engine.root.ShellExecutor
import com.apextuner.engine.root.ShellResult
import com.apextuner.engine.root.ShellSelector
import com.apextuner.engine.safety.RollbackManager
import com.apextuner.data.repository.LogRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Unit tests for [CpuController]. Uses a fake [ShellExecutor] so we can verify
 * the transactional write/verify/rollback contract without touching real sysfs.
 */
class CpuControllerTest {

    @Test
    fun `buildPreset MAX_PERFORMANCE sets performance governor and max freq`() = runBlocking {
        val controller = newController(fakeShellWithCluster())
        val preset = controller.buildPreset(Profile.ThermalPolicy.MAX_PERFORMANCE)
        assertThat(preset).hasSize(1)
        val cluster = preset.first()
        assertThat(cluster.governor).isEqualTo("performance")
        assertThat(cluster.maxFreqKHz).isEqualTo(1_800_000L)
        assertThat(cluster.minFreqKHz).isEqualTo(1_800_000L)
        assertThat(cluster.onlineMask).containsExactly(true, true, true, true)
    }

    @Test
    fun `buildPreset POWER_SAVE halves max freq`() = runBlocking {
        val controller = newController(fakeShellWithCluster())
        val preset = controller.buildPreset(Profile.ThermalPolicy.POWER_SAVE)
        val cluster = preset.first()
        assertThat(cluster.governor).isEqualTo("conservative")
        assertThat(cluster.maxFreqKHz).isEqualTo(900_000L) // 1_800_000 / 2
    }

    @Test
    fun `buildPreset POWER_SAVE littleOnly disables other clusters`() = runBlocking {
        val controller = newController(fakeShellWithTwoClusters())
        val preset = controller.buildPreset(Profile.ThermalPolicy.POWER_SAVE, littleOnly = true)
        assertThat(preset).hasSize(2)
        assertThat(preset[0].onlineMask).containsExactly(true, true, true, true) // little stays on
        assertThat(preset[1].onlineMask).containsExactly(false, false, false, false) // big off
    }

    @Test
    fun `buildPreset BALANCED uses schedutil with full freq range`() = runBlocking {
        val controller = newController(fakeShellWithCluster())
        val preset = controller.buildPreset(Profile.ThermalPolicy.BALANCED)
        val cluster = preset.first()
        assertThat(cluster.governor).isEqualTo("schedutil")
        assertThat(cluster.minFreqKHz).isEqualTo(300_000L)
        assertThat(cluster.maxFreqKHz).isEqualTo(1_800_000L)
    }

    @Test
    fun `buildPreset falls back when preferred governor unavailable`() = runBlocking {
        val controller = newController(fakeShellWithCluster(governors = listOf("ondemand")))
        val preset = controller.buildPreset(Profile.ThermalPolicy.MAX_PERFORMANCE)
        // performance not available → should fall back to ondemand via fallback list
        assertThat(preset.first().governor).isEqualTo("ondemand")
    }

    private fun newController(shell: ShellExecutor): CpuController {
        val selector = mockk<ShellSelector>()
        coEvery { selector.best() } returns shell
        coEvery { selector.bestForSysfsWrite() } returns shell
        val logs = mockk<LogRepository>(relaxed = true)
        val rollback = RollbackManager(logs)
        val topology = CpuTopology()
        return CpuController(selector, topology, rollback)
    }

    private fun fakeShellWithCluster(
        governors: List<String> = listOf("performance", "schedutil", "conservative")
    ): ShellExecutor {
        val shell = mockk<ShellExecutor>()
        every { shell.isPrivileged } returns true
        coEvery { shell.readFile(CpuPaths.onlineFile()) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(0)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(1)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(2)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(3)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.scalingGovernor(any())) } returns "schedutil"
        coEvery { shell.readFile(CpuPaths.scalingMinFreq(any())) } returns "300000"
        coEvery { shell.readFile(CpuPaths.scalingMaxFreq(any())) } returns "1800000"
        coEvery { shell.readFile(CpuPaths.availableGovernors(any())) } returns governors.joinToString(" ")
        coEvery { shell.readFile(CpuPaths.availableFrequencies(any())) } returns "300000 1800000"
        coEvery { shell.readFile(CpuPaths.onlineFile(0)) } returns "1"
        coEvery { shell.readFile(CpuPaths.onlineFile(1)) } returns "1"
        coEvery { shell.readFile(CpuPaths.onlineFile(2)) } returns "1"
        coEvery { shell.readFile(CpuPaths.onlineFile(3)) } returns "1"
        coEvery { shell.writeFile(any(), any()) } returns true
        coEvery { shell.exec(any()) } returns ShellResult.EMPTY
        return shell
    }

    private fun fakeShellWithTwoClusters(): ShellExecutor {
        val shell = mockk<ShellExecutor>()
        every { shell.isPrivileged } returns true
        coEvery { shell.readFile(CpuPaths.onlineFile()) } returns "0-7"
        // cluster 0: cpu0-3
        coEvery { shell.readFile(CpuPaths.relatedCpus(0)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(1)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(2)) } returns "0-3"
        coEvery { shell.readFile(CpuPaths.relatedCpus(3)) } returns "0-3"
        // cluster 1: cpu4-7
        coEvery { shell.readFile(CpuPaths.relatedCpus(4)) } returns "4-7"
        coEvery { shell.readFile(CpuPaths.relatedCpus(5)) } returns "4-7"
        coEvery { shell.readFile(CpuPaths.relatedCpus(6)) } returns "4-7"
        coEvery { shell.readFile(CpuPaths.relatedCpus(7)) } returns "4-7"

        coEvery { shell.readFile(CpuPaths.scalingGovernor(0)) } returns "schedutil"
        coEvery { shell.readFile(CpuPaths.scalingGovernor(4)) } returns "schedutil"
        coEvery { shell.readFile(CpuPaths.scalingMinFreq(0)) } returns "300000"
        coEvery { shell.readFile(CpuPaths.scalingMaxFreq(0)) } returns "1800000"
        coEvery { shell.readFile(CpuPaths.scalingMinFreq(4)) } returns "600000"
        coEvery { shell.readFile(CpuPaths.scalingMaxFreq(4)) } returns "2800000"
        coEvery { shell.readFile(CpuPaths.availableGovernors(any())) } returns "performance schedutil conservative"
        coEvery { shell.readFile(CpuPaths.availableFrequencies(any())) } returns "300000 1800000"
        coEvery { shell.readFile(CpuPaths.onlineFile(any())) } returns "1"
        coEvery { shell.writeFile(any(), any()) } returns true
        return shell
    }
}
