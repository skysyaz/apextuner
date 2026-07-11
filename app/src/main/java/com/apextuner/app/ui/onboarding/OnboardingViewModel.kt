package com.apextuner.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { WELCOME, ROOT, SHIZUKU, USAGE, ACCESSIBILITY, VPN, BATTERY, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val hasUsageAccess: Boolean = false,
    val hasAccessibility: Boolean = false,
    val vpnConsentGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val rootAvailability: RootAvailability,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init { viewModelScope.launch { recheck() } }

    fun goto(step: OnboardingStep) {
        _state.value = _state.value.copy(step = step)
    }

    fun next() {
        val cur = _state.value.step
        val nextStep = OnboardingStep.values()
            .getOrElse(cur.ordinal + 1) { OnboardingStep.DONE }
        _state.value = _state.value.copy(step = nextStep)
    }

    suspend fun recheck() {
        val caps = rootAvailability.probe()
        _state.value = _state.value.copy(caps = caps)
    }

    fun setUsageAccess(v: Boolean) { _state.value = _state.value.copy(hasUsageAccess = v) }
    fun setAccessibility(v: Boolean) { _state.value = _state.value.copy(hasAccessibility = v) }
    fun setVpnConsent(v: Boolean) { _state.value = _state.value.copy(vpnConsentGranted = v) }
    fun setBatteryOptimizationIgnored(v: Boolean) {
        _state.value = _state.value.copy(batteryOptimizationIgnored = v)
    }

    suspend fun finish() {
        settings.setOnboardingComplete(true)
        _state.value = _state.value.copy(step = OnboardingStep.DONE)
    }
}
