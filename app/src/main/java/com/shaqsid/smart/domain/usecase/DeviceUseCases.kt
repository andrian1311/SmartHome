package com.shaqsid.smart.domain.usecase

import com.shaqsid.smart.domain.model.DeviceSchedule
import com.shaqsid.smart.domain.model.PairingMode
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow

class InitializeDevicesUseCase(private val repository: DeviceRepository) {
    operator fun invoke() = repository.initialize()
}

class ClearDeviceSessionUseCase(private val repository: DeviceRepository) {
    operator fun invoke() = repository.clearSession()
}

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
    suspend operator fun invoke(ssid: String, password: String, mode: PairingMode): Result<Unit> {
        return repository.addDevice(ssid, password, mode)
    }
}

class AddDeviceByQrCodeUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(qrContent: String): Result<Unit> {
        return repository.addDeviceByQrCode(qrContent)
    }
}

class UpdateDeviceStatusUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String, isOn: Boolean): Result<Unit> {
        return repository.updateDeviceStatus(id, isOn)
    }
}

class PublishDpUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(deviceId: String, dpId: String, value: Any): Result<Unit> {
        return repository.publishDp(deviceId, dpId, value)
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

class GetSchedulesUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(deviceId: String): Result<List<DeviceSchedule>> =
        repository.getSchedules(deviceId)
}

class AddScheduleUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(
        deviceId: String,
        time: String,
        loops: String,
        dpId: String,
        turnOn: Boolean
    ): Result<Unit> = repository.addSchedule(deviceId, time, loops, dpId, turnOn)
}

class SetScheduleEnabledUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(deviceId: String, scheduleId: String, enabled: Boolean): Result<Unit> =
        repository.setScheduleEnabled(deviceId, scheduleId, enabled)
}

class DeleteScheduleUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(deviceId: String, scheduleId: String): Result<Unit> =
        repository.deleteSchedule(deviceId, scheduleId)
}

data class DeviceUseCases(
    val initialize: InitializeDevicesUseCase,
    val clearSession: ClearDeviceSessionUseCase,
    val getDevices: GetDevicesUseCase,
    val getDevice: GetDeviceUseCase,
    val addDevice: AddDeviceUseCase,
    val addDeviceByQrCode: AddDeviceByQrCodeUseCase,
    val updateDeviceStatus: UpdateDeviceStatusUseCase,
    val publishDp: PublishDpUseCase,
    val removeDevice: RemoveDeviceUseCase,
    val renameDevice: RenameDeviceUseCase,
    val getSchedules: GetSchedulesUseCase,
    val addSchedule: AddScheduleUseCase,
    val setScheduleEnabled: SetScheduleEnabledUseCase,
    val deleteSchedule: DeleteScheduleUseCase
)
