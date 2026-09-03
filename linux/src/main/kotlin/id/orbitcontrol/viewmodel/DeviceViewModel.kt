package id.orbitcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.orbitcontrol.data.repository.DeviceRepository
import id.orbitcontrol.domain.ConnectedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceUiState(val loading: Boolean = true, val devices: List<ConnectedDevice> = emptyList(), val error: String? = null)
class DeviceViewModel(private val repository: DeviceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        _uiState.value = runCatching { repository.getDevices() }
            .fold({ DeviceUiState(false, it) }, { DeviceUiState(false, error = it.userMessage()) })
    }
}
