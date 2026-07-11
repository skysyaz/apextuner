package com.apextuner.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ApexTuner's theme. Three modes:
 *  - "system" → follow platform dark/light
 *  - "dark"   → force dark
 *  - "oled"   → force pure-black surfaces (AMOLED-friendly)
 *
 * When [dynamicColor] is true (default on Android 12+) the palette is taken
 * from the user's wallpaper via Monet; otherwise we fall back to the brand
 * purple palette defined in [Color.kt].
 */
private val Color_White = androidx.compose.ui.graphics.Color.White
private val Color_AlmostBlack = androidx.compose.ui.graphics.Color(0xFF1A1A24)

private val ApexDark = darkColorScheme(
    primary = ApexPurple,
    onPrimary = Color_White,
    primaryContainer = ApexPurpleContainer,
    secondary = ApexMint,
    tertiary = ApexCoral,
    error = ApexRed,
    background = ApexBgDark,
    surface = ApexSurfaceDark,
    onBackground = Color_White,
    onSurface = Color_White
)

private val ApexOled = ApexDark.copy(
    background = ApexBgOled,
    surface = ApexBgOled
)

private val ApexLight = lightColorScheme(
    primary = ApexPurpleDark,
    onPrimary = Color_White,
    primaryContainer = ApexPurpleContainer,
    secondary = ApexMint,
    tertiary = ApexCoral,
    error = ApexRed,
    background = ApexSurfaceLight,
    onBackground = Color_AlmostBlack,
    onSurface = Color_AlmostBlack
)

@Composable
fun ApexTunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        oledMode -> ApexOled
        darkTheme -> ApexDark
        else -> ApexLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content
    )
}
