package com.shaqsid.smart

import com.shaqsid.smart.data.repository.DeviceRepositoryImpl
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.shaqsid.smart.domain.usecase.*

class AppContainer {
    val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl()
    }

    val deviceUseCases: DeviceUseCases by lazy {
        DeviceUseCases(
            getDevices = GetDevicesUseCase(deviceRepository),
            getDevice = GetDeviceUseCase(deviceRepository),
            addDevice = AddDeviceUseCase(deviceRepository),
            updateDeviceStatus = UpdateDeviceStatusUseCase(deviceRepository),
            removeDevice = RemoveDeviceUseCase(deviceRepository)
        )
    }
}
