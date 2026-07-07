package com.shaqsid.smart.domain.repository

import com.shaqsid.smart.domain.model.DeviceSchedule
import com.shaqsid.smart.domain.model.PairingMode
import com.shaqsid.smart.domain.model.SmartDevice
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    /** Loads the logged-in user's home and devices. Safe to call on each entry to the home screen. */
    fun initialize()

    /** Clears the in-memory device/home state, e.g. after logout. */
    fun clearSession()

    fun getDevices(): Flow<List<SmartDevice>>

    /** True while the home/device list is being fetched from Tuya. */
    fun isLoading(): Flow<Boolean>

    fun getDevice(id: String): Flow<SmartDevice?>
    /** Pairs a Wi-Fi device using EZ (SmartConfig) or AP (hotspot) mode. */
    suspend fun addDevice(ssid: String, password: String, mode: PairingMode): Result<Unit>

    /** Pairs a device from a scanned QR code's content. */
    suspend fun addDeviceByQrCode(qrContent: String): Result<Unit>
    suspend fun updateDeviceStatus(id: String, isOn: Boolean): Result<Unit>

    /** Writes an arbitrary data point (DP) value; [value] must be a Boolean, Int, or String. */
    suspend fun publishDp(deviceId: String, dpId: String, value: Any): Result<Unit>
    suspend fun renameDevice(id: String, newName: String): Result<Unit>

    /** Renames a single control/data point (e.g. one gang of a multi-switch) via its DP id. */
    suspend fun renameControl(deviceId: String, dpId: String, newName: String): Result<Unit>
    suspend fun removeDevice(id: String): Result<Unit>

    // --- Scheduled tasks (timers) ---
    suspend fun getSchedules(deviceId: String): Result<List<DeviceSchedule>>
    suspend fun addSchedule(
        deviceId: String,
        time: String,
        loops: String,
        dpId: String,
        turnOn: Boolean
    ): Result<Unit>
    suspend fun updateSchedule(
        deviceId: String,
        scheduleId: String,
        time: String,
        loops: String,
        dpId: String,
        turnOn: Boolean
    ): Result<Unit>
    suspend fun setScheduleEnabled(deviceId: String, scheduleId: String, enabled: Boolean): Result<Unit>
    suspend fun deleteSchedule(deviceId: String, scheduleId: String): Result<Unit>
}
