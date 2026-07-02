package com.shaqsid.smart.feature.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.usecase.AuthUseCases
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val deviceUseCases: DeviceUseCases,
    private val authUseCases: AuthUseCases
) : ViewModel() {

    val devices: StateFlow<List<SmartDevice>> = deviceUseCases.getDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isLoading: StateFlow<Boolean> = deviceUseCases.isLoading()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // One-shot messages (errors / confirmations) surfaced to the UI as a snackbar.
    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        // Load the current user's home and devices when the screen opens.
        deviceUseCases.initialize()
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authUseCases.logout()
                .onSuccess {
                    deviceUseCases.clearSession()
                    onLoggedOut()
                }
                .onFailure { emitMessage(it.message ?: "Logout failed") }
        }
    }

    fun toggleDeviceState(device: SmartDevice) {
        viewModelScope.launch {
            deviceUseCases.updateDeviceStatus(device.id, !device.isOn)
                .onFailure { emitMessage(it.message ?: "Failed to control ${device.name}") }
        }
    }

    fun removeDevice(id: String) {
        viewModelScope.launch {
            deviceUseCases.removeDevice(id)
                .onSuccess { emitMessage("Device removed") }
                .onFailure { emitMessage(it.message ?: "Failed to remove device") }
        }
    }

    fun renameDevice(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            deviceUseCases.renameDevice(id, newName)
                .onSuccess { emitMessage("Device renamed") }
                .onFailure { emitMessage(it.message ?: "Failed to rename device") }
        }
    }

    private suspend fun emitMessage(message: String) {
        _messages.emit(message)
    }
}
