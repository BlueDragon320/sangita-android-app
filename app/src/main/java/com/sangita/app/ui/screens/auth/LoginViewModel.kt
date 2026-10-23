package com.sangita.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.LoginRequest
import com.sangita.app.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lockoutSeconds: Int = 0,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val userPreferences: UserPreferences,
    private val networkClient: NetworkClient,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            serverUrl = userPreferences.getServerUrl(),
            username = userPreferences.getUsername() ?: "",
            isLoggedIn = !userPreferences.getToken().isNullOrBlank()
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var lockoutTimerJob: Job? = null

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, errorMessage = null) }
    }

    fun onUsernameChange(u: String) {
        _uiState.update { it.copy(username = u, errorMessage = null) }
    }

    fun onPasswordChange(p: String) {
        _uiState.update { it.copy(password = p, errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        val rawDomain = state.serverUrl.trim()
        if (rawDomain.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your website domain or server URL") }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter username and password") }
            return
        }

        // Intelligently normalize domain name (e.g. music.mysite.com -> https://music.mysite.com)
        val normalizedUrl = userPreferences.setServerUrl(rawDomain)
        _uiState.update { it.copy(serverUrl = normalizedUrl, isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val api = networkClient.createApiService(normalizedUrl)
                val response = api.login(LoginRequest(state.username.trim(), state.password))

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (!body.token.isNullOrBlank()) {
                        userPreferences.setToken(body.token)
                        userPreferences.setUsername(body.username)
                        userPreferences.setRole(body.role ?: "user")

                        // Initial library sync
                        launch { musicRepository.refreshPlaylists() }

                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = body.error ?: "Invalid response from server"
                            )
                        }
                    }
                } else if (response.code() == 429) {
                    // Lockout triggered
                    val retryAfter = response.headers()["Retry-After"]?.toIntOrNull() ?: 300
                    startLockoutCountdown(retryAfter)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Invalid credentials (HTTP ${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not connect to $normalizedUrl\n(${e.localizedMessage ?: "Network unreachable"})"
                    )
                }
            }
        }
    }

    private fun startLockoutCountdown(seconds: Int) {
        lockoutTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                lockoutSeconds = seconds,
                errorMessage = "Too many failed attempts. Cooldown active."
            )
        }
        lockoutTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(lockoutSeconds = remaining) }
            }
            _uiState.update { it.copy(errorMessage = null) }
        }
    }
}
