package com.apextuner.engine.display

import com.apextuner.data.model.DisplayConfig

/**
 * One row in the supported display-modes list. Wraps
 * [android.view.Display.Mode] for testability (the platform class is final).
 */
data class DisplayModeInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
) {
    val label: String get() = "${width}x${height} @ ${refreshRateHz.toInt()}Hz"
}

/**
 * Live display state — what the dashboard binds to.
 */
data class DisplayState(
    val activeModeId: Int,
    val activeRefreshRateHz: Float,
    val supportedModes: List<DisplayModeInfo>,
    val forcePeakHz: Boolean,
    val adaptive: Boolean,
    val perAppPackages: List<String>
) {
    fun toConfig(): DisplayConfig = DisplayConfig(
        modeId = activeModeId,
        refreshRateHz = activeRefreshRateHz,
        forcePeakHz = forcePeakHz,
        adaptive = adaptive,
        batterySaverHz = false,
        perAppPackages = perAppPackages
    )

    val peakHz: Float get() = supportedModes.maxOfOrNull { it.refreshRateHz } ?: activeRefreshRateHz

    companion object {
        val EMPTY = DisplayState(
            activeModeId = -1, activeRefreshRateHz = 60f,
            supportedModes = emptyList(), forcePeakHz = false, adaptive = true,
            perAppPackages = emptyList()
        )
    }
}
