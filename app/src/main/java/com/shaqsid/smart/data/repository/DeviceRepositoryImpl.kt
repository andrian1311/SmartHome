package com.shaqsid.smart.data.repository

import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DeviceRepositoryImpl : DeviceRepository {
    // Mocked data source for now since Tuya SDK needs real App/Secret Keys to initialize properly.
    private val devicesFlow = MutableStateFlow<List<SmartDevice>>(
        listOf(
            SmartDevice("1", "Living Room Light", true, false),
            SmartDevice("2", "Smart Plug", true, true),
            SmartDevice("3", "AC", false, false)
        )
    )

    override fun getDevices(): Flow<List<SmartDevice>> {
        return devicesFlow
    }

    override fun getDevice(id: String): Flow<SmartDevice?> {
        return devicesFlow.map { devices -> devices.find { it.id == id } }
    }

    override suspend fun addDevice(name: String, id: String): Result<Unit> {
        devicesFlow.update { currentList ->
            val newList = currentList.toMutableList()
            newList.add(SmartDevice(id, name, isOnline = true, isOn = false))
            newList
        }
        return Result.success(Unit)
    }

    override suspend fun updateDeviceStatus(id: String, isOn: Boolean): Result<Unit> {
        devicesFlow.update { currentList ->
            currentList.map { device ->
                if (device.id == id) device.copy(isOn = isOn) else device
            }
        }
        return Result.success(Unit)
    }

    override suspend fun removeDevice(id: String): Result<Unit> {
        devicesFlow.update { currentList ->
            currentList.filter { it.id != id }
        }
        return Result.success(Unit)
    }
}
