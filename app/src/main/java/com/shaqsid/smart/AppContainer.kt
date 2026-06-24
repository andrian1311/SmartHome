package com.shaqsid.smart

import android.content.Context
import com.shaqsid.smart.data.repository.DeviceRepositoryImpl
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.shaqsid.smart.domain.usecase.*

class AppContainer(private val context: Context) {
    val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(context)
    }

    val deviceUseCases: DeviceUseCases by lazy {
        DeviceUseCases(
            getDevices = GetDevicesUseCase(deviceRepository),
            getDevice = GetDeviceUseCase(deviceRepository),
            addDevice = AddDeviceUseCase(deviceRepository),
            updateDeviceStatus = UpdateDeviceStatusUseCase(deviceRepository),
            removeDevice = RemoveDeviceUseCase(deviceRepository),
            renameDevice = RenameDeviceUseCase(deviceRepository)
        )
    }
}
