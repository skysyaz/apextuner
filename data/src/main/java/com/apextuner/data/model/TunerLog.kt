package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * One immutable audit-log row. Every sysfs write, VPN state change, thermal
 * breach, and profile apply/rollback appends a [TunerLog] via
 * [com.apextuner.data.repository.LogRepository].
 */
@Serializable
data class TunerLog(
    val id: Long = 0L,
    val timestamp: Long,
    val level: Level,
    val category: Category,
    val message: String,
    val detail: String? = null
) {
    enum class Level { DEBUG, INFO, WARN, ERROR }
    enum class Category { CPU, GPU, DISPLAY, THERMAL, VPN, DNS, PROFILE, SYSTEM, SAFETY }
}
