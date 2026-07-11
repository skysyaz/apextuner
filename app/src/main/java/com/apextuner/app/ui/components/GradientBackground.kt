package com.apextuner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Subtle vertical gradient background. Used behind the dashboard so the
 * glassmorphic cards have something to refract. Pure black on OLED mode
 * (handled by the theme selecting [com.apextuner.app.ui.theme.ApexBgOled]).
 */
@Composable
fun GradientBackground(
    top: Color,
    bottom: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        content()
    }
}
