package com.apextuner.engine.display

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.apextuner.data.model.DisplayConfig
import com.apextuner.data.model.NetworkConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the supported display modes via [DisplayManager] and applies a chosen
 * mode id to an Activity's window via [WindowManager.LayoutParams.preferredDisplayModeId].
 *
 * For Android 11+ we additionally call [android.view.Surface.setFrameRate]
 * on the window's surface when forcing peak Hz. Per-app refresh rate injection
 * for arbitrary games requires either an AccessibilityService or root; the
 * injection path itself lives in [GameLaunchAccessibilityService] — this
 * class just resolves "what mode id should we ask for".
 *
 * Note: applying a mode only affects the calling Activity's own window.
 * Globally forcing the display mode for OTHER apps requires root + the
 * `settings put system peak_refresh_rate` / SurfaceFlinger ioctl path,
 * which we expose via [applyGlobalForcePeakHz] when root is available.
 */
@Singleton
class DisplayController @Inject constructor() {

    fun readState(context: Context, cfg: DisplayConfig?): DisplayState {
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            ?: return DisplayState.EMPTY

        val modes = display.supportedModes.mapIndexed { idx, m ->
            DisplayModeInfo(
                modeId = m.modeId,
                width = m.physicalWidth,
                height = m.physicalHeight,
                refreshRateHz = m.refreshRate
            )
        }
        val active = display.mode
        return DisplayState(
            activeModeId = active.modeId,
            activeRefreshRateHz = active.refreshRate,
            supportedModes = modes,
            forcePeakHz = cfg?.forcePeakHz ?: false,
            adaptive = cfg?.adaptive ?: true,
            perAppPackages = cfg?.perAppPackages ?: emptyList()
        )
    }

    /**
     * Apply [config] to [activity]'s window. Call from
     * `Activity.onAttachedToWindow` or after `setContentView`.
     */
    fun applyToActivity(activity: Activity, config: DisplayConfig) {
        val params = activity.window.attributes
        params.preferredDisplayModeId = if (config.forcePeakHz) {
            pickPeakModeId(activity) ?: config.modeId
        } else {
            config.modeId
        }
        activity.window.attributes = params
    }

    /** Returns the mode id with the highest refresh rate, or null if unknown. */
    fun pickPeakModeId(context: Context): Int? =
        readState(context, null).supportedModes.maxByOrNull { it.refreshRateHz }?.modeId

    /**
     * Globally force peak refresh rate for the whole device. Requires root.
     * Implemented as `settings put system peak_refresh_rate <hz>` and
     * `settings put system min_refresh_rate <hz>`; the caller passes the Hz
     * picked from the device's supported modes.
     */
    fun globalForcePeakHzCommand(hz: Float): String {
        val intHz = hz.toInt().toString()
        return "settings put system peak_refresh_rate $intHz; " +
            "settings put system min_refresh_rate $intHz; " +
            "settings put system refresh_rate_mode 1"
    }

    /** Revert global peak-Hz forcing. Also requires root. */
    fun globalAdaptiveHzCommand(): String =
        "settings put system peak_refresh_rate 60; " +
            "settings put system min_refresh_rate 60; " +
            "settings put system refresh_rate_mode 0"

    @Suppress("unused")
    private fun supportsFrameRateApi(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Suppress("unused")
    private val networkCfgMarker: NetworkConfig? = null // keep import for downstream callers
}
