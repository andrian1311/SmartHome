package com.shaqsid.smart

import android.content.Context
import com.shaqsid.smart.data.repository.AuthRepositoryImpl
import com.shaqsid.smart.data.repository.DeviceRepositoryImpl
import com.shaqsid.smart.domain.repository.AuthRepository
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.shaqsid.smart.domain.usecase.*

class AppContainer(private val context: Context) {

    private val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl()
    }

    val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(context)
    }

    val authUseCases: AuthUseCases by lazy {
        AuthUseCases(
            isLoggedIn = IsLoggedInUseCase(authRepository),
            sendRegisterCode = SendRegisterCodeUseCase(authRepository),
            register = RegisterUseCase(authRepository),
            login = LoginUseCase(authRepository),
            sendResetPasswordCode = SendResetPasswordCodeUseCase(authRepository),
            resetPassword = ResetPasswordUseCase(authRepository),
            logout = LogoutUseCase(authRepository)
        )
    }

    val deviceUseCases: DeviceUseCases by lazy {
        DeviceUseCases(
            initialize = InitializeDevicesUseCase(deviceRepository),
            clearSession = ClearDeviceSessionUseCase(deviceRepository),
            getDevices = GetDevicesUseCase(deviceRepository),
            isLoading = IsDevicesLoadingUseCase(deviceRepository),
            getDevice = GetDeviceUseCase(deviceRepository),
            addDevice = AddDeviceUseCase(deviceRepository),
            addDeviceByQrCode = AddDeviceByQrCodeUseCase(deviceRepository),
            updateDeviceStatus = UpdateDeviceStatusUseCase(deviceRepository),
            publishDp = PublishDpUseCase(deviceRepository),
            removeDevice = RemoveDeviceUseCase(deviceRepository),
            renameDevice = RenameDeviceUseCase(deviceRepository),
            getSchedules = GetSchedulesUseCase(deviceRepository),
            addSchedule = AddScheduleUseCase(deviceRepository),
            updateSchedule = UpdateScheduleUseCase(deviceRepository),
            setScheduleEnabled = SetScheduleEnabledUseCase(deviceRepository),
            deleteSchedule = DeleteScheduleUseCase(deviceRepository)
        )
    }
}
