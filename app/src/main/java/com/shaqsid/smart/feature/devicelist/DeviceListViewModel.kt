package com.shaqsid.smart.feature.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.usecase.DeviceUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {

    val devices: StateFlow<List<SmartDevice>> = deviceUseCases.getDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleDeviceState(device: SmartDevice) {
        viewModelScope.launch {
            deviceUseCases.updateDeviceStatus(device.id, !device.isOn)
        }
    }

    fun removeDevice(id: String) {
        viewModelScope.launch {
            deviceUseCases.removeDevice(id)
        }
    }
}
