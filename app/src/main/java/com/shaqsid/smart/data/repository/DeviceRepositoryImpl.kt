package com.shaqsid.smart.data.repository

import android.content.Context
import android.util.Log
import com.shaqsid.smart.domain.model.DeviceControl
import com.shaqsid.smart.domain.model.PairingMode
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.sdk.api.IThingActivatorGetToken
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.enums.ActivatorModelEnum
import com.thingclips.smart.home.sdk.builder.ActivatorBuilder
import com.thingclips.smart.home.sdk.builder.ThingQRCodeActivatorBuilder
import com.thingclips.smart.home.sdk.api.IThingHomeStatusListener
import com.thingclips.smart.sdk.bean.GroupBean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

    private val devicesFlow = MutableStateFlow<List<SmartDevice>>(emptyList())
    private var currentHomeId: Long = 0L
    private val TAG = "DeviceRepositoryImpl"

    override fun initialize() {
        if (!ThingHomeSdk.getUserInstance().isLogin) {
            clearSession()
            return
        }
        // Already set up for this session: just pull the latest device state.
        if (currentHomeId != 0L) {
            refreshDevices()
        } else {
            fetchHomeList()
        }
    }

    override fun clearSession() {
        currentHomeId = 0L
        devicesFlow.value = emptyList()
    }

    private fun fetchHomeList() {
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
            override fun onSuccess(homeBeans: List<HomeBean>?) {
                if (homeBeans.isNullOrEmpty()) {
                    createDefaultHome()
                } else {
                    setupHome(homeBeans[0].homeId)
                }
            }

            override fun onError(errorCode: String?, errorMsg: String?) {
                Log.e(TAG, "Query Home List Error: $errorCode $errorMsg")
            }
        })
    }

    private fun createDefaultHome() {
        ThingHomeSdk.getHomeManagerInstance().createHome(
            "My Smart Home", 0.0, 0.0, "", emptyList(),
            object : IThingHomeResultCallback {
                override fun onSuccess(homeBean: HomeBean?) {
                    homeBean?.homeId?.let { setupHome(it) }
                }

                override fun onError(errorCode: String?, errorMsg: String?) {
                    Log.e(TAG, "Create Home Error: $errorCode $errorMsg")
                }
            }
        )
    }

    private fun setupHome(homeId: Long) {
        currentHomeId = homeId
        val homeInstance = ThingHomeSdk.newHomeInstance(homeId)

        homeInstance.registerHomeStatusListener(object : IThingHomeStatusListener {
            override fun onDeviceAdded(devId: String?) {
                refreshDevices()
            }

            override fun onDeviceRemoved(devId: String?) {
                refreshDevices()
            }

            override fun onGroupAdded(groupId: Long) {}

            override fun onGroupRemoved(groupId: Long) {}

            override fun onMeshAdded(meshId: String?) {}
        })

        homeInstance.getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                updateDevicesFlow(homeBean?.deviceList)
            }

            override fun onError(errorCode: String?, errorMsg: String?) {
                Log.e(TAG, "Get Home Detail Error: $errorCode $errorMsg")
            }
        })
    }

    private fun refreshDevices() {
        if (currentHomeId == 0L) return
        ThingHomeSdk.newHomeInstance(currentHomeId).getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                updateDevicesFlow(homeBean?.deviceList)
            }

            override fun onError(errorCode: String?, errorMsg: String?) {
                Log.e(TAG, "Refresh Devices Error: $errorCode $errorMsg")
            }
        })
    }

    private fun updateDevicesFlow(tuyaDevices: List<DeviceBean>?) {
        val smartDevices = tuyaDevices?.map { deviceBean ->
            // DP "1" is the standard switch data point for most Tuya devices
            val isOn = deviceBean.dps?.get("1") as? Boolean ?: false
            SmartDevice(
                id = deviceBean.devId,
                name = deviceBean.name ?: "Unknown Device",
                isOnline = deviceBean.isOnline,
                isOn = isOn,
                controls = parseControls(deviceBean)
            )
        } ?: emptyList()
        devicesFlow.value = smartDevices
    }

    /**
     * Maps a device's Tuya schema + current DP values into typed [DeviceControl]s the UI can render.
     * Unsupported/complex types (raw, bitmap, struct) are skipped.
     */
    private fun parseControls(deviceBean: DeviceBean): List<DeviceControl> {
        val schemaMap = deviceBean.schemaMap ?: return emptyList()
        val dps = deviceBean.dps ?: emptyMap()

        return schemaMap.values
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { schema ->
                val dpId = schema.id ?: return@mapNotNull null
                val name = schema.name?.takeIf { it.isNotBlank() } ?: "DP $dpId"
                val editable = schema.mode?.contains("w") ?: false
                val value = dps[dpId]

                val property = runCatching { JSONObject(schema.property ?: "{}") }.getOrNull() ?: JSONObject()
                val type = property.optString("type").ifBlank { schema.type ?: "" }

                when (type) {
                    "bool" -> DeviceControl.Switch(
                        dpId, name, editable, on = value as? Boolean ?: false
                    )

                    "value" -> DeviceControl.Numeric(
                        dpId, name, editable,
                        current = (value as? Number)?.toInt() ?: property.optInt("min"),
                        min = property.optInt("min", 0),
                        max = property.optInt("max", 100),
                        step = property.optInt("step", 1).coerceAtLeast(1),
                        scale = property.optInt("scale", 0),
                        unit = property.optString("unit")
                    )

                    "enum" -> {
                        val options = property.optJSONArray("range")?.let { arr ->
                            (0 until arr.length()).map { arr.optString(it) }
                        } ?: emptyList()
                        DeviceControl.Enumeration(
                            dpId, name, editable,
                            current = value?.toString() ?: options.firstOrNull().orEmpty(),
                            options = options
                        )
                    }

                    "string" -> DeviceControl.Text(
                        dpId, name, editable, current = value?.toString().orEmpty()
                    )

                    else -> null
                }
            }
    }

    override fun getDevices(): Flow<List<SmartDevice>> = devicesFlow

    override fun getDevice(id: String): Flow<SmartDevice?> =
        devicesFlow.map { devices -> devices.find { it.id == id } }

    override suspend fun addDevice(ssid: String, password: String, mode: PairingMode): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            if (currentHomeId == 0L) {
                continuation.resume(Result.failure(Exception("Home not initialized. Please wait and try again.")))
                return@suspendCancellableCoroutine
            }

            val activatorModel = when (mode) {
                PairingMode.AP -> ActivatorModelEnum.THING_AP
                else -> ActivatorModelEnum.THING_EZ
            }

            ThingHomeSdk.getActivatorInstance().getActivatorToken(
                currentHomeId,
                object : IThingActivatorGetToken {
                    override fun onSuccess(token: String?) {
                        val builder = ActivatorBuilder()
                            .setContext(context)
                            .setSsid(ssid)
                            .setPassword(password)
                            .setActivatorModel(activatorModel)
                            .setTimeOut(100)
                            .setToken(token)
                            .setListener(object : IThingSmartActivatorListener {
                                override fun onError(errorCode: String?, errorMsg: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            Result.failure(Exception("Pairing failed: $errorMsg"))
                                        )
                                    }
                                }

                                override fun onActiveSuccess(devResp: DeviceBean?) {
                                    if (continuation.isActive) {
                                        continuation.resume(Result.success(Unit))
                                    }
                                    refreshDevices()
                                }

                                override fun onStep(step: String?, data: Any?) {
                                    Log.d(TAG, "Pairing step: $step")
                                }
                            })

                        val activator = ThingHomeSdk.getActivatorInstance().newActivator(builder)
                        activator?.start()

                        continuation.invokeOnCancellation {
                            activator?.stop()
                        }
                    }

                    override fun onFailure(errorCode: String?, errorMsg: String?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("Failed to get token: $errorMsg"))
                            )
                        }
                    }
                }
            )
        }

    override suspend fun addDeviceByQrCode(qrContent: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            if (currentHomeId == 0L) {
                continuation.resume(Result.failure(Exception("Home not initialized. Please wait and try again.")))
                return@suspendCancellableCoroutine
            }

            val builder = ThingQRCodeActivatorBuilder()
                .setContext(context)
                .setHomeId(currentHomeId)
                .setUuid(qrContent)
                .setTimeOut(100)
                .setListener(object : IThingSmartActivatorListener {
                    override fun onError(errorCode: String?, errorMsg: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("QR pairing failed: $errorMsg")))
                        }
                    }

                    override fun onActiveSuccess(devResp: DeviceBean?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.success(Unit))
                        }
                        refreshDevices()
                    }

                    override fun onStep(step: String?, data: Any?) {
                        Log.d(TAG, "QR pairing step: $step")
                    }
                })

            val activator = ThingHomeSdk.getActivatorInstance().newQRCodeDevActivator(builder)
            activator?.start()

            continuation.invokeOnCancellation {
                activator?.stop()
            }
        }

    override suspend fun updateDeviceStatus(id: String, isOn: Boolean): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val mDevice = ThingHomeSdk.newDeviceInstance(id)
            if (mDevice == null) {
                continuation.resume(Result.failure(Exception("Device not found")))
                return@suspendCancellableCoroutine
            }
            // DP "1" is the standard switch command for Tuya devices
            val dps = "{\"1\": $isOn}"
            mDevice.publishDps(dps, object : IResultCallback {
                override fun onError(code: String?, error: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Failed to update status: $error")))
                    }
                }

                override fun onSuccess() {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }
            })
        }

    override suspend fun publishDp(deviceId: String, dpId: String, value: Any): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val mDevice = ThingHomeSdk.newDeviceInstance(deviceId)
            if (mDevice == null) {
                continuation.resume(Result.failure(Exception("Device not found")))
                return@suspendCancellableCoroutine
            }
            // JSONObject encodes the value with the right JSON type (bool/number/string).
            val command = JSONObject().put(dpId, value).toString()
            mDevice.publishDps(command, object : IResultCallback {
                override fun onError(code: String?, error: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Failed to send command: $error")))
                    }
                }

                override fun onSuccess() {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }
            })
        }

    override suspend fun renameDevice(id: String, newName: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val mDevice = ThingHomeSdk.newDeviceInstance(id)
            if (mDevice == null) {
                continuation.resume(Result.failure(Exception("Device not found")))
                return@suspendCancellableCoroutine
            }
            mDevice.renameDevice(newName, object : IResultCallback {
                override fun onError(code: String?, error: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Failed to rename: $error")))
                    }
                }

                override fun onSuccess() {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                    refreshDevices()
                }
            })
        }

    override suspend fun removeDevice(id: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val mDevice = ThingHomeSdk.newDeviceInstance(id)
            if (mDevice == null) {
                continuation.resume(Result.failure(Exception("Device not found")))
                return@suspendCancellableCoroutine
            }
            mDevice.removeDevice(object : IResultCallback {
                override fun onError(code: String?, error: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Failed to remove: $error")))
                    }
                }

                override fun onSuccess() {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }
            })
        }
}
