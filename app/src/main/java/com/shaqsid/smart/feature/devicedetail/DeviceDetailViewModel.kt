package com.shaqsid.smart.feature.devicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    private val deviceUseCases: DeviceUseCases,
    val deviceId: String
) : ViewModel() {

    val device: StateFlow<SmartDevice?> = deviceUseCases.getDevice(deviceId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun toggle(isOn: Boolean) {
        viewModelScope.launch {
            deviceUseCases.updateDeviceStatus(deviceId, isOn)
                .onFailure { emitMessage(it.message ?: "Failed to control device") }
        }
    }

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            deviceUseCases.renameDevice(deviceId, newName)
                .onSuccess { emitMessage("Device renamed") }
                .onFailure { emitMessage(it.message ?: "Failed to rename device") }
        }
    }

    fun remove(onRemoved: () -> Unit) {
        viewModelScope.launch {
            deviceUseCases.removeDevice(deviceId)
                .onSuccess { onRemoved() }
                .onFailure { emitMessage(it.message ?: "Failed to remove device") }
        }
    }

    private suspend fun emitMessage(message: String) {
        _messages.emit(message)
    }
}
