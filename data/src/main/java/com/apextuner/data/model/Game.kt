package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * A user-selected game with an associated profile. Detected at runtime by
 * [com.apextuner.engine.gaming.GameDetector] (UsageStats / Accessibility).
 */
@Serializable
data class Game(
    val packageName: String,
    val label: String,
    val profileId: Long = Profile.DEFAULT_ID_BALANCED,
    val enabled: Boolean = true,
    val installedAt: Long = 0L,
    val lastLaunchedAt: Long = 0L
)
