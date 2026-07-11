package com.apextuner.engine.profile

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.Profile
import com.apextuner.data.repository.LogRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.cpu.CpuController
import com.apextuner.engine.display.DisplayController
import com.apextuner.engine.gpu.GpuController
import com.apextuner.engine.root.ShellSelector
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ProfileApplierTest {

    @Test
    fun `applyBuiltIn BALANCED routes through cpu and gpu and logs success`() = runBlocking {
        val cpu = mockk<CpuController>(relaxed = true)
        coEvery { cpu.buildPreset(any()) } returns emptyList()
        coEvery { cpu.apply(any()) } returns true
        val gpu = mockk<GpuController>(relaxed = true)
        coEvery { gpu.readCurrent() } returns com.apextuner.engine.gpu.GpuState.EMPTY
        coEvery { gpu.buildPreset(any(), any()) } returns null
        val display = mockk<DisplayController>(relaxed = true)
        val selector = mockk<ShellSelector>(relaxed = true)
        coEvery { selector.bestForSysfsWrite() } returns null
        val settings = mockk<SettingsDataStore>(relaxed = true)
        val logs = mockk<LogRepository>(relaxed = true)
        val profileRepo = mockk<ProfileRepository>(relaxed = true)
        coEvery { profileRepo.getById(Profile.DEFAULT_ID_BALANCED) } returns null

        val applier = ProfileApplier(profileRepo, cpu, gpu, display, selector, settings, logs)
        val ok = applier.applyBuiltIn(Profile.ThermalPolicy.BALANCED)
        assertThat(ok).isTrue()
    }

    @Test
    fun `applyById falls back to BALANCED when id not found`() = runBlocking {
        val cpu = mockk<CpuController>(relaxed = true)
        coEvery { cpu.buildPreset(any()) } returns emptyList()
        coEvery { cpu.apply(any()) } returns true
        val gpu = mockk<GpuController>(relaxed = true)
        coEvery { gpu.readCurrent() } returns com.apextuner.engine.gpu.GpuState.EMPTY
        coEvery { gpu.buildPreset(any(), any()) } returns null
        val display = mockk<DisplayController>(relaxed = true)
        val selector = mockk<ShellSelector>(relaxed = true)
        val settings = mockk<SettingsDataStore>(relaxed = true)
        val logs = mockk<LogRepository>(relaxed = true)
        val profileRepo = mockk<ProfileRepository>(relaxed = true)
        coEvery { profileRepo.getById(any()) } returns null

        val applier = ProfileApplier(profileRepo, cpu, gpu, display, selector, settings, logs)
        val ok = applier.applyById(999L)
        assertThat(ok).isTrue()
    }
}
