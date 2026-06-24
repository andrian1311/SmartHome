package com.shaqsid.smart.feature.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddDeviceViewModel(
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    /** Pre-fills the SSID from the connected Wi-Fi without overwriting anything the user typed. */
    fun prefillSsid(ssid: String) {
        if (ssid.isNotBlank() && _uiState.value.ssid.isBlank()) {
            _uiState.value = _uiState.value.copy(ssid = ssid)
        }
    }

    fun updateSsid(ssid: String) {
        _uiState.value = _uiState.value.copy(ssid = ssid, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun addDevice(onSuccess: () -> Unit) {
        val currentSsid = _uiState.value.ssid
        val currentPassword = _uiState.value.password
        if (currentSsid.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = deviceUseCases.addDevice(currentSsid, currentPassword)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(error = result.exceptionOrNull()?.message ?: "Failed to add device")
            }
        }
    }
}

data class AddDeviceUiState(
    val ssid: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
