package com.apextuner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The signature surface of ApexTuner's UI: a translucent card with a soft
 * gradient tint, hairline border, and large rounded corners. Used for every
 * dashboard stat card, tuning panel, and chart container.
 *
 * The glassmorphism effect is achieved with a vertical alpha gradient over
 * the surface color plus a 0.5dp translucent-white border. A real blur layer
 * behind the card would require a RenderEffect (API 31+) — we keep this
 * implementation API-26-compatible by faking the depth with the gradient.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    border: Color = Color.White.copy(alpha = 0.18f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.45f))
                )
            )
            .border(BorderStroke(0.5.dp, border), RoundedCornerShape(cornerRadius))
            .padding(0.dp)
    ) {
        content()
    }
}

/** Variant with a tinted accent edge — used for active-state cards. */
@Composable
fun AccentGlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = modifier,
        border = accent.copy(alpha = 0.55f),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Suppress("unused")
private val blurMarker = Modifier.blur(8.dp)
