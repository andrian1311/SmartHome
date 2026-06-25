package com.shaqsid.smart.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.usecase.AuthUseCases
import com.shaqsid.smart.util.Countries
import com.shaqsid.smart.util.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authUseCases: AuthUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun selectCountry(country: Country) {
        countryDetected = true
        _uiState.value = _uiState.value.copy(country = country, error = null)
    }

    private var countryDetected = false

    /** Applies an auto-detected country once, unless the user already picked one. */
    fun applyDetectedCountry(country: Country) {
        if (!countryDetected) {
            countryDetected = true
            _uiState.value = _uiState.value.copy(country = country)
        }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            authUseCases.login(state.email.trim(), state.password, state.country.dialCode)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Login failed"
                    )
                }
        }
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val country: Country = Countries.DEFAULT,
    val isLoading: Boolean = false,
    val error: String? = null
)
