package com.shaqsid.smart.feature.devicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.model.DeviceSchedule
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _schedules = MutableStateFlow<List<DeviceSchedule>>(emptyList())
    val schedules: StateFlow<List<DeviceSchedule>> = _schedules.asStateFlow()

    fun loadSchedules() {
        viewModelScope.launch {
            deviceUseCases.getSchedules(deviceId)
                .onSuccess { _schedules.value = it }
                .onFailure { emitMessage(it.message ?: "Failed to load schedules") }
        }
    }

    fun addSchedule(time: String, loops: String, dpId: String, turnOn: Boolean) {
        viewModelScope.launch {
            deviceUseCases.addSchedule(deviceId, time, loops, dpId, turnOn)
                .onSuccess { emitMessage("Schedule added"); loadSchedules() }
                .onFailure { emitMessage(it.message ?: "Failed to add schedule") }
        }
    }

    fun setScheduleEnabled(scheduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            deviceUseCases.setScheduleEnabled(deviceId, scheduleId, enabled)
                .onSuccess { loadSchedules() }
                .onFailure { emitMessage(it.message ?: "Failed to update schedule") }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            deviceUseCases.deleteSchedule(deviceId, scheduleId)
                .onSuccess { emitMessage("Schedule deleted"); loadSchedules() }
                .onFailure { emitMessage(it.message ?: "Failed to delete schedule") }
        }
    }

    /** Writes a data point value (Boolean / Int / String) for any control on this device. */
    fun setControl(dpId: String, value: Any) {
        viewModelScope.launch {
            deviceUseCases.publishDp(deviceId, dpId, value)
                .onFailure { emitMessage(it.message ?: "Failed to send command") }
        }
    }

    /** Toggles a switch; if it has a running countdown, also cancels that countdown. */
    fun toggleSwitch(control: com.shaqsid.smart.domain.model.DeviceControl.Switch, on: Boolean) {
        setControl(control.dpId, on)
        if (control.countdownDpId != null && control.countdownSeconds > 0) {
            setControl(control.countdownDpId, 0)
        }
    }

    /** Sets a switch's countdown in seconds (0 turns the countdown off). */
    fun setCountdown(countdownDpId: String, seconds: Int) {
        setControl(countdownDpId, seconds)
    }

    /** Starts panning/tilting the camera in [direction]; no-op if the camera has no PTZ. */
    fun ptzMove(direction: com.shaqsid.smart.domain.model.PtzDirection) {
        val ptz = device.value?.ptz ?: return
        setControl(ptz.controlDpId, direction.dpValue)
    }

    /** Stops an in-progress pan/tilt; no-op if the camera exposes no `ptz_stop` DP. */
    fun ptzStop() {
        val stopDpId = device.value?.ptz?.stopDpId ?: return
        setControl(stopDpId, true)
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
