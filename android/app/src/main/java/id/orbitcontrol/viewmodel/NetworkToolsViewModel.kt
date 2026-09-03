package id.orbitcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.orbitcontrol.data.repository.BandLockResult
import id.orbitcontrol.data.repository.BandLockSupport
import id.orbitcontrol.data.repository.DiagnosisResult
import id.orbitcontrol.data.repository.NetworkBandSnapshot
import id.orbitcontrol.data.repository.NetworkStatisticsSnapshot
import id.orbitcontrol.data.repository.NetworkToolsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkToolsUiState(
    val bandLoading: Boolean = false,
    val bandSnapshot: NetworkBandSnapshot = NetworkBandSnapshot(),
    val selectedBands: Set<Int> = emptySet(),
    val bandLockChecking: Boolean = false,
    val bandLockApplying: Boolean = false,
    val bandLockSupport: BandLockSupport = BandLockSupport(),
    val bandLockResult: BandLockResult? = null,
    val statisticsLoading: Boolean = false,
    val statisticsSnapshot: NetworkStatisticsSnapshot = NetworkStatisticsSnapshot(),
    val diagnosisTarget: String = "1.1.1.1",
    val diagnosisRunning: Boolean = false,
    val diagnosisResult: DiagnosisResult? = null,
    val diagnosisError: String? = null,
)

class NetworkToolsViewModel(
    private val repository: NetworkToolsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkToolsUiState())
    val uiState: StateFlow<NetworkToolsUiState> = _uiState.asStateFlow()
    private var postApplyPollingJob: Job? = null

    init {
        refreshBand()
        refreshStatistics()
        viewModelScope.launch {
            delay(1_000L)
            checkBandLockSupport()
        }
    }

    fun refreshBand() {
        viewModelScope.launch { refreshBandNow() }
    }

    fun toggleBand(number: Int) {
        _uiState.update { state ->
            val next = if (number in state.selectedBands) emptySet() else setOf(number)
            state.copy(selectedBands = next)
        }
    }

    fun checkBandLockSupport() {
        if (_uiState.value.bandLockChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(bandLockChecking = true, bandLockResult = null) }
            runCatching { repository.checkBandLockSupport() }
                .onSuccess { support ->
                    _uiState.update { it.copy(bandLockChecking = false, bandLockSupport = support) }
                    refreshBand()
                }
                .onFailure { _uiState.update { it.copy(bandLockChecking = false) } }
        }
    }

    fun applyBandLock() {
        val state = _uiState.value
        if (state.bandLockApplying || !state.bandLockSupport.canApply || state.selectedBands.isEmpty()) return
        viewModelScope.launch {
            val target = _uiState.value.selectedBands
            _uiState.update { it.copy(bandLockApplying = true, bandLockResult = null) }
            val applyResult = runCatching { repository.applyBandLock(target) }
            _uiState.update { current ->
                current.copy(
                    bandLockApplying = false,
                    bandLockResult = applyResult.getOrElse {
                        BandLockResult(
                            success = false,
                            requestedBands = target.sorted(),
                            requestedLteBand = null,
                            message = "Band lock gagal diterapkan.",
                        )
                    },
                )
            }
            refreshBandNow()
            schedulePostApplyPolling()
        }
    }

    fun refreshStatistics() {
        if (_uiState.value.statisticsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(statisticsLoading = true) }
            val snapshot = repository.getStatisticsSnapshot()
            _uiState.update { it.copy(statisticsLoading = false, statisticsSnapshot = snapshot) }
        }
    }

    fun updateDiagnosisTarget(value: String) {
        _uiState.update { it.copy(diagnosisTarget = value) }
    }

    fun useQuickDiagnosisTarget(value: String) {
        _uiState.update { it.copy(diagnosisTarget = value) }
    }

    fun runDiagnosis() {
        if (_uiState.value.diagnosisRunning) return
        viewModelScope.launch {
            val target = _uiState.value.diagnosisTarget
            _uiState.update { it.copy(diagnosisRunning = true, diagnosisError = null, diagnosisResult = null) }
            runCatching { repository.runDiagnosis(target) }
                .onSuccess { result -> _uiState.update { it.copy(diagnosisRunning = false, diagnosisResult = result) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            diagnosisRunning = false,
                            diagnosisError = error.message ?: "Diagnosis gagal dijalankan.",
                        )
                    }
                }
        }
    }

    private suspend fun refreshBandNow() {
        if (_uiState.value.bandLoading) return
        _uiState.update { it.copy(bandLoading = true) }
        val snapshot = repository.getBandSnapshot()
        _uiState.update { current ->
            val candidates = snapshot.bandInfo.supportedBands.map { it.band }.toSet()
            current.copy(
                bandLoading = false,
                bandSnapshot = snapshot,
                selectedBands = current.selectedBands.filter { it in candidates }.toSet(),
            )
        }
    }

    private fun schedulePostApplyPolling() {
        postApplyPollingJob?.cancel()
        postApplyPollingJob = viewModelScope.launch {
            var previousCheckpoint = 0L
            POST_APPLY_POLL_SECONDS.forEach { checkpoint ->
                delay((checkpoint - previousCheckpoint) * 1_000L)
                refreshBandNow()
                previousCheckpoint = checkpoint
            }
        }
    }

    companion object {
        private val POST_APPLY_POLL_SECONDS = listOf(5L, 10L, 20L, 30L)
    }
}
