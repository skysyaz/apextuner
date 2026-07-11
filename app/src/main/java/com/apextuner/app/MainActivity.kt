package com.apextuner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apextuner.app.ui.navigation.ApexNavHost
import com.apextuner.app.ui.theme.ApexTunerTheme
import com.apextuner.data.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val snap = settings.snapshot.collectAsState(initial = null).value
            ApexTunerTheme(
                darkTheme = when (snap?.themeMode) {
                    "dark", "oled" -> true
                    "light" -> false
                    else -> true // system default; Compose resolves isSystemInDarkTheme()
                },
                oledMode = snap?.themeMode == "oled",
                dynamicColor = snap?.dynamicColor ?: true
            ) {
                ApexNavHost(
                    onboardingComplete = snap?.onboardingComplete ?: false
                )
            }
        }
    }
}
