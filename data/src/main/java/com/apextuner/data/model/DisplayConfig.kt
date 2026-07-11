package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * Display / refresh-rate configuration. [modeId] maps to
 * [android.view.Display.Mode.getModeId]. -1 means "let the system decide".
 */
@Serializable
data class DisplayConfig(
    val modeId: Int,                     // preferredDisplayModeId
    val refreshRateHz: Float,            // resolved refresh rate (60, 90, 120, 144...)
    val forcePeakHz: Boolean,            // global "Force Peak Hz" toggle
    val adaptive: Boolean,               // system-managed refresh rate
    val batterySaverHz: Boolean,         // clamp to lowest mode on battery saver
    val perAppPackages: List<String> = emptyList() // games that should auto-apply [modeId]
)
