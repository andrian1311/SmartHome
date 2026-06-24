package com.shaqsid.smart.data.repository

import android.content.Context
import android.util.Log
import com.shaqsid.smart.domain.model.SmartDevice
import com.shaqsid.smart.domain.repository.DeviceRepository
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.bean.User
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
import com.thingclips.smart.home.sdk.api.IThingHomeChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

    private val devicesFlow = MutableStateFlow<List<SmartDevice>>(emptyList())
    private var currentHomeId: Long = 0L
    private val TAG = "DeviceRepositoryImpl"

    init {
        initializeTuyaSession()
    }

    private fun initializeTuyaSession() {
        if (ThingHomeSdk.getUserInstance().isLogin) {
            fetchHomeList()
        } else {
            ThingHomeSdk.getUserInstance().touristRegisterAndLogin("1", "SmartUser", object : ILoginCallback {
                override fun onSuccess(user: User?) {
                    fetchHomeList()
                }
                override fun onError(code: String?, error: String?) {
                    Log.e(TAG, "Tuya Login Error: $code $error")
                }
            })
        }
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
        ThingHomeSdk.getHomeManagerInstance().createHome("My Smart Home", 0.0, 0.0, "", emptyList(), object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                homeBean?.homeId?.let { setupHome(it) }
            }
            override fun onError(errorCode: String?, errorMsg: String?) {
                Log.e(TAG, "Create Home Error: $errorCode $errorMsg")
            }
        })
    }

    private fun setupHome(homeId: Long) {
        currentHomeId = homeId
        val homeInstance = ThingHomeSdk.newHomeInstance(homeId)
        
        homeInstance.registerHomeStatusListener(object : IThingHomeChangeListener {
            override fun onDeviceAdded(devId: String?) { refreshDevices() }
            override fun onDeviceRemoved(devId: String?) { refreshDevices() }
            override fun onDeviceUpdated(devId: String?) { refreshDevices() }
            override fun onHomeAdded(homeId: Long) {}
            override fun onHomeInvite(homeId: Long, homeName: String?) {}
            override fun onHomeRemoved(homeId: Long) {}
            override fun onHomeInfoChanged(homeId: Long) {}
            override fun onSharedDeviceList(sharedDeviceList: MutableList<DeviceBean>?) {}
            override fun onSharedMemberAdded(memberId: Long) {}
            override fun onSharedMemberRemoved(memberId: Long) {}
            override fun onSharedMemberUpdated(memberId: Long) {}
            override fun onServerConnectSuccess() {}
        })

        homeInstance.getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                updateDevicesFlow(homeBean?.deviceList)
            }
            override fun onError(errorCode: String?, errorMsg: String?) {}
        })
    }

    private fun refreshDevices() {
        ThingHomeSdk.newHomeInstance(currentHomeId)?.getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                updateDevicesFlow(homeBean?.deviceList)
            }
            override fun onError(errorCode: String?, errorMsg: String?) {}
        })
    }

    private fun updateDevicesFlow(tuyaDevices: List<DeviceBean>?) {
        val smartDevices = tuyaDevices?.map {
            // "1" usually corresponds to the switch state in standard Tuya DP (Data Point) schema
            val isOn = it.dps["1"] as? Boolean ?: false
            SmartDevice(it.devId, it.name, it.isOnline, isOn)
        } ?: emptyList()
        devicesFlow.value = smartDevices
    }

    override fun getDevices(): Flow<List<SmartDevice>> = devicesFlow

    override fun getDevice(id: String): Flow<SmartDevice?> = devicesFlow.map { devices -> devices.find { it.id == id } }

    override suspend fun addDevice(ssid: String, password: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        if (currentHomeId == 0L) {
            continuation.resume(Result.failure(Exception("Home not initialized")))
            return@suspendCancellableCoroutine
        }

        ThingHomeSdk.getActivatorInstance().getActivatorToken(currentHomeId, object : IThingActivatorGetToken {
            override fun onSuccess(token: String?) {
                val builder = ActivatorBuilder()
                    .setContext(context)
                    .setSsid(ssid)
                    .setPassword(password)
                    .setActivatorModel(ActivatorModelEnum.TY_EZ)
                    .setTimeOut(100)
                    .setToken(token)
                    .setListener(object : IThingSmartActivatorListener {
                        override fun onError(errorCode: String?, errorMsg: String?) {
                            if (continuation.isActive) continuation.resume(Result.failure(Exception("Pairing failed: $errorMsg")))
                        }
                        override fun onActiveSuccess(devResp: DeviceBean?) {
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                            refreshDevices()
                        }
                        override fun onStep(step: String?, data: Any?) {}
                    })
                val mTuyaActivator = ThingHomeSdk.getActivatorInstance().newActivator(builder)
                mTuyaActivator.start()
            }
            override fun onFailure(errorCode: String?, errorMsg: String?) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("Token failed: $errorMsg")))
            }
        })
    }

    override suspend fun updateDeviceStatus(id: String, isOn: Boolean): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val mDevice = ThingHomeSdk.newDeviceInstance(id)
        if (mDevice == null) {
            continuation.resume(Result.failure(Exception("Device not found")))
            return@suspendCancellableCoroutine
        }
        val dps = "{\"1\": $isOn}"
        mDevice.publishDps(dps, object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("Failed to update status: $error")))
            }
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            }
        })
    }

    override suspend fun renameDevice(id: String, newName: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val mDevice = ThingHomeSdk.newDeviceInstance(id)
        if (mDevice == null) {
            continuation.resume(Result.failure(Exception("Device not found")))
            return@suspendCancellableCoroutine
        }
        mDevice.renameDevice(newName, object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("Failed to rename: $error")))
            }
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
                refreshDevices()
            }
        })
    }

    override suspend fun removeDevice(id: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val mDevice = ThingHomeSdk.newDeviceInstance(id)
        if (mDevice == null) {
            continuation.resume(Result.failure(Exception("Device not found")))
            return@suspendCancellableCoroutine
        }
        mDevice.removeDevice(object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("Failed to remove: $error")))
            }
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            }
        })
    }
}
