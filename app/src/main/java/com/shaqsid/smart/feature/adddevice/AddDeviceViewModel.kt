package com.shaqsid.smart.feature.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.model.PairingMode
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

    fun selectMode(mode: PairingMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null)
    }

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

    fun updateQrContent(qrContent: String) {
        _uiState.value = _uiState.value.copy(qrContent = qrContent, error = null)
    }

    fun addDevice(onSuccess: () -> Unit) {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = when (state.mode) {
                PairingMode.QR -> {
                    if (state.qrContent.isBlank()) {
                        _uiState.value = state.copy(isLoading = false)
                        return@launch
                    }
                    deviceUseCases.addDeviceByQrCode(state.qrContent.trim())
                }
                else -> {
                    if (state.ssid.isBlank()) {
                        _uiState.value = state.copy(isLoading = false)
                        return@launch
                    }
                    deviceUseCases.addDevice(state.ssid.trim(), state.password, state.mode)
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
            result
                .onSuccess { onSuccess() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = it.message ?: "Failed to add device")
                }
        }
    }
}

data class AddDeviceUiState(
    val mode: PairingMode = PairingMode.EZ,
    val ssid: String = "",
    val password: String = "",
    val qrContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /** Whether the inputs required by the selected [mode] are filled in. */
    val canSubmit: Boolean
        get() = when (mode) {
            PairingMode.QR -> qrContent.isNotBlank()
            else -> ssid.isNotBlank()
        }
}
