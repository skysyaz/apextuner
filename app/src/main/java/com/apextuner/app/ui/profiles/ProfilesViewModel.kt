package com.apextuner.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.model.Profile
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.profile.ProfileApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val activeId: Long = 0L,
    val message: String? = null
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val applier: ProfileApplier
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.seedBuiltIns()
            repo.observeAll().collect { ps ->
                _state.value = _state.value.copy(profiles = ps)
            }
        }
    }

    fun apply(id: Long) = viewModelScope.launch {
        val ok = applier.applyById(id)
        _state.value = _state.value.copy(activeId = id,
            message = if (ok) "Applied" else "Apply failed — see logs")
    }

    fun create(name: String, description: String) = viewModelScope.launch {
        runCatching { repo.create(Profile(id = 0, name = name, description = description)) }
            .onSuccess { _state.value = _state.value.copy(message = "Created '$name'") }
            .onFailure { _state.value = _state.value.copy(message = "Create failed: ${it.message}") }
    }

    fun duplicate(id: Long, newName: String) = viewModelScope.launch {
        runCatching { repo.duplicate(id, newName) }
            .onSuccess { _state.value = _state.value.copy(message = "Duplicated as '$newName'") }
    }

    fun delete(id: Long) = viewModelScope.launch {
        val ok = repo.delete(id)
        _state.value = _state.value.copy(message = if (ok) "Deleted" else "Cannot delete built-in profile")
    }

    fun export(): String = repo.export(_state.value.profiles)

    fun import(payload: String) = viewModelScope.launch {
        runCatching {
            val incoming = repo.import(payload)
            incoming.forEach { p -> repo.create(p.copy(id = 0, name = p.name + " (imported)")) }
            _state.value = _state.value.copy(message = "Imported ${incoming.size} profiles")
        }.onFailure { _state.value = _state.value.copy(message = "Import failed: ${it.message}") }
    }
}
