package com.shaqsid.smart.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.usecase.AuthUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authUseCases: AuthUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateCountryCode(value: String) {
        _uiState.value = _uiState.value.copy(countryCode = value, error = null)
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun updateCode(value: String) {
        _uiState.value = _uiState.value.copy(code = value, error = null)
    }

    fun sendCode() {
        val state = _uiState.value
        if (state.email.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null, message = null)
            authUseCases.sendRegisterCode(state.email.trim(), state.countryCode.trim())
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        codeSent = true,
                        message = "Verification code sent to ${state.email.trim()}"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Failed to send code"
                    )
                }
        }
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank() || state.code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null, message = null)
            authUseCases.register(
                state.email.trim(),
                state.password,
                state.code.trim(),
                state.countryCode.trim()
            )
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Registration failed"
                    )
                }
        }
    }
}

data class RegisterUiState(
    val countryCode: String = "1",
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
