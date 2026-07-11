package com.apextuner.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Lightweight haptic helper. Wraps Compose's [LocalHapticFeedback] so every
 * toggle in the app gets consistent feedback. Honors the user's
 * "hapticsEnabled" setting — callers check [enabled] before invoking [tap].
 *
 * On Android 10+ we could use VibrationEffect predefined effects, but
 * HapticFeedbackType is enough for taps and the API is stable back to API 1.
 */
class Haptics(
    val enabled: Boolean,
    private val perform: (HapticFeedbackType) -> Unit
) {
    fun tap() {
        if (!enabled) return
        perform(HapticFeedbackType.TextHandleMove)
    }
    fun confirm() {
        if (!enabled) return
        perform(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean = true): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(enabled) {
        Haptics(enabled) { type -> feedback.performHapticFeedback(type) }
    }
}
