package id.orbitcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.orbitcontrol.data.repository.DeviceRepository
import id.orbitcontrol.data.repository.SignalRepository
import id.orbitcontrol.data.repository.StatusRepository
import id.orbitcontrol.data.repository.TrafficRepository
import id.orbitcontrol.domain.DashboardSnapshot
import id.orbitcontrol.domain.EndpointValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class DashboardUiState(val loading: Boolean = true, val snapshot: DashboardSnapshot = DashboardSnapshot(), val refreshedAt: Long? = null)

class DashboardViewModel(
    private val status: StatusRepository, private val signal: SignalRepository,
    private val traffic: TrafficRepository, private val devices: DeviceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()
    init { viewModelScope.launch { while (true) { refreshInternal(); delay(5_000) } } }
    fun refresh() { viewModelScope.launch { refreshInternal() } }
    private suspend fun refreshInternal() {
        if (!refreshMutex.tryLock()) return
        try {
            _uiState.value = _uiState.value.copy(loading = true)
            val snapshot = DashboardSnapshot(
                session = EndpointValue(status.sessionSummary()),
                signal = endpoint { signal.getSignal() },
                status = endpoint { status.getStatus() },
                operator = endpoint { status.getOperator() },
                network = endpoint { status.getNetworkInfo() },
                traffic = endpoint { traffic.getTraffic() },
                devices = endpoint { devices.getDevices() },
            )
            _uiState.value = DashboardUiState(false, snapshot, System.currentTimeMillis())
        } finally {
            refreshMutex.unlock()
        }
    }
    private suspend fun <T> endpoint(block: suspend () -> T): EndpointValue<T> = runCatching { block() }
        .fold({ EndpointValue(value = it) }, { EndpointValue(error = it.userMessage()) })
}
