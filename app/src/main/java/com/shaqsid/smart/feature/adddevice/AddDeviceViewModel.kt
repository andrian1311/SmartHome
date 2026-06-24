package com.shaqsid.smart.feature.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AddDeviceViewModel(
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    fun updateDeviceName(name: String) {
        _uiState.value = _uiState.value.copy(deviceName = name)
    }

    fun addDevice(onSuccess: () -> Unit) {
        val currentName = _uiState.value.deviceName
        if (currentName.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Generate a random ID for the mock
            val newId = UUID.randomUUID().toString()
            val result = deviceUseCases.addDevice(currentName, newId)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(error = "Failed to add device")
            }
        }
    }
}

data class AddDeviceUiState(
    val deviceName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
