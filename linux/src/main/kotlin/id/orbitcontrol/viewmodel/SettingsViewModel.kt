package id.orbitcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.data.SessionSnapshot
import id.orbitcontrol.data.repository.DebugReportRepository
import id.orbitcontrol.data.repository.InfoField
import id.orbitcontrol.data.repository.NetworkToolsRepository
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val exportingReport: Boolean = false,
    val reportMessage: String? = null,
    val reportError: Boolean = false,
    val firmwareDetailsLoading: Boolean = false,
    val firmwareDetailsLoaded: Boolean = false,
    val firmwareDetails: List<InfoField> = emptyList(),
    val firmwareDetailsMessage: String? = null,
)

class SettingsViewModel(
    private val session: HuaweiSessionManager,
    private val debugReportRepository: DebugReportRepository,
    private val networkToolsRepository: NetworkToolsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _shareReport = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val shareReport: SharedFlow<File> = _shareReport.asSharedFlow()

    fun sessionSnapshot(): SessionSnapshot = session.snapshot()

    fun exportDebugReport() {
        if (_uiState.value.exportingReport) return
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, reportMessage = "Membuat laporan...", reportError = false) }
            runCatching { debugReportRepository.export() }
                .onSuccess { file ->
                    _uiState.update {
                        it.copy(exportingReport = false, reportMessage = "Laporan siap disimpan.", reportError = false)
                    }
                    _shareReport.emit(file)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(exportingReport = false, reportMessage = "Laporan debug gagal dibuat.", reportError = true)
                    }
                }
        }
    }

    fun reportShareFailed() {
        _uiState.update { it.copy(reportMessage = "Berkas laporan tidak dapat disimpan.", reportError = true) }
    }

    fun loadFirmwareDetails() {
        if (_uiState.value.firmwareDetailsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(firmwareDetailsLoading = true, firmwareDetailsMessage = null) }
            runCatching { networkToolsRepository.getBandSnapshot() }
                .onSuccess { snapshot ->
                    val sessionState = session.snapshot()
                    val sessionFields = listOf(
                        InfoField(
                            label = "Status autentikasi",
                            value = if (session.authenticated.value) "Aktif" else "Tidak aktif",
                            source = "session",
                        ),
                        InfoField(
                            label = "SessionID",
                            value = if (sessionState.hasSessionId) "Tersedia (nilai disembunyikan)" else "Tidak tersedia",
                            source = "session",
                        ),
                        InfoField(
                            label = "Token",
                            value = if (sessionState.hasVerificationToken) "Tersedia (nilai disembunyikan)" else "Tidak tersedia",
                            source = "session",
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            firmwareDetailsLoading = false,
                            firmwareDetailsLoaded = true,
                            firmwareDetails = snapshot.bandInfo.technicalFields + sessionFields,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            firmwareDetailsLoading = false,
                            firmwareDetailsLoaded = true,
                            firmwareDetailsMessage = "Detail firmware belum dapat dibaca.",
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { session.logout() }
    }
}
