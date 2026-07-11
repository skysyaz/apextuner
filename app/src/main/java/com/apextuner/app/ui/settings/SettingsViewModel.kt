package com.apextuner.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.datastore.SettingsSnapshot
import com.apextuner.data.repository.SettingsRepository
import com.apextuner.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val snapshot: SettingsSnapshot? = null,
    val logCount: Int = 0,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val logs: LogRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.snapshot.collectLatest { s -> _state.value = _state.value.copy(snapshot = s) }
        }
        viewModelScope.launch {
            logs.observeRecent(1000).collectLatest { l ->
                _state.value = _state.value.copy(logCount = l.size)
            }
        }
    }

    fun setTheme(mode: String) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { repo.setHapticsEnabled(v) }
    fun setCpuThreshold(c: Int) = viewModelScope.launch { repo.setCpuTempThreshold(c) }
    fun setGpuThreshold(c: Int) = viewModelScope.launch { repo.setGpuTempThreshold(c) }
    fun setAutoRevert(v: Boolean) = viewModelScope.launch { repo.setAutoRevertOnThermal(v) }
    fun setWatchdog(v: Boolean) = viewModelScope.launch { repo.setWatchdogEnabled(v) }
    fun setApplyOnBoot(v: Boolean) = viewModelScope.launch { repo.setApplyOnBoot(v) }
    fun setBootProfile(id: Long) = viewModelScope.launch { repo.setBootProfileId(id) }
    fun setPollInterval(ms: Int) = viewModelScope.launch { repo.setPollIntervalMs(ms) }
    fun setChartPoints(n: Int) = viewModelScope.launch { repo.setChartHistoryPoints(n) }
    fun setLogRetention(d: Int) = viewModelScope.launch { repo.setLogRetentionDays(d) }
    fun setLogLevel(level: String) = viewModelScope.launch { repo.setLogLevel(level) }
    fun clearLogs() = viewModelScope.launch {
        logs.clear(); _state.value = _state.value.copy(message = "Logs cleared")
    }
}
