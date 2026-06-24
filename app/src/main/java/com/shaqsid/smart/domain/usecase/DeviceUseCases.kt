package com.shaqsid.smart.domain.usecase

import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow

class GetDevicesUseCase(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<SmartDevice>> {
        return repository.getDevices()
    }
}

class GetDeviceUseCase(private val repository: DeviceRepository) {
    operator fun invoke(id: String): Flow<SmartDevice?> {
        return repository.getDevice(id)
    }
}

class AddDeviceUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(ssid: String, password: String): Result<Unit> {
        return repository.addDevice(ssid, password)
    }
}

class UpdateDeviceStatusUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String, isOn: Boolean): Result<Unit> {
        return repository.updateDeviceStatus(id, isOn)
    }
}

class RenameDeviceUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String, newName: String): Result<Unit> {
        return repository.renameDevice(id, newName)
    }
}

class RemoveDeviceUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.removeDevice(id)
    }
}

data class DeviceUseCases(
    val getDevices: GetDevicesUseCase,
    val getDevice: GetDeviceUseCase,
    val addDevice: AddDeviceUseCase,
    val updateDeviceStatus: UpdateDeviceStatusUseCase,
    val removeDevice: RemoveDeviceUseCase,
    val renameDevice: RenameDeviceUseCase
)
