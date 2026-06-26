package com.shaqsid.smart.data.repository

import android.content.Context
import android.util.Log
import com.shaqsid.smart.domain.model.DeviceControl
import com.shaqsid.smart.domain.model.DeviceSchedule
import com.shaqsid.smart.domain.model.PairingMode
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.thingclips.sdk.core.PluginManager
import com.thingclips.smart.interior.api.IAppDpParserPlugin
import com.thingclips.smart.android.device.builder.ThingTimerBuilder
import com.thingclips.smart.android.device.enums.TimerDeviceTypeEnum
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.constant.TimerUpdateEnum
import com.thingclips.smart.sdk.api.IThingDataCallback
import com.thingclips.smart.sdk.bean.TimerTask
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingDevice
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

    // Per-device listeners that deliver real-time DP/online changes so the UI stays in sync.
    private val deviceListeners = mutableMapOf<String, IThingDevice>()

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
        deviceListeners.values.forEach {
            runCatching { it.unRegisterDevListener() }
            runCatching { it.onDestroy() }
        }
        deviceListeners.clear()
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
        val smartDevices = tuyaDevices?.map { it.toSmartDevice() } ?: emptyList()
        devicesFlow.value = smartDevices
        registerDeviceListeners(smartDevices.map { it.id })
    }

    private fun DeviceBean.toSmartDevice(): SmartDevice {
        // DP "1" is the standard switch data point for most Tuya devices
        val isOn = dps?.get("1") as? Boolean ?: false
        return SmartDevice(
            id = devId,
            name = name ?: "Unknown Device",
            isOnline = isOnline,
            isOn = isOn,
            controls = parseControls(this)
        )
    }

    /**
     * Registers a real-time listener per device. DP and online changes (from the app, the physical
     * device, or anywhere else) are merged into [devicesFlow] so the UI reflects the true state.
     */
    private fun registerDeviceListeners(devIds: Collection<String>) {
        devIds.forEach { devId ->
            if (deviceListeners.containsKey(devId)) return@forEach
            val device = ThingHomeSdk.newDeviceInstance(devId) ?: return@forEach
            device.registerDevListener(object : IDevListener {
                override fun onDpUpdate(id: String?, dpStr: String?) = applyDpUpdate(id, dpStr)
                override fun onStatusChanged(id: String?, online: Boolean) = applyOnlineChange(id, online)
                override fun onNetworkStatusChanged(id: String?, status: Boolean) {}
                override fun onRemoved(id: String?) {}
                override fun onDevInfoUpdate(id: String?) {}
            })
            deviceListeners[devId] = device
        }
    }

    /** Merges a DP-change JSON ({"1":true,...}) into the matching device's controls in the flow. */
    private fun applyDpUpdate(devId: String?, dpStr: String?) {
        devId ?: return
        val json = dpStr?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        devicesFlow.value = devicesFlow.value.map { device ->
            if (device.id != devId) return@map device
            val controls = device.controls.map { c ->
                when {
                    // A switch may also carry a paired countdown DP that updates independently.
                    c is DeviceControl.Switch -> {
                        val on = if (json.has(c.dpId)) json.optBoolean(c.dpId, c.on) else c.on
                        val seconds = if (c.countdownDpId != null && json.has(c.countdownDpId)) {
                            json.optInt(c.countdownDpId, c.countdownSeconds)
                        } else c.countdownSeconds
                        c.copy(on = on, countdownSeconds = seconds)
                    }
                    json.has(c.dpId) -> c.withValue(json.opt(c.dpId))
                    else -> c
                }
            }
            val isOn = if (json.has("1")) json.optBoolean("1", device.isOn) else device.isOn
            device.copy(isOn = isOn, controls = controls)
        }
    }

    private fun applyOnlineChange(devId: String?, online: Boolean) {
        devId ?: return
        devicesFlow.value = devicesFlow.value.map {
            if (it.id == devId) it.copy(isOnline = online) else it
        }
    }

    /** Returns a copy of this control with its value replaced by the new raw DP value. */
    private fun DeviceControl.withValue(value: Any?): DeviceControl = when (this) {
        is DeviceControl.Switch ->
            copy(on = (value as? Boolean) ?: value?.toString()?.toBooleanStrictOrNull() ?: on)
        is DeviceControl.Numeric ->
            copy(current = (value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull() ?: current)
        is DeviceControl.Enumeration -> copy(current = value?.toString() ?: current)
        is DeviceControl.Text -> copy(current = value?.toString() ?: current)
    }

    /**
     * Maps a device's Tuya schema + current DP values into typed [DeviceControl]s the UI can render.
     * Unsupported/complex types (raw, bitmap, struct) are skipped.
     */
    /** Per-DP metadata from Tuya's DP parser: human-readable name + standard code (e.g. `countdown_1`). */
    private data class DpMeta(val name: String?, val code: String?)

    private fun buildDpMeta(deviceBean: DeviceBean): Map<String, DpMeta> = runCatching {
        val plugin = PluginManager.service(IAppDpParserPlugin::class.java) ?: return emptyMap()
        val parser = plugin.update(deviceBean)
        parser.getAllDp().associate { dp ->
            val schema = runCatching { dp.getSchema() }.getOrNull()
            dp.getDpId() to DpMeta(
                name = dp.getDisplayTitle()?.takeIf { it.isNotBlank() },
                code = schema?.code?.takeIf { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyMap())

    private fun parseControls(deviceBean: DeviceBean): List<DeviceControl> {
        val schemaMap = deviceBean.schemaMap
        val dps = deviceBean.dps ?: emptyMap()
        val meta = buildDpMeta(deviceBean)
        val labels = meta.mapNotNull { (id, m) -> m.name?.let { id to it } }.toMap()

        // Some devices report no schema (e.g. freshly paired). Fall back to inferring basic
        // controls from the raw DP values so the detail screen isn't empty.
        if (schemaMap.isNullOrEmpty()) {
            return pairCountdowns(parseControlsFromDps(dps, labels), dps, meta)
        }

        val schemaControls = schemaMap.values
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { schema ->
                val dpId = schema.id ?: return@mapNotNull null
                val name = labels[dpId] ?: schema.name?.takeIf { it.isNotBlank() } ?: "DP $dpId"
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
        return pairCountdowns(schemaControls, dps, meta)
    }

    /**
     * Folds standard `countdown_N` DPs into their matching `switch_N` control as a per-switch
     * countdown (seconds), and removes them from the standalone control list. Pairs by the numeric
     * suffix of the DP code (falls back to the suffix in the display name, then to a 1:1 match when
     * there's a single switch and a single countdown).
     */
    private fun pairCountdowns(
        controls: List<DeviceControl>,
        dps: Map<String, Any?>,
        meta: Map<String, DpMeta>
    ): List<DeviceControl> {
        fun suffixNumber(s: String?): Int? = s?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }
        fun isCountdown(dpId: String): Boolean {
            val m = meta[dpId] ?: return false
            val code = m.code?.lowercase().orEmpty()
            val name = m.name?.lowercase().orEmpty()
            return code.startsWith("countdown") || name.contains("countdown") ||
                (m.name?.contains("倒计时") == true)
        }

        val countdownDps = controls.map { it.dpId }.filter { isCountdown(it) }
        if (countdownDps.isEmpty()) return controls

        val switches = controls.filterIsInstance<DeviceControl.Switch>()
        val byNumber = countdownDps.associateBy { suffixNumber(meta[it]?.code) ?: suffixNumber(meta[it]?.name) }
        val singleMatch = switches.size == 1 && countdownDps.size == 1

        return controls.mapNotNull { c ->
            when {
                c.dpId in countdownDps -> null // remove standalone countdown rows
                c is DeviceControl.Switch -> {
                    val cdDp = if (singleMatch) countdownDps.first()
                    else (suffixNumber(meta[c.dpId]?.code) ?: suffixNumber(c.name))?.let { byNumber[it] }
                    if (cdDp != null) {
                        c.copy(countdownDpId = cdDp, countdownSeconds = (dps[cdDp] as? Number)?.toInt() ?: 0)
                    } else c
                }
                else -> c
            }
        }
    }

    /**
     * Schema-less fallback: infer controls from the runtime type of each DP value. Booleans become
     * toggles (most bool DPs are writable); numbers and strings are shown as read-only values since
     * their range/writability is unknown without a schema.
     */
    private fun parseControlsFromDps(
        dps: Map<String, Any?>,
        labels: Map<String, String>
    ): List<DeviceControl> {
        return dps.entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .map { (dpId, value) ->
                val name = labels[dpId] ?: "DP $dpId"
                when (value) {
                    is Boolean -> DeviceControl.Switch(dpId, name, editable = true, on = value)
                    else -> DeviceControl.Text(dpId, name, editable = false, current = value?.toString().orEmpty())
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

    // --- Scheduled tasks (timers) ---

    private val timer get() = ThingHomeSdk.getTimerInstance()

    override suspend fun getSchedules(deviceId: String): Result<List<DeviceSchedule>> =
        suspendCancellableCoroutine { continuation ->
            timer.getAllTimerList(deviceId, TimerDeviceTypeEnum.DEVICE,
                object : IThingDataCallback<List<TimerTask>> {
                    override fun onSuccess(tasks: List<TimerTask>?) {
                        val schedules = tasks.orEmpty().flatMap { task ->
                            task.timerList.orEmpty().map { t ->
                                // The new timer API returns the action in getValue() as a dps JSON
                                // (e.g. {"1":true}); getDpId() is empty. Fall back to the legacy
                                // split fields if it isn't JSON.
                                val (dpId, turnOn) = parseTimerAction(t.dpId, t.value)
                                DeviceSchedule(
                                    id = t.timerId,
                                    time = t.time ?: "",
                                    loops = t.loops ?: DeviceSchedule.LOOPS_ONCE,
                                    enabled = t.isOpen,
                                    dpId = dpId,
                                    turnOn = turnOn
                                )
                            }
                        }.sortedBy { it.time }
                        if (continuation.isActive) continuation.resume(Result.success(schedules))
                    }

                    override fun onError(code: String?, error: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception(error ?: "Failed to load schedules")))
                        }
                    }
                })
        }

    /**
     * Resolves a timer's target switch DP id and on/off value. The new timer API returns the action
     * in [value] as a dps JSON (e.g. `{"1":true}`); older timers split it into [dpId]/[value].
     */
    private fun parseTimerAction(dpId: String?, value: String?): Pair<String, Boolean> {
        val json = value?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json != null && json.length() > 0) {
            val key = json.keys().next()
            return key to json.optBoolean(key, json.optString(key).equals("true", ignoreCase = true))
        }
        return (dpId.orEmpty()) to (value?.equals("true", ignoreCase = true) ?: false)
    }

    override suspend fun addSchedule(
        deviceId: String,
        time: String,
        loops: String,
        dpId: String,
        turnOn: Boolean
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        // actions JSON: {"dps":{"<dpId>":<bool>}, "time":"HH:mm"}
        val dps = JSONObject().put(dpId, turnOn)
        val actions = JSONObject().put("dps", dps).put("time", time).toString()
        val builder = ThingTimerBuilder.Builder()
            .taskName(SCHEDULE_TASK)
            .devId(deviceId)
            .deviceType(TimerDeviceTypeEnum.DEVICE)
            .actions(actions)
            .loops(loops)
            .status(1)
            .appPush(false)
            .build()
        timer.addTimer(builder, object : IResultCallback {
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            }

            override fun onError(code: String?, error: String?) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(Exception(error ?: "Failed to add schedule")))
                }
            }
        })
    }

    override suspend fun setScheduleEnabled(
        deviceId: String,
        scheduleId: String,
        enabled: Boolean
    ): Result<Unit> = updateTimer(
        deviceId, scheduleId, if (enabled) TimerUpdateEnum.OPEN else TimerUpdateEnum.CLOSE
    )

    override suspend fun deleteSchedule(deviceId: String, scheduleId: String): Result<Unit> =
        updateTimer(deviceId, scheduleId, TimerUpdateEnum.DELETE)

    private suspend fun updateTimer(
        deviceId: String,
        scheduleId: String,
        op: TimerUpdateEnum
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        timer.updateTimerStatus(
            deviceId, TimerDeviceTypeEnum.DEVICE, listOf(scheduleId), op,
            object : IResultCallback {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onError(code: String?, error: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception(error ?: "Failed to update schedule")))
                    }
                }
            })
    }

    private companion object {
        const val SCHEDULE_TASK = "app_schedule"
    }
}
