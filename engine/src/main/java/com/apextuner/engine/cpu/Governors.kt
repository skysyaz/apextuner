package com.apextuner.engine.cpu

/**
 * Canonical CPU governor names recognized by the Linux cpufreq framework.
 * The list is informational — the actual availability is read from
 * `scaling_available_governors` per cluster.
 */
object Governors {
    const val PERFORMANCE = "performance"
    const val POWERSAVE = "powersave"
    const val USERSPACE = "userspace"
    const val ONDEMAND = "ondemand"
    const val CONSERVATIVE = "conservative"
    const val SCHEDUTIL = "schedutil"
    const val INTERACTIVE = "interactive"
    const val WALT = "walt"

    /** Governors ApexTuner considers "high performance". */
    val performanceSet = setOf(PERFORMANCE, INTERACTIVE)

    /** Governors ApexTuner considers "balanced". */
    val balancedSet = setOf(SCHEDUTIL, ONDEMAND, WALT)

    /** Governors ApexTuner considers "power saving". */
    val powerSaveSet = setOf(POWERSAVE, CONSERVATIVE)

    fun classify(governor: String): String = when (governor.lowercase()) {
        in performanceSet -> "performance"
        in balancedSet -> "balanced"
        in powerSaveSet -> "power_save"
        else -> "unknown"
    }

    val allKnown: List<String> = listOf(
        PERFORMANCE, INTERACTIVE, SCHEDUTIL, ONDEMAND, CONSERVATIVE, POWERSAVE, USERSPACE, WALT
    )
}
