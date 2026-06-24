package com.shaqsid.smart.domain.repository

import com.shaqsid.smart.domain.model.SmartDevice
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    /** Loads the logged-in user's home and devices. Safe to call on each entry to the home screen. */
    fun initialize()

    /** Clears the in-memory device/home state, e.g. after logout. */
    fun clearSession()

    fun getDevices(): Flow<List<SmartDevice>>
    fun getDevice(id: String): Flow<SmartDevice?>
    suspend fun addDevice(ssid: String, password: String): Result<Unit>
    suspend fun updateDeviceStatus(id: String, isOn: Boolean): Result<Unit>
    suspend fun renameDevice(id: String, newName: String): Result<Unit>
    suspend fun removeDevice(id: String): Result<Unit>
}
