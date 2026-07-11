package com.apextuner.engine.thermal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThermalZoneTest {
    @Test
    fun matchesRealCpuZoneNames() {
        assertThat(ThermalZone(0, "cpu-thermal", 45f).isCpu).isTrue()
        assertThat(ThermalZone(1, "soc-thermal", 48f).isCpu).isTrue()
        assertThat(ThermalZone(2, "cpu", 40f).isCpu).isTrue()
    }

    @Test
    fun matchesRealGpuZoneNames() {
        assertThat(ThermalZone(3, "gpu-thermal-0", 55f).isGpu).isTrue()
        assertThat(ThermalZone(4, "gpuss", 52f).isGpu).isTrue()
    }

    @Test
    fun batteryExactAndContains() {
        assertThat(ThermalZone(5, "battery", 32f).isBattery).isTrue()
        assertThat(ThermalZone(6, "battery-thermal", 33f).isBattery).isTrue()
    }
}
