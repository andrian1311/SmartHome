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

    /** Current home id, needed to open the Tuya BizBundle control panel. */
    val homeId: Long get() = deviceUseCases.getCurrentHomeId()

    /** Writes a data point value (Boolean / Int / String) for any control on this device. */
    fun setControl(dpId: String, value: Any) {
        viewModelScope.launch {
            deviceUseCases.publishDp(deviceId, dpId, value)
                .onFailure { emitMessage(it.message ?: "Failed to send command") }
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

    /** Shows a one-off message (snackbar) from the UI. */
    fun notify(message: String) {
        viewModelScope.launch { emitMessage(message) }
    }

    private suspend fun emitMessage(message: String) {
        _messages.emit(message)
    }
}
