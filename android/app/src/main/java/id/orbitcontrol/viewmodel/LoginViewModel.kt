package id.orbitcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.orbitcontrol.data.AppPreferences
import id.orbitcontrol.data.HuaweiSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val host: String = "http://192.168.8.1", val username: String = "admin", val password: String = "",
    val saveConfig: Boolean = false, val loading: Boolean = false, val message: String? = null, val isError: Boolean = false,
)

class LoginViewModel(private val session: HuaweiSessionManager, private val preferences: AppPreferences) : ViewModel() {
    private val saved = preferences.load()
    private val _uiState = MutableStateFlow(LoginUiState(saved.host, saved.username, saveConfig = saved.saveConfig))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setHost(value: String) = _uiState.update { it.copy(host = value, message = null) }
    fun setUsername(value: String) = _uiState.update { it.copy(username = value, message = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, message = null) }
    fun setSaveConfig(value: Boolean) = _uiState.update { it.copy(saveConfig = value) }

    fun testConnection() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.update { it.copy(loading = true, message = null) }
        session.testConnection(state.host).fold(
            onSuccess = { _uiState.update { it.copy(loading = false, message = "Modem terjangkau. Silakan masuk.", isError = false) } },
            onFailure = { error -> _uiState.update { it.copy(loading = false, message = error.userMessage(), isError = true) } },
        )
    }

    fun login() = viewModelScope.launch {
        val state = _uiState.value
        if (state.password.isEmpty()) {
            _uiState.update { it.copy(message = "Password modem wajib diisi.", isError = true) }
            return@launch
        }
        _uiState.update { it.copy(loading = true, message = null) }
        session.login(state.host, state.username, state.password).fold(
            onSuccess = {
                preferences.save(state.host, state.username, state.saveConfig)
                _uiState.update { it.copy(password = "", loading = false, message = null, isError = false) }
            },
            onFailure = { error -> _uiState.update { it.copy(password = "", loading = false, message = error.userMessage(), isError = true) } },
        )
    }
}

internal fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "Terjadi kesalahan yang tidak diketahui."
