package com.apextuner.engine.cpu

/**
 * Canonical sysfs paths for the cpufreq subsystem. Linux exposes one
 * `cpuN/cpufreq` directory per logical core; clustering is inferred from
 * shared `related_cpus` and (optionally) `frequency_table` content.
 *
 * All paths are absolute. The shell layer is responsible for quoting.
 */
object CpuPaths {
    private const val CPU_ROOT = "/sys/devices/system/cpu"

    fun cpusDir() = "$CPU_ROOT"

    fun onlineFile() = "$CPU_ROOT/online"

    fun cpuDir(cpuId: Int) = "$CPU_ROOT/cpu$cpuId"

    fun onlineFile(cpuId: Int) = "$CPU_ROOT/cpu$cpuId/online"

    fun cpufreqDir(cpuId: Int) = "$CPU_ROOT/cpu$cpuId/cpufreq"

    fun scalingGovernor(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_governor"

    fun scalingMinFreq(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_min_freq"

    fun scalingMaxFreq(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_max_freq"

    fun scalingCurFreq(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_cur_freq"

    fun availableGovernors(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_available_governors"

    fun availableFrequencies(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_available_frequencies"

    fun relatedCpus(cpuId: Int) = "${cpufreqDir(cpuId)}/related_cpus"

    fun frequencyTable(cpuId: Int) = "${cpufreqDir(cpuId)}/scaling_available_frequencies"

    fun timeInState(cpuId: Int) = "${cpufreqDir(cpuId)}/time_in_state"

    /** /proc/stat first column (name) + 10 jiffy counters. */
    val procStat = "/proc/stat"

    /** Thermal zone temp helpers live in [com.apextuner.engine.thermal.ThermalPaths]. */
}
